package io.github.kriziw.bl3372setup.broadlink

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.time.LocalDateTime

class BroadlinkDiscoveryTest {
    private val fixedClock = { HelloTime(LocalDateTime.of(2026, 10, 4, 14, 37), 1) }

    @Test
    fun `finds a device once and advertises the real reply port`() = runBlocking {
        val reply = FakeBroadlinkDevice.helloResponse(0x520F, hex("563412d7d11c"), "润新水处理器")
        FakeBroadlinkDevice { request ->
            if (request.size == 0x30 && request[0x26] == 0x06.toByte()) listOf(reply) else emptyList()
        }.use { device ->
            val found = mutableListOf<DiscoveredDevice>()
            val local = InetAddress.getByName("192.168.1.23") as Inet4Address
            val devices = BroadlinkDiscovery(loopbackSockets, fixedClock, durationMillis = 700, resendIntervalMillis = 200)
                .discover(listOf(device.address), local) { found += it }

            val only = devices.single()
            assertEquals(only, found.single())
            assertEquals(InetAddress.getLoopbackAddress(), only.address)
            assertEquals(0x520F, only.deviceType)
            assertTrue(only.isRunxinBl3372)
            assertEquals("1C:D1:D7:12:34:56", only.mac)
            assertEquals("润新水处理器", only.name)

            // It keeps re-broadcasting like python-broadlink does; every hello is well formed and
            // names the port the reply actually came back to.
            assertTrue(device.received.size >= 3)
            device.received.forEach { (hello, sender) ->
                assertEquals(0x30, hello.size)
                assertEquals(BroadlinkPackets.checksum(hello), (hello[0x20].toInt() and 0xFF) or ((hello[0x21].toInt() and 0xFF) shl 8))
                assertEquals(sender.port, (hello[0x1C].toInt() and 0xFF) or ((hello[0x1D].toInt() and 0xFF) shl 8))
                assertEquals(listOf(23, 1, 168, 192), hello.copyOfRange(0x18, 0x1C).map { it.toInt() and 0xFF })
            }
        }
    }

    @Test
    fun `a paced unicast sweep finds the target and stops early`() = runBlocking {
        val reply = FakeBroadlinkDevice.helloResponse(0x520F, hex("563412d7d11c"), "")
        FakeBroadlinkDevice { listOf(reply) }.use { device ->
            // 60 silent destinations (other ports on loopback) plus the device, in batches of 16.
            val silent = (1..60).map { java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 9) }
            val started = System.nanoTime()
            val found = BroadlinkDiscovery(loopbackSockets, fixedClock, durationMillis = 5_000, resendIntervalMillis = 2_000, batchSize = 16)
                .discover(silent + device.address, null, stopWhen = { it.mac == "1C:D1:D7:12:34:56" })
            assertEquals(1, found.size)
            assertTrue("stopped early", (System.nanoTime() - started) / 1_000_000 < 4_000)
            assertEquals("1C:D1:D7:12:34:56", found.single().mac)
            assertEquals(listOf(0x1C, 0xD1, 0xD7, 0x12, 0x34, 0x56), found.single().macBytes.map { it.toInt() and 0xFF })
        }
    }

    @Test
    fun `returns nothing when no device answers`() = runBlocking {
        FakeBroadlinkDevice().use { device ->
            val devices = BroadlinkDiscovery(loopbackSockets, fixedClock, durationMillis = 300, resendIntervalMillis = 100)
                .discover(listOf(device.address), null)
            assertTrue(devices.isEmpty())
        }
    }
}
