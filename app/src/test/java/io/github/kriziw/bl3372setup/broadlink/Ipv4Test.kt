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

    @Test
    fun `IPv4 literals are parsed without DNS`() {
        assertEquals(ip("192.168.20.15"), Ipv4.parse(" 192.168.20.15 "))
        assertNull(Ipv4.parse("192.168.20.256"))
        assertNull(Ipv4.parse("softener.lan"))
        assertNull(Ipv4.parse("192.168.20"))
    }

    @Test
    fun `subnets for cross-VLAN scanning`() {
        val subnet = Ipv4.parseSubnet("192.168.20.77/24")!!
        assertEquals("192.168.20.0/24", subnet.toString())
        val hosts = subnet.hosts()
        assertEquals(254, hosts.size)
        assertEquals(ip("192.168.20.1"), hosts.first())
        assertEquals(ip("192.168.20.254"), hosts.last())
        assertEquals(1022, Ipv4.parseSubnet("10.0.0.0/22")!!.hosts().size)
        assertNull("too large to sweep", Ipv4.parseSubnet("10.0.0.0/21"))
        assertNull(Ipv4.parseSubnet("10.0.0.0"))
        assertEquals("10.1.2.0/24", Ipv4.surrounding24(ip("10.1.2.3")).toString())
    }

    @Test
    fun `same subnet check`() {
        assertEquals(true, Ipv4.sameSubnet(ip("192.168.1.10"), ip("192.168.1.200"), 24))
        assertEquals(false, Ipv4.sameSubnet(ip("192.168.1.10"), ip("192.168.20.10"), 24))
        assertEquals(true, Ipv4.sameSubnet(ip("192.168.1.10"), ip("192.168.20.10"), 16))
    }

    private fun ip(text: String) = InetAddress.getByName(text) as Inet4Address
}
