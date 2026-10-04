package io.github.kriziw.bl3372setup.broadlink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.LocalDateTime

/**
 * The hex vectors below were produced by running python-broadlink 0.19.0's own `setup()` and
 * `scan()` source against a stub socket (see tools/golden_vectors.py and docs/PROTOCOL.md).
 */
class BroadlinkPacketsTest {

    @Test
    fun `setup packet matches python-broadlink for WPA2`() {
        val packet = BroadlinkPackets.buildSetupPacket("MyHomeWiFi", "correct-horse-42", SecurityMode.WPA2)
        assertArrayEquals(
            hex(
                "0000000000000000000000000000000000000000000000000000000000000000" +
                    "71c8000000001400000000000000000000000000000000000000000000000000" +
                    "000000004d79486f6d6557694669000000000000000000000000000000000000" +
                    "00000000636f72726563742d686f7273652d3432000000000000000000000000" +
                    "000000000a100300",
            ),
            packet,
        )
    }

    @Test
    fun `setup packet matches python-broadlink for an open network`() {
        val packet = BroadlinkPackets.buildSetupPacket("Net", "", SecurityMode.OPEN)
        assertArrayEquals(
            hex(
                "0000000000000000000000000000000000000000000000000000000000000000" +
                    "edbf000000001400000000000000000000000000000000000000000000000000" +
                    "000000004e657400000000000000000000000000000000000000000000000000" +
                    "0000000000000000000000000000000000000000000000000000000000000000" +
                    "0000000003000000",
            ),
            packet,
        )
    }

    @Test
    fun `setup packet matches python-broadlink with both fields at the 32 byte maximum`() {
        val packet = BroadlinkPackets.buildSetupPacket("A".repeat(32), "B".repeat(32), SecurityMode.WPA_WPA2)
        assertArrayEquals(
            hex(
                "0000000000000000000000000000000000000000000000000000000000000000" +
                    "67cf000000001400000000000000000000000000000000000000000000000000" +
                    "0000000041414141414141414141414141414141414141414141414141414141" +
                    "4141414142424242424242424242424242424242424242424242424242424242" +
                    "4242424220200400",
            ),
            packet,
        )
    }

    @Test
    fun `setup packet layout and checksum`() {
        val ssid = "MyHomeWiFi"
        val password = "correct-horse-42"
        val packet = BroadlinkPackets.buildSetupPacket(ssid, password, SecurityMode.WPA2)

        assertEquals(136, packet.size)
        assertEquals(0x14, packet.u8(0x26))
        assertArrayEquals(ssid.toByteArray(), packet.copyOfRange(0x44, 0x44 + ssid.length))
        assertTrue(packet.copyOfRange(0x44 + ssid.length, 0x64).all { it == 0.toByte() })
        assertArrayEquals(password.toByteArray(), packet.copyOfRange(0x64, 0x64 + password.length))
        assertTrue(packet.copyOfRange(0x64 + password.length, 0x84).all { it == 0.toByte() })
        assertEquals(ssid.length, packet.u8(0x84))
        assertEquals(password.length, packet.u8(0x85))
        assertEquals(3, packet.u8(0x86))
        assertEquals(0, packet.u8(0x87))

        // Little-endian checksum = 0xBEAF + sum of every other byte.
        var expected = 0xBEAF
        packet.forEachIndexed { i, b -> if (i != 0x20 && i != 0x21) expected += b.toInt() and 0xFF }
        assertEquals(expected and 0xFFFF, packet.u8(0x20) or (packet.u8(0x21) shl 8))
        assertEquals(0xC871, packet.u8(0x20) or (packet.u8(0x21) shl 8))

        // Everything outside the documented fields is zero.
        val fields = setOf(0x20, 0x21, 0x26, 0x84, 0x85, 0x86) + (0x44 until 0x84)
        packet.forEachIndexed { i, b -> if (i !in fields) assertEquals("offset $i", 0, b.toInt()) }
    }

    @Test
    fun `security mode codes`() {
        val codes = SecurityMode.entries.associateWith {
            BroadlinkPackets.buildSetupPacket("Net", "password1", it).u8(0x86)
        }
        assertEquals(mapOf(SecurityMode.OPEN to 0, SecurityMode.WPA to 2, SecurityMode.WPA2 to 3, SecurityMode.WPA_WPA2 to 4), codes)
    }

    @Test
    fun `open network ignores the password`() {
        val packet = BroadlinkPackets.buildSetupPacket("Net", "leftover-text", SecurityMode.OPEN)
        assertEquals(0, packet.u8(0x85))
        assertTrue(packet.copyOfRange(0x64, 0x84).all { it == 0.toByte() })
    }

    @Test
    fun `non-ASCII text is encoded as UTF-8 with byte lengths`() {
        // python-broadlink writes ord(c) (Latin-1: 4b e1 76 e9); routers advertise UTF-8 SSIDs, as do
        // rbroadlink and waringer/broadlink. See docs/PROTOCOL.md.
        val packet = BroadlinkPackets.buildSetupPacket("Kávé", "jelszó12", SecurityMode.WPA)
        assertArrayEquals(hex("4bc3a176c3a9"), packet.copyOfRange(0x44, 0x4A))
        assertEquals(6, packet.u8(0x84))
        assertArrayEquals("jelszó12".toByteArray(Charsets.UTF_8), packet.copyOfRange(0x64, 0x6D))
        assertEquals(9, packet.u8(0x85))
    }

