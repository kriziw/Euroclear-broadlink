package io.github.kriziw.bl3372setup.broadlink

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** An error code reported by the device at offset 0x22 of a reply, or a malformed reply. */
class BroadlinkException(val code: Int, message: String) : IOException("BroadLink error $code: $message") {
    companion object {
        /** Device error codes, as named by python-broadlink `exceptions.py`. */
        fun fromCode(code: Int) = BroadlinkException(
            code,
            when (code) {
                -1 -> "authentication failed"
                -2 -> "logged out"
                -3 -> "device offline"
                -4 -> "command not supported"
                -5 -> "device busy (storage full)"
                -6 -> "structure abnormal"
                -7 -> "control key expired"
                else -> "unknown error"
            },
        )

        const val AUTH_FAILED = -1
        const val KEY_EXPIRED = -7
        /** Empirically transient on the BL3372 (Danirv/ypsilon-local); cause unknown. */
        const val TRANSIENT = -5
        const val MALFORMED = -4007
    }
}

/** A decrypted, error-checked reply to [BroadlinkCommand.buildPacket]. */
class BroadlinkReply(val payload: ByteArray)

/**
 * Encrypted BroadLink command packets (authentication 0x65, command 0x6A), mirroring
 * python-broadlink `Device.send_packet()` / `Device.auth()`. Pure functions only.
 */
object BroadlinkCommand {
    const val TYPE_AUTH = 0x65
    const val TYPE_COMMAND = 0x6A

    val INITIAL_KEY: ByteArray = hexBytes("097628343fe99e23765c1513accf8b02")
    private val IV: ByteArray = hexBytes("562e17996d093d28ddb3ba695a2e6f58")
    private val MAGIC: ByteArray = hexBytes("5aa5aa555aa5aa55")

    private const val HEADER_SIZE = 0x38
    private const val OFFSET_ERROR = 0x22
    private const val OFFSET_DEVTYPE = 0x24
    private const val OFFSET_TYPE = 0x26
    private const val OFFSET_COUNT = 0x28
    private const val OFFSET_MAC = 0x2A
    private const val OFFSET_ID = 0x30
    private const val OFFSET_PAYLOAD_CHECKSUM = 0x34

    /** python-broadlink increments the counter before every packet and keeps the top bit set. */
    fun nextCount(count: Int): Int = ((count + 1) or 0x8000) and 0xFFFF

    /** The 0x50-byte authentication request payload sent by python-broadlink `auth()`. */
    fun authPayload(): ByteArray = ByteArray(0x50).also { p ->
        for (i in 0x04 until 0x14) p[i] = 0x31
        p[0x1E] = 0x01
        p[0x2D] = 0x01
        "Test 1".toByteArray(Charsets.US_ASCII).copyInto(p, 0x30)
    }

    /**
     * @param mac the device MAC in display order (as in a hello reply); it is written reversed.
     */
    fun buildPacket(
        packetType: Int,
        count: Int,
        deviceType: Int,
        mac: ByteArray,
        sessionId: Int,
        payload: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        MAGIC.copyInto(header, 0)
        writeLe(header, OFFSET_DEVTYPE, deviceType, 2)
        writeLe(header, OFFSET_TYPE, packetType, 2)
        writeLe(header, OFFSET_COUNT, count, 2)
        mac.reversedArray().copyInto(header, OFFSET_MAC)
        writeLe(header, OFFSET_ID, sessionId, 4)
        writeLe(header, OFFSET_PAYLOAD_CHECKSUM, sum(payload), 2)

        val padded = payload.copyOf(payload.size + (16 - payload.size % 16) % 16)
        val packet = header + aes(Cipher.ENCRYPT_MODE, key, padded)
        writeLe(packet, 0x20, BroadlinkPackets.checksum(packet), 2)
        return packet
    }

    /** True if [data] looks like a BroadLink reply: long enough and correctly checksummed. */
    fun isWellFormedReply(data: ByteArray, length: Int = data.size): Boolean {
        if (length < 0x30) return false
        val stored = (data[0x20].toInt() and 0xFF) or ((data[0x21].toInt() and 0xFF) shl 8)
        return stored == BroadlinkPackets.checksum(data, length)
    }

    /**
     * Checks the reply's error field and decrypts its body.
     * @throws BroadlinkException for a device error or a malformed reply.
     */
    fun parseReply(data: ByteArray, length: Int, key: ByteArray): BroadlinkReply {
        if (!isWellFormedReply(data, length)) throw BroadlinkException(BroadlinkException.MALFORMED, "malformed reply")
        val error = ((data[OFFSET_ERROR].toInt() and 0xFF) or (data[OFFSET_ERROR + 1].toInt() shl 8)).toShort().toInt()
        if (error != 0) throw BroadlinkException.fromCode(error)
        val body = data.copyOfRange(HEADER_SIZE, length)
        if (body.isEmpty() || body.size % 16 != 0) {
            throw BroadlinkException(BroadlinkException.MALFORMED, "encrypted body length ${body.size}")
        }
        return BroadlinkReply(aes(Cipher.DECRYPT_MODE, key, body))
    }

    private fun aes(mode: Int, key: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance("AES/CBC/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(IV))
            doFinal(data)
        }

    private fun sum(data: ByteArray): Int = data.fold(0xBEAF) { acc, b -> acc + (b.toInt() and 0xFF) } and 0xFFFF

    private fun writeLe(target: ByteArray, offset: Int, value: Int, size: Int) {
        for (i in 0 until size) target[offset + i] = (value shr (8 * i)).toByte()
    }

    private fun hexBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}
