package io.github.kriziw.bl3372setup.broadlink

import java.net.Inet4Address
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Wi-Fi security mode written at offset 0x86 of the setup packet.
 *
 * Codes are from python-broadlink `setup()` (0 none, 1 WEP, 2 WPA1, 3 WPA2, 4 WPA1/2) and
 * BroadLink's App SDK `deviceAPConfig` docs. WEP (1) is intentionally not offered.
 */
enum class SecurityMode(val code: Int) {
    OPEN(0),
    WPA(2),
    WPA2(3),
    WPA_WPA2(4);

    val usesPassword: Boolean get() = this != OPEN
}

/** Why a set of credentials can't be encoded into a setup packet. */
sealed interface CredentialProblem {
    data object SsidEmpty : CredentialProblem
    data class SsidTooLong(val bytes: Int) : CredentialProblem
    data class PasswordTooShort(val bytes: Int) : CredentialProblem
    data class PasswordTooLong(val bytes: Int) : CredentialProblem
}

class InvalidCredentialsException(val problem: CredentialProblem) :
    IllegalArgumentException(problem.toString())

/** Wall-clock values written into a hello packet, matching python-broadlink's `Datetime.pack`. */
data class HelloTime(val local: LocalDateTime, val utcOffsetHours: Int) {
    companion object {
        /**
         * python-broadlink builds its timezone from `-time.timezone`, i.e. the *standard* offset
         * without DST, and expresses "now" in that offset. Mirror it exactly.
         */
        fun now(): HelloTime {
            val rawOffsetSeconds = TimeZone.getDefault().rawOffset / 1000
            val offset = ZoneOffset.ofTotalSeconds(rawOffsetSeconds)
            return HelloTime(
                local = LocalDateTime.ofInstant(Instant.now(), offset),
                utcOffsetHours = rawOffsetSeconds / 3600,
            )
        }
    }
}

/** A reply to a hello (discovery) packet. */
data class HelloResponse(
    val deviceType: Int,
    val mac: String,
    val name: String,
    val isLocked: Boolean,
)

/**
 * Builds and parses BroadLink local-protocol packets. Pure functions only: no sockets, no Android.
 *
 * See docs/PROTOCOL.md for the derivation of every offset used here.
 */
object BroadlinkPackets {
    const val PORT = 80

    const val SETUP_PACKET_SIZE = 0x88
    const val MAX_SSID_BYTES = 32
    const val MAX_PASSWORD_BYTES = 32
    const val MIN_WPA_PASSWORD_BYTES = 8

    const val HELLO_PACKET_SIZE = 0x30

    /** Device type reported by the BL3372 in Runxin F79D controllers (Danirv/ypsilon-local). */
    const val DEVTYPE_RUNXIN_BL3372 = 0x520F

    private const val CHECKSUM_SEED = 0xBEAF
    private const val OFFSET_CHECKSUM = 0x20
    private const val OFFSET_COMMAND = 0x26

    private const val COMMAND_SETUP = 0x14
    private const val COMMAND_SETUP_ACK = 0x15
    private const val COMMAND_HELLO = 0x06

    private const val OFFSET_SSID = 0x44
    private const val OFFSET_PASSWORD = 0x64
    private const val OFFSET_SSID_LENGTH = 0x84
    private const val OFFSET_PASSWORD_LENGTH = 0x85
    private const val OFFSET_SECURITY = 0x86

    private const val OFFSET_HELLO_UTC_OFFSET = 0x08
    private const val OFFSET_HELLO_YEAR = 0x0C
    private const val OFFSET_HELLO_MINUTE = 0x0E
    private const val OFFSET_HELLO_HOUR = 0x0F
    private const val OFFSET_HELLO_SHORT_YEAR = 0x10
    private const val OFFSET_HELLO_WEEKDAY = 0x11
    private const val OFFSET_HELLO_DAY = 0x12
    private const val OFFSET_HELLO_MONTH = 0x13
    private const val OFFSET_HELLO_LOCAL_IP = 0x18
    private const val OFFSET_HELLO_LOCAL_PORT = 0x1C

    private const val OFFSET_RESPONSE_DEVTYPE = 0x34
    private const val OFFSET_RESPONSE_MAC = 0x3A
    private const val OFFSET_RESPONSE_NAME = 0x40
    private const val OFFSET_RESPONSE_LOCKED = 0x7F