    @Test
    fun `validation rejects what the packet cannot carry`() {
        assertEquals(CredentialProblem.SsidEmpty, BroadlinkPackets.validate("", "password1", SecurityMode.WPA2))
        assertEquals(CredentialProblem.SsidTooLong(33), BroadlinkPackets.validate("x".repeat(33), "password1", SecurityMode.WPA2))
        assertEquals(CredentialProblem.SsidTooLong(34), BroadlinkPackets.validate("é".repeat(17), "password1", SecurityMode.WPA2))
        assertEquals(CredentialProblem.PasswordTooLong(33), BroadlinkPackets.validate("Net", "p".repeat(33), SecurityMode.WPA2))
        assertEquals(CredentialProblem.PasswordTooLong(63), BroadlinkPackets.validate("Net", "p".repeat(63), SecurityMode.WPA2))
        assertEquals(CredentialProblem.PasswordTooShort(7), BroadlinkPackets.validate("Net", "1234567", SecurityMode.WPA2))
        assertNull(BroadlinkPackets.validate("Net", "12345678", SecurityMode.WPA2))
        assertNull(BroadlinkPackets.validate("Net", "", SecurityMode.OPEN))
        assertNull(BroadlinkPackets.validate("x".repeat(32), "p".repeat(32), SecurityMode.WPA2))

        val error = assertThrows(InvalidCredentialsException::class.java) {
            BroadlinkPackets.buildSetupPacket("Net", "p".repeat(33), SecurityMode.WPA2)
        }
        assertEquals(CredentialProblem.PasswordTooLong(33), error.problem)
        assertFalse("exception text must not contain the password", error.message.orEmpty().contains("ppp"))
    }

    @Test
    fun `hello packet matches python-broadlink`() {
        val time = HelloTime(LocalDateTime.of(2026, 10, 4, 14, 37), utcOffsetHours = 1)
        val packet = BroadlinkPackets.buildHelloPacket(time, ipv4("192.168.1.23"), 51234)
        assertArrayEquals(
            hex("000000000000000001000000ea07250e1a07040a000000001701a8c022c8000073c20000000006000000000000000000"),
            packet,
        )
        assertEquals(48, packet.size)
        assertEquals(0x06, packet.u8(0x26))
        assertEquals(7, packet.u8(0x11)) // 2026-10-04 is a Sunday: ISO weekday 7
    }

    @Test
    fun `hello packet with negative UTC offset and no local address`() {
        val time = HelloTime(LocalDateTime.of(2026, 1, 5, 23, 59), utcOffsetHours = -5)
        val packet = BroadlinkPackets.buildHelloPacket(time, null, 0)
        assertArrayEquals(hex("fbffffff"), packet.copyOfRange(0x08, 0x0C))
        assertTrue(packet.copyOfRange(0x18, 0x1E).all { it == 0.toByte() })
        assertEquals(BroadlinkPackets.checksum(packet), packet.u8(0x20) or (packet.u8(0x21) shl 8))
    }

    @Test
    fun `setup acknowledgement as documented by waringer-broadlink`() {
        val ack = hex("0000000000000000000000000000000000000000000000000000000000000000c4be0000000015000000000000000000")
        assertTrue(BroadlinkPackets.isSetupAck(ack))

        assertFalse(BroadlinkPackets.isSetupAck(ack.copyOf().also { it[0x20] = 0 }))
        assertFalse(BroadlinkPackets.isSetupAck(ack.copyOf().also { it[0x26] = 0x14 }))
        assertFalse(BroadlinkPackets.isSetupAck(ack, length = 0x2F))
        // A reflected copy of our own setup packet is not an acknowledgement.
        assertFalse(BroadlinkPackets.isSetupAck(BroadlinkPackets.buildSetupPacket("Net", "", SecurityMode.OPEN)))
    }

    @Test
    fun `hello response parsing`() {
        val response = ByteArray(0x88)
        response[0x26] = 0x07
        response[0x34] = 0x0F
        response[0x35] = 0x52
        hex("ffeeddccbbaa").copyInto(response, 0x3A)
        "润新水处理器".toByteArray(Charsets.UTF_8).copyInto(response, 0x40)
        response[0x7F] = 1

        val parsed = BroadlinkPackets.parseHelloResponse(response)!!
        assertEquals(0x520F, parsed.deviceType)
        assertEquals("AA:BB:CC:DD:EE:FF", parsed.mac)
        assertEquals("润新水处理器", parsed.name)
        assertTrue(parsed.isLocked)

        assertNull(BroadlinkPackets.parseHelloResponse(response, length = 0x3F))
        val helloRequest = BroadlinkPackets.buildHelloPacket(HelloTime(LocalDateTime.now(), 0), null, 0)
        assertNull(BroadlinkPackets.parseHelloResponse(helloRequest.copyOf(0x80)))
    }

    @Test
    fun `checksum of an empty packet is the seed`() {
        assertEquals(0xBEAF, BroadlinkPackets.checksum(ByteArray(0x30)))
        assertEquals(0xBEAF + 0xFF * 4 and 0xFFFF, BroadlinkPackets.checksum(ByteArray(0x30) { if (it < 4) -1 else 0 }))
        // Bytes 0x20/0x21 (the checksum field itself) are excluded, and the sum wraps at 16 bits.
        assertEquals((0xBEAF + 0xFF * (0x222 - 2)) and 0xFFFF, BroadlinkPackets.checksum(ByteArray(0x222) { -1 }))
    }

    private fun ByteArray.u8(offset: Int) = this[offset].toInt() and 0xFF

    private fun ipv4(text: String) = InetAddress.getByName(text) as Inet4Address
}

internal fun hex(text: String): ByteArray =
    ByteArray(text.length / 2) { i -> text.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
