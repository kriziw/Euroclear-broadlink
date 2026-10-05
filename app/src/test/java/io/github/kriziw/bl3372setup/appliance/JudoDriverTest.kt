package io.github.kriziw.bl3372setup.appliance

import io.github.kriziw.bl3372setup.appliance.judo.JudoDriver
import io.github.kriziw.bl3372setup.appliance.judo.JudoProtocol
import io.github.kriziw.bl3372setup.network.FakeHttpServer
import io.github.kriziw.bl3372setup.network.LocalHttp
import io.github.kriziw.bl3372setup.network.LoginRejectedException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/** Values are the worked examples from JUDO's official command tables. */
class JudoDriverTest {
    /** A JUDO i-soft SAFE+ module: reads answer from [registers], writes update them. */
    private class FakeJudo(type: String = "33") {
        val registers = ConcurrentHashMap(
            mapOf(
                "FF00" to type, "0100" to "6b1502", "2300" to "00", "5700" to "14",
                "5600" to "f6541100", "5100" to "0600", "2900" to "2EDC0000", "2800" to "EC221000",
            ),
        )
        var authorised = true
        val server = FakeHttpServer { request ->
            if (!authorised) return@FakeHttpServer FakeHttpServer.ok("", "401 Unauthorized")
            val command = request.path.removePrefix("/api/rest/").uppercase()
            when {
                command.startsWith("30") && command.length == 6 ->
                    registers["5100"] = command.substring(4) + "00" // 1 byte °dH, read back as 2 bytes
                command.startsWith("56") && command.length == 8 ->
                    registers["5600"] = command.substring(4) + registers.getValue("5600").substring(4)
                command.startsWith("57") && command.length == 6 -> registers["5700"] = command.substring(4)
            }
            FakeHttpServer.ok("""{"data":"${registers[command] ?: ""}"}""")
        }

        fun driver(user: String = "admin", password: String = "Connectivity") =
            JudoDriver(LocalHttp(server.connector), ApplianceAddress("judo", server.port, user, password), minGapMillis = 0)
    }

    @Test
    fun `official examples encode and decode`() {
        assertEquals("/api/rest/56004448", JudoProtocol.path(JudoProtocol.SALT, JudoProtocol.le(18500, 2)))
        assertEquals("/api/rest/570014", JudoProtocol.path(JudoProtocol.SALT_WARNING, byteArrayOf(20)))
        assertEquals("/api/rest/350000", JudoProtocol.path(JudoProtocol.REGENERATE, byteArrayOf(0)))
        assertEquals("/api/rest/3C00", JudoProtocol.path(JudoProtocol.CLOSE_VALVE))
        assertEquals("2.21k", JudoProtocol.firmware(JudoProtocol.hex("6b1502")))
        assertEquals("1.0.12", JudoProtocol.firmware(JudoProtocol.hex("0C0001")))
        assertEquals(1_057_516L, JudoProtocol.leInt(JudoProtocol.hex("EC221000"), 0, 4))
    }

    @Test
    fun `reads an i-soft SAFE+ with its valve`() = runBlocking {
        val judo = FakeJudo()
        judo.server.use {
            val partial = mutableListOf<ApplianceState>()
            val state = judo.driver().read { partial += it }
            assertEquals("JUDO i-soft SAFE+", state.model)
            assertEquals(0x33, state.modelCode)
            assertEquals("2.21k", state.firmware)
            assertEquals(21.75, state.reading(ReadingKind.SALT_STOCK_KG)!!.value, 1e-9)
            assertEquals(17.0, state.reading(ReadingKind.SALT_RANGE_DAYS)!!.value, 0.0)
            assertEquals(6.0, state.reading(ReadingKind.TARGET_HARDNESS)!!.value, 0.0)
            assertEquals(56.366, state.reading(ReadingKind.SOFT_WATER_TOTAL_M3)!!.value, 1e-9)
            assertEquals(1057.516, state.reading(ReadingKind.WATER_TOTAL_M3)!!.value, 1e-9)
            assertEquals(20.0, (state.setting(SettingKey.SALT_WARNING_DAYS) as ApplianceSetting.Number).value!!, 0.0)
            assertEquals(listOf(ApplianceAction.REGENERATE, ApplianceAction.CLOSE_VALVE, ApplianceAction.OPEN_VALVE), state.actions)
            assertTrue(state.complete)
            assertTrue(partial.isNotEmpty() && partial.all { !it.complete })
            val auth = judo.server.requests.first().headers["authorization"]
            assertEquals("Basic YWRtaW46Q29ubmVjdGl2aXR5", auth)
        }
    }

    @Test
    fun `a setting is confirmed by reading it back`() = runBlocking {
        val judo = FakeJudo()
        judo.server.use {
            val driver = judo.driver()
            driver.read()
            val result = driver.apply(ApplianceChange.SetNumber(SettingKey.TARGET_HARDNESS, 8.0))
            assertTrue(result is ChangeResult.Confirmed)
            assertEquals("/api/rest/300008", judo.server.requests.first { it.path.startsWith("/api/rest/30") }.path)
            assertEquals(8.0, (result as ChangeResult.Confirmed).state!!.reading(ReadingKind.TARGET_HARDNESS)!!.value, 0.0)

            val salt = driver.apply(ApplianceChange.SetNumber(SettingKey.SALT_STOCK, 18.5))
            assertTrue(salt is ChangeResult.Confirmed)
            assertTrue(judo.server.requests.any { it.path == "/api/rest/56004448" })
        }
    }

    @Test
    fun `a write the module ignores is not confirmed and never resent`() = runBlocking {
        // Identifies as an i-soft SAFE+, but every other register keeps answering 0600.
        val ignoring = FakeHttpServer { r -> FakeHttpServer.ok("""{"data":"${if (r.path.endsWith("FF00")) "33" else "0600"}"}""") }
        ignoring.use {
            val driver = JudoDriver(LocalHttp(ignoring.connector), ApplianceAddress("judo", ignoring.port, "admin", "x"), minGapMillis = 0)
            driver.read()
            val result = driver.apply(ApplianceChange.SetNumber(SettingKey.TARGET_HARDNESS, 9.0))
            assertTrue(result is ChangeResult.NotConfirmed)
            assertEquals(1, ignoring.requests.count { it.path == "/api/rest/300009" })
        }
    }

    @Test
    fun `actions are accepted without a read-back`() = runBlocking {
        val judo = FakeJudo()
        judo.server.use {
            val driver = judo.driver()
            driver.read()
            assertTrue(driver.apply(ApplianceChange.Run(ApplianceAction.REGENERATE)) is ChangeResult.Accepted)
            assertTrue(judo.server.requests.any { it.path == "/api/rest/350000" })
        }
    }

    @Test
    fun `SOFTwell is monitoring only`() = runBlocking {
        val judo = FakeJudo(type = "34")
        judo.server.use {
            val state = judo.driver().read()
            assertEquals("JUDO SOFTwell P", state.model)
            assertTrue(state.settings.isEmpty() && state.actions.isEmpty())
            assertEquals(56.366, state.reading(ReadingKind.SOFT_WATER_TOTAL_M3)!!.value, 1e-9)
        }
    }

    @Test(expected = LoginRejectedException::class)
    fun `a wrong password is reported as a rejected login`(): Unit = runBlocking {
        val judo = FakeJudo().apply { authorised = false }
        judo.server.use { judo.driver(password = "wrong").read() }
    }
}
