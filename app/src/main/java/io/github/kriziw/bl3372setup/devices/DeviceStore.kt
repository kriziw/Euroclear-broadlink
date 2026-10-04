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
 */
data class SavedDevice(
    /** `AA:BB:CC:DD:EE:FF`, the stable identity. */
    val mac: String,
    val name: String,
    /** Last known IPv4 address; may be on another VLAN than the phone. */
    val lastIp: String,
    val deviceType: Int,
    /** The user confirmed the values match the controller and unlocked controls for an unverified model. */
    val controlsUnlocked: Boolean = false,
)

/** Saved devices in app-private SharedPreferences (excluded from backup by data_extraction_rules). */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<SavedDevice>> = _devices.asStateFlow()

    fun get(mac: String): SavedDevice? = _devices.value.firstOrNull { it.mac == mac }

    /** Adds the device, or updates the entry with the same MAC while keeping its user-chosen name. */
    fun save(device: SavedDevice) = update { list ->
        val existing = list.firstOrNull { it.mac == device.mac }
        if (existing == null) list + device else list.map { if (it.mac == device.mac) device.copy(name = existing.name) else it }
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
                    .put("unlocked", it.controlsUnlocked),
            )
        }
    }.toString()

    private companion object {
        const val KEY = "saved_devices"
    }
}