    /** Returns the first problem with these credentials, or null if they can be sent. */
    fun validate(ssid: String, password: String, security: SecurityMode): CredentialProblem? {
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8).size
        if (ssidBytes == 0) return CredentialProblem.SsidEmpty
        if (ssidBytes > MAX_SSID_BYTES) return CredentialProblem.SsidTooLong(ssidBytes)
        if (!security.usesPassword) return null
        val passwordBytes = password.toByteArray(Charsets.UTF_8).size
        if (passwordBytes > MAX_PASSWORD_BYTES) return CredentialProblem.PasswordTooLong(passwordBytes)
        if (passwordBytes < MIN_WPA_PASSWORD_BYTES) return CredentialProblem.PasswordTooShort(passwordBytes)
        return null
    }

    /**
     * Builds the 136-byte AP-mode "join Wi-Fi" packet, equivalent to python-broadlink's
     * `broadlink.setup(ssid, password, security_mode)`.
     *
     * Strings are encoded as UTF-8 (identical to python-broadlink for ASCII). For [SecurityMode.OPEN]
     * the password is ignored and sent empty.
     *
     * @throws InvalidCredentialsException if [validate] reports a problem.
     */
    fun buildSetupPacket(ssid: String, password: String, security: SecurityMode): ByteArray {
        validate(ssid, password, security)?.let { throw InvalidCredentialsException(it) }
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        val passwordBytes =
            if (security.usesPassword) password.toByteArray(Charsets.UTF_8) else ByteArray(0)

        val packet = ByteArray(SETUP_PACKET_SIZE)
        packet[OFFSET_COMMAND] = COMMAND_SETUP.toByte()
        ssidBytes.copyInto(packet, OFFSET_SSID)
        passwordBytes.copyInto(packet, OFFSET_PASSWORD)
        packet[OFFSET_SSID_LENGTH] = ssidBytes.size.toByte()
        packet[OFFSET_PASSWORD_LENGTH] = passwordBytes.size.toByte()
        packet[OFFSET_SECURITY] = security.code.toByte()
        passwordBytes.fill(0)

        writeChecksum(packet)
        return packet
    }

    /**
     * Builds the 48-byte discovery packet, equivalent to python-broadlink's `scan()`.
     *
     * @param localAddress this phone's IPv4 address on the network being scanned, or null.
     * @param localPort the UDP port the reply should come back to.
     */
    fun buildHelloPacket(time: HelloTime, localAddress: Inet4Address?, localPort: Int): ByteArray {
        val packet = ByteArray(HELLO_PACKET_SIZE)
        writeIntLe(packet, OFFSET_HELLO_UTC_OFFSET, time.utcOffsetHours)
        writeShortLe(packet, OFFSET_HELLO_YEAR, time.local.year)
        packet[OFFSET_HELLO_MINUTE] = time.local.minute.toByte()
        packet[OFFSET_HELLO_HOUR] = time.local.hour.toByte()
        packet[OFFSET_HELLO_SHORT_YEAR] = (time.local.year % 100).toByte()
        packet[OFFSET_HELLO_WEEKDAY] = time.local.dayOfWeek.value.toByte()
        packet[OFFSET_HELLO_DAY] = time.local.dayOfMonth.toByte()
        packet[OFFSET_HELLO_MONTH] = time.local.monthValue.toByte()
        localAddress?.address?.reversedArray()?.copyInto(packet, OFFSET_HELLO_LOCAL_IP)
        writeShortLe(packet, OFFSET_HELLO_LOCAL_PORT, localPort)
        packet[OFFSET_COMMAND] = COMMAND_HELLO.toByte()
        writeChecksum(packet)
        return packet
    }

    /** True if [data] is the 0x15 acknowledgement a device sends after a setup packet. */
    fun isSetupAck(data: ByteArray, length: Int = data.size): Boolean {
        if (length < HELLO_PACKET_SIZE) return false
        if (data[OFFSET_COMMAND].toInt() and 0xFF != COMMAND_SETUP_ACK) return false
        val stored = (data[OFFSET_CHECKSUM].toInt() and 0xFF) or
            ((data[OFFSET_CHECKSUM + 1].toInt() and 0xFF) shl 8)
        return stored == checksum(data, length)
    }

    /** Parses a reply to a hello packet the way python-broadlink's `scan()` does. */
    fun parseHelloResponse(data: ByteArray, length: Int = data.size): HelloResponse? {
        if (length < OFFSET_RESPONSE_NAME) return null
        // Another client's hello request is not a device reply.
        if (data[OFFSET_COMMAND].toInt() and 0xFF == COMMAND_HELLO) return null

        val deviceType = (data[OFFSET_RESPONSE_DEVTYPE].toInt() and 0xFF) or
            ((data[OFFSET_RESPONSE_DEVTYPE + 1].toInt() and 0xFF) shl 8)
        val mac = (OFFSET_RESPONSE_NAME - 1 downTo OFFSET_RESPONSE_MAC)
            .joinToString(":") { "%02X".format(data[it].toInt() and 0xFF) }
        var nameEnd = OFFSET_RESPONSE_NAME
        while (nameEnd < length && data[nameEnd] != 0.toByte()) nameEnd++
        val name = String(data, OFFSET_RESPONSE_NAME, nameEnd - OFFSET_RESPONSE_NAME, Charsets.UTF_8)
        val isLocked = length > OFFSET_RESPONSE_LOCKED && data[OFFSET_RESPONSE_LOCKED] != 0.toByte()
        return HelloResponse(deviceType, mac, name, isLocked)
    }

    /** `(0xBEAF + sum of all bytes except the checksum field) & 0xFFFF`. */
    fun checksum(data: ByteArray, length: Int = data.size): Int {
        var sum = CHECKSUM_SEED
        for (i in 0 until length) {
            if (i == OFFSET_CHECKSUM || i == OFFSET_CHECKSUM + 1) continue
            sum += data[i].toInt() and 0xFF
        }
        return sum and 0xFFFF
    }

    private fun writeChecksum(packet: ByteArray) {
        writeShortLe(packet, OFFSET_CHECKSUM, checksum(packet))
    }

    private fun writeShortLe(packet: ByteArray, offset: Int, value: Int) {
        packet[offset] = value.toByte()
        packet[offset + 1] = (value shr 8).toByte()
    }

    private fun writeIntLe(packet: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) packet[offset + i] = (value shr (8 * i)).toByte()
    }
}
