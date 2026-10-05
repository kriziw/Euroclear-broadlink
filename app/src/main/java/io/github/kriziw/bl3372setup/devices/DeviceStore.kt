package io.github.kriziw.bl3372setup.devices

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A remembered softener. Only addressing data is stored: never Wi-Fi credentials or session keys.
 * Appliances of other brands also keep their local API login, encrypted with [CredentialCipher].
 */
data class SavedDevice(
    /** `AA:BB:CC:DD:EE:FF` for BroadLink modules; `<brand>:<uuid>` for other brands. The stable identity. */
    val mac: String,
    val name: String,
    /** Last known IPv4 address; may be on another VLAN than the phone. */
    val lastIp: String,
    val deviceType: Int,
    /** The user confirmed the values match the controller and unlocked controls for an unverified model. */
    val controlsUnlocked: Boolean = false,
    /** Field-1 identity checked at opt-in. Old unscoped unlocks require confirmation again. */
    val unlockedModelCode: Int? = null,
    /** [io.github.kriziw.bl3372setup.appliance.Brand] id, or null for a Runxin controller behind a BroadLink module. */
    val brand: String? = null,
    val port: Int? = null,
    val user: String? = null,
    /** Password or login code of the appliance's local API. Never logged. */
    val secret: String? = null,
    /** Model the appliance last reported, for the device list. */
    val model: String? = null,
) {
    override fun toString() =
        "SavedDevice(mac=$mac, name=$name, lastIp=$lastIp, deviceType=$deviceType, brand=$brand, model=$model, " +
            "secret=${if (secret == null) null else "***"})"
}

/** Saved devices in app-private SharedPreferences (excluded from backup by data_extraction_rules). */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<SavedDevice>> = _devices.asStateFlow()

    fun get(mac: String): SavedDevice? = _devices.value.firstOrNull { it.mac == mac }

    /** Adds the device, or updates the entry with the same MAC while keeping its user-chosen name. */
    fun save(device: SavedDevice) = update { list ->
        val existing = list.firstOrNull { it.mac == device.mac }
        if (existing == null) list + device else list.map {
            if (it.mac == device.mac) device.copy(
                name = existing.name,
                controlsUnlocked = existing.controlsUnlocked && existing.deviceType == device.deviceType,
                unlockedModelCode = existing.unlockedModelCode.takeIf { existing.deviceType == device.deviceType },
            ) else it
        }
    }

    fun update(mac: String, transform: (SavedDevice) -> SavedDevice) = update { list ->
        list.map { if (it.mac == mac) transform(it) else it }
    }

    fun remove(mac: String) = update { list -> list.filterNot { it.mac == mac } }

    @Synchronized
    private fun update(transform: (List<SavedDevice>) -> List<SavedDevice>) {
        val next = transform(_devices.value)
        _devices.value = next
        prefs.edit { putString(KEY, encode(next)) }
    }

    private fun load(): List<SavedDevice> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            array.getJSONObject(i).run {
                SavedDevice(
                    mac = getString("mac"),
                    name = getString("name"),
                    lastIp = getString("ip"),
                    deviceType = getInt("type"),
                    controlsUnlocked = optBoolean("unlocked", false),
                    unlockedModelCode = if (has("unlockedModel") && !isNull("unlockedModel")) getInt("unlockedModel") else null,
                    brand = optText("brand"),
                    port = if (has("port") && !isNull("port")) getInt("port") else null,
                    user = optText("user"),
                    secret = optText("secret")?.let(CredentialCipher::decrypt),
                    model = optText("model"),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun encode(devices: List<SavedDevice>): String = JSONArray().apply {
        devices.forEach {
            put(
                JSONObject()
                    .put("mac", it.mac)
                    .put("name", it.name)
                    .put("ip", it.lastIp)
                    .put("type", it.deviceType)
                    .put("unlocked", it.controlsUnlocked)
                    .put("unlockedModel", it.unlockedModelCode)
                    .put("brand", it.brand)
                    .put("port", it.port)
                    .put("user", it.user)
                    .put("secret", it.secret?.let(CredentialCipher::encrypt))
                    .put("model", it.model),
            )
        }
    }.toString()

    private fun JSONObject.optText(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private companion object {
        const val KEY = "saved_devices"
    }
}
