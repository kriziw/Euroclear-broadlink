package io.github.kriziw.bl3372setup.appliance

import io.github.kriziw.bl3372setup.appliance.syr.SyrWifiProvisioner
import io.github.kriziw.bl3372setup.network.FakeHttpServer
import io.github.kriziw.bl3372setup.network.LocalHttp
import io.github.kriziw.bl3372setup.network.TcpConnector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.Socket

class ApplianceOnboardingTest {
    /** A NeoSoft on its access point: accepts the key and SSID, then reports connecting, then connected. */
    private fun fakeNeoSoft(polls: List<Int>, ip: String = "192.168.1.57") = run {
        var poll = 0
        FakeHttpServer { r ->
            val path = r.path
            when {
                path.startsWith("/neosoft/set/wfk/") -> FakeHttpServer.ok("""{"setwfk":"OK"}""")
                path.startsWith("/neosoft/set/wfc/") -> FakeHttpServer.ok("""{"setwfc":"OK"}""")
                path == "/neosoft/get/wfs" -> FakeHttpServer.ok("""{"getWFS":${polls[minOf(poll++, polls.lastIndex)]}}""")
                path == "/neosoft/get/wip" -> FakeHttpServer.ok("""{"getWIP":"$ip"}""")
                else -> FakeHttpServer.ok("", "404 Not Found")
            }
        }
    }

    @Test
    fun `SYR gets the key before the SSID, then reports its new address`() = runBlocking {
        fakeNeoSoft(polls = listOf(1, 1, 2)).use { server ->
            val result = SyrWifiProvisioner(LocalHttp(server.connector), "192.168.4.1", server.port, pollMillis = 10)
                .provision("Home Net", "s3cret:pass")
            assertEquals(SyrWifiProvisioner.Result.Connected("192.168.1.57"), result)
            val (key, ssid) = server.requests.take(2).map { it.path }
            // Key first, values literal except the space; the colon is not percent-encoded.
            assertEquals("/neosoft/set/wfk/s3cret:pass", key)
            assertEquals("/neosoft/set/wfc/Home%20Net", ssid)
        }
    }

    @Test
    fun `SYR without a connection in time is reported, not retried`() = runBlocking {
        fakeNeoSoft(polls = listOf(1)).use { server ->
            val result = SyrWifiProvisioner(LocalHttp(server.connector), "192.168.4.1", server.port, pollMillis = 10, timeoutMillis = 50)
                .provision("Home", "password1")
            assertEquals(SyrWifiProvisioner.Result.NotConnected, result)
            assertEquals(1, server.requests.count { it.path.startsWith("/neosoft/set/wfc/") })
        }
    }

    @Test
    fun `SYR losing its access point after the settings counts as switching networks`() = runBlocking {
        var served = 0
        val server = FakeHttpServer { r ->
            served++
            if (r.path.startsWith("/neosoft/set/")) FakeHttpServer.ok("""{"set":"OK"}""") else FakeHttpServer.ok("", "500 Gone")
        }
        server.use {
            val result = SyrWifiProvisioner(LocalHttp(server.connector), "192.168.4.1", server.port, pollMillis = 10).provision("Home", "password1")
            assertEquals(SyrWifiProvisioner.Result.LinkLost, result)
        }
    }

    @Test
    fun `SYR path values keep what firmware expects literally`() {
        assertEquals("02:30", SyrWifiProvisioner.pathValue("02:30"))
        assertEquals("a%2Fb%3Fc%23d%25e%20f", SyrWifiProvisioner.pathValue("a/b?c#d%e f"))
        assertEquals("%C3%A9", SyrWifiProvisioner.pathValue("é"))
        assertTrue(SyrWifiProvisioner.needsEncoding("Home Net"))
        assertFalse(SyrWifiProvisioner.needsEncoding("HomeNet-2.4"))
    }

    @Test
    fun `the search reports only hosts whose port is open`() = runBlocking {
        val open = setOf("10.0.0.7", "10.0.0.42")
        val connector = TcpConnector { host, _, _ -> if (host in open) Socket() else throw IOException("refused") }
        var progress = 0
        val hosts = (1..254).map { "10.0.0.$it" }
        val found = ApplianceScanner.openHosts(connector, hosts, 80, parallel = 32) { progress = it }
        assertEquals(listOf("10.0.0.7", "10.0.0.42"), found)
        assertEquals(254, progress)
    }
}
