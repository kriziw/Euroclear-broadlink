package io.github.kriziw.bl3372setup.broadlink

import io.github.kriziw.bl3372setup.runxin.RunxinFrames
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Vectors from tools/golden_device_vectors.py, i.e. python-broadlink's own `auth()`/`send_packet()`. */
class BroadlinkCommandTest {
    private val mac = hex("1cd1d7123456")
    private val sessionKey = ByteArray(16) { (0x10 + it).toByte() }

    @Test
    fun `auth request matches python-broadlink`() {
        val packet = BroadlinkCommand.buildPacket(
            BroadlinkCommand.TYPE_AUTH, 0x8001, 0x520F, mac, 0, BroadlinkCommand.authPayload(), BroadlinkCommand.INITIAL_KEY,
        )
        assertArrayEquals(hex(AUTH_REQUEST), packet)
    }

    @Test
    fun `command packet carrying a TFB-wrapped Runxin query matches python-broadlink`() {
        val query = RunxinFrames.query(listOf(1, 34))
        val tfb = byteArrayOf(query.size.toByte(), 0) + query
        val packet = BroadlinkCommand.buildPacket(
            BroadlinkCommand.TYPE_COMMAND, 0x8002, 0x520F, mac, 0x11223344, tfb, sessionKey,
        )
        assertArrayEquals(hex(COMMAND_REQUEST), packet)
    }

    @Test
    fun `auth reply accepted by python-broadlink yields session id and key`() {
        val reply = hex(AUTH_REPLY)
        val payload = BroadlinkCommand.parseReply(reply, reply.size, BroadlinkCommand.INITIAL_KEY).payload
        assertArrayEquals(hex("44332211"), payload.copyOfRange(0, 4))
        assertArrayEquals(sessionKey, payload.copyOfRange(4, 0x14))
    }

    @Test
    fun `reply error codes and corruption are reported`() {
        val reply = hex(AUTH_REPLY)
        val withError = reply.copyOf().also {
            it[0x22] = 0xFB.toByte() // -5 little-endian
            it[0x23] = 0xFF.toByte()
            val sum = BroadlinkPackets.checksum(it)
            it[0x20] = sum.toByte()
            it[0x21] = (sum shr 8).toByte()
        }
        val error = assertThrows(BroadlinkException::class.java) {
            BroadlinkCommand.parseReply(withError, withError.size, BroadlinkCommand.INITIAL_KEY)
        }
        assertEquals(-5, error.code)

        val corrupted = reply.copyOf().also { it[0x40] = (it[0x40] + 1).toByte() }
        assertEquals(
            BroadlinkException.MALFORMED,
            assertThrows(BroadlinkException::class.java) {
                BroadlinkCommand.parseReply(corrupted, corrupted.size, BroadlinkCommand.INITIAL_KEY)
            }.code,
        )
    }

    @Test
    fun `counter keeps its top bit and wraps`() {
        assertEquals(0x8001, BroadlinkCommand.nextCount(0x8000))
        assertEquals(0x8000, BroadlinkCommand.nextCount(0xFFFF))
    }

    companion object {
        const val AUTH_REQUEST =
            "5aa5aa555aa5aa5500000000000000000000000000000000000000000000000086ef00000f5265000180563412d7d11c" +
                "00000000b2c30000453452e7f92eda958344930835ef9a6d93b0b6da60530408ebba79410b080296f9f7cd7779b46f" +
                "2513e2c5bbd4450e907fa1ba8fc5e0169776e2620824fff3f85f6f64f7120b1f724ff1b048b76e3e30"
        const val AUTH_REPLY =
            "5aa5aa555aa5aa5500000000000000000000000000000000000000000000000095d500000f52e9030000563412d7d11c" +
                "000000000000000083cbb33e991c5c8822ce8e09caafcf84fb3f1bfbcaad0a881ea43f503022c254"
        const val COMMAND_REQUEST =
            "5aa5aa555aa5aa55000000000000000000000000000000000000000000000000fbd400000f526a000280563412d7d11c" +
                "4433221143c4000030eb2fb815c0022a283907037b585c86ad76734a5eacace0e94b8402bb1a21a9"
    }
}
