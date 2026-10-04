package io.github.kriziw.bl3372setup.broadlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress

class Ipv4Test {
    @Test
    fun `subnet broadcast address`() {
        assertEquals(ip("192.168.10.255"), Ipv4.broadcastAddress(ip("192.168.10.2"), 24))
        assertEquals(ip("192.168.255.255"), Ipv4.broadcastAddress(ip("192.168.10.2"), 16))
        assertEquals(ip("10.0.0.127"), Ipv4.broadcastAddress(ip("10.0.0.100"), 25))
        assertNull(Ipv4.broadcastAddress(ip("10.0.0.1"), 31))
        assertNull(Ipv4.broadcastAddress(ip("10.0.0.1"), 32))
    }

    @Test
    fun `destinations start with the limited broadcast and skip duplicates`() {
        assertEquals(
            listOf("255.255.255.255", "192.168.10.255", "192.168.10.1").map { InetSocketAddress(ip(it), 80) },
            Ipv4.destinations(ip("192.168.10.255"), unicast = ip("192.168.10.1")),
        )
        assertEquals(
            listOf(InetSocketAddress(ip("255.255.255.255"), 80)),
            Ipv4.destinations(null, unicast = ip("255.255.255.255")),
        )
    }

    private fun ip(text: String) = InetAddress.getByName(text) as Inet4Address
}
