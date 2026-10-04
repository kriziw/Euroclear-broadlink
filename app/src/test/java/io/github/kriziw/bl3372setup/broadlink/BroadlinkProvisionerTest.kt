package io.github.kriziw.bl3372setup.broadlink

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

class BroadlinkProvisionerTest {
    private val packet = BroadlinkPackets.buildSetupPacket("MyHomeWiFi", "correct-horse-42", SecurityMode.WPA2)

    @Test
    fun `stops after the device acknowledges`() = runBlocking {
        FakeBroadlinkDevice { listOf(FakeBroadlinkDevice.ACK) }.use { device ->
            val rounds = mutableListOf<Int>()
            val outcome = BroadlinkProvisioner(loopbackSockets, maxAttempts = 3, ackTimeoutMillis = 2_000)
                .send(packet, listOf(device.address)) { round, _ -> rounds += round }

            assertEquals(BroadlinkProvisioner.Outcome.Acknowledged(InetAddress.getLoopbackAddress(), 1), outcome)
            assertEquals(listOf(1), rounds)
            assertEquals(1, device.received.size)
            assertArrayEquals(packet, device.received.single().first)
        }
    }

    @Test
    fun `resends a bounded number of times when nothing answers`() = runBlocking {
        FakeBroadlinkDevice().use { device ->
            val outcome = BroadlinkProvisioner(loopbackSockets, maxAttempts = 3, ackTimeoutMillis = 150)
                .send(packet, listOf(device.address))

            assertEquals(BroadlinkProvisioner.Outcome.NotAcknowledged(3), outcome)
            Thread.sleep(100)
            assertEquals(3, device.received.size)
            device.received.forEach { (data, _) -> assertArrayEquals(packet, data) }
        }
    }

    @Test
    fun `ignores replies that are not acknowledgements`() = runBlocking {
        val noise = ByteArray(0x30).also { it[0x26] = 0x07 }
        FakeBroadlinkDevice { listOf(noise, ByteArray(4), FakeBroadlinkDevice.ACK) }.use { device ->
            val outcome = BroadlinkProvisioner(loopbackSockets, ackTimeoutMillis = 2_000)
                .send(packet, listOf(device.address))
            assertTrue(outcome is BroadlinkProvisioner.Outcome.Acknowledged)
        }
    }

    @Test
    fun `sends the same packet to every destination each round`() = runBlocking {
        FakeBroadlinkDevice().use { first ->
            FakeBroadlinkDevice().use { second ->
                BroadlinkProvisioner(loopbackSockets, maxAttempts = 2, ackTimeoutMillis = 100)
                    .send(packet, listOf(first.address, second.address))
                Thread.sleep(100)
                assertEquals(2, first.received.size)
                assertEquals(2, second.received.size)
            }
        }
    }

    @Test
    fun `reports the link going away after a successful round`() = runBlocking {
        FakeBroadlinkDevice().use { device ->
            val sockets = SocketFactory { FailingAfterFirstSend() }
            val outcome = BroadlinkProvisioner(sockets, maxAttempts = 3, ackTimeoutMillis = 100)
                .send(packet, listOf(device.address))
            assertEquals(BroadlinkProvisioner.Outcome.LinkLostAfterSend(1), outcome)
        }
    }

    @Test(expected = IOException::class)
    fun `fails when not even the first round can be sent`(): Unit = runBlocking {
        FakeBroadlinkDevice().use { device ->
            val sockets = SocketFactory { DatagramSocket(0, InetAddress.getLoopbackAddress()).apply { close() } }
            BroadlinkProvisioner(sockets).send(packet, listOf(device.address))
        }
    }

    /** Simulates the module's AP disappearing: the kernel rejects sends with ENONET. */
    private class FailingAfterFirstSend : DatagramSocket(0, InetAddress.getLoopbackAddress()) {
        private var sends = 0

        override fun send(p: DatagramPacket) {
            if (++sends > 1) throw SocketException("sendto failed: ENONET (Machine is not on the network)")
            super.send(p)
        }
    }
}
