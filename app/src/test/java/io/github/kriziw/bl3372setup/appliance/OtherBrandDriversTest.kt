package io.github.kriziw.bl3372setup.appliance

import io.github.kriziw.bl3372setup.appliance.bwt.BwtPerlaDriver
import io.github.kriziw.bl3372setup.appliance.bwt.BwtPerlaProtocol
import io.github.kriziw.bl3372setup.appliance.gruenbeck.GruenbeckDriver
import io.github.kriziw.bl3372setup.appliance.gruenbeck.GruenbeckProtocol
import io.github.kriziw.bl3372setup.appliance.syr.SyrNeoSoftDriver
import io.github.kriziw.bl3372setup.appliance.syr.SyrNeoSoftProtocol
import io.github.kriziw.bl3372setup.network.FakeHttpServer
import io.github.kriziw.bl3372setup.network.LocalHttp
import io.github.kriziw.bl3372setup.network.LoginRejectedException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class OtherBrandDriversTest {
    // --- BWT Perla -----------------------------------------------------------------------------

    private val perlaDuplex = """
        {"ActiveErrorIDs":"5,32","BlendedWaterSinceSetup_l":123456,"CapacityColumn1_ml_dH":2000000,
         "CapacityColumn2_ml_dH":1000000,"CurrentFlowrate_l_h":300,"FirmwareVersion":"2.0206",
         "HardnessIN_dH":20,"HardnessOUT_dH":4,"HolidayModeStartTime":-1,"LastRegenerationColumn1":"2026-10-04 02:10:00",
         "OutOfService":0,"RegenerationCountSinceSetup":812,"RegenerativLevel":35,"RegenerativRemainingDays":40,
         "ShowError":1,"WaterTreatedCurrentDay_l":80,"WaterTreatedCurrentMonth_l":400,"WaterTreatedCurrentYear_l":8000}
    """.trimIndent()

    @Test
    fun `BWT Perla readings are converted to blended water`() {
        val state = BwtPerlaProtocol.parse(JSONObject(perlaDuplex))
        assertEquals("BWT Perla Duplex", state.model)
        // 2 000 000 ml·°dH at 20 -> 4 °dH = 125 L.
        assertEquals(125.0, state.reading(ReadingKind.REMAINING_CAPACITY_L)!!.value, 1e-9)
        assertEquals(62.5, state.reading(ReadingKind.REMAINING_CAPACITY_2_L)!!.value, 1e-9)
        // 80 L of 0 °dH water blended to 4 °dH from 20 °dH raw water = 100 L.
        assertEquals(100.0, state.reading(ReadingKind.WATER_TODAY_L)!!.value, 1e-9)
        assertEquals(35.0, state.reading(ReadingKind.SALT_LEVEL_PERCENT)!!.value, 0.0)
        assertEquals(listOf(AlertKind.SALT_LOW, AlertKind.MAINTENANCE_DUE), state.alerts.map { it.kind })
        assertTrue(state.alerts.none { it.fatal })
        assertEquals(ApplianceStatus.IN_SERVICE, state.status)
        assertTrue(state.settings.isEmpty() && state.actions.isEmpty())
    }

    @Test
    fun `BWT Perla One and holiday mode`() {
        val json = JSONObject(perlaDuplex).put("CapacityColumn2_ml_dH", -1).put("HolidayModeStartTime", 1)
        val state = BwtPerlaProtocol.parse(json)
        assertEquals("BWT Perla One", state.model)
        assertNull(state.reading(ReadingKind.REMAINING_CAPACITY_2_L))
        assertEquals(ApplianceStatus.HOLIDAY, state.status)
    }

    @Test
    fun `BWT sends the login code and reports a wrong one`() = runBlocking {
        FakeHttpServer { r ->
            if (r.headers["authorization"] == "Basic dXNlcjoxMjM0NQ==") FakeHttpServer.ok(perlaDuplex) else FakeHttpServer.ok("", "404 Not Found")
        }.use { server ->
            val good = BwtPerlaDriver(LocalHttp(server.connector), ApplianceAddress("perla", server.port, secret = "12345"))
            assertEquals("BWT Perla Duplex", good.read().model)
            assertEquals("/api/GetCurrentData", server.requests.first().path)
            val bad = BwtPerlaDriver(LocalHttp(server.connector), ApplianceAddress("perla", server.port, secret = "0"))
            val error = runCatching { bad.read() }.exceptionOrNull()
            assertTrue(error is LoginRejectedException)
        }
    }

    // --- Grünbeck softliQ -------------------------------------------------------------------------

    @Test
    fun `Grünbeck mux queries and replies`() {
        assertEquals("id=2444&show=D_A_1_1|D_C_5_1~", GruenbeckProtocol.query(listOf("D_A_1_1", "D_C_5_1")))
        assertEquals("id=2444&code=245&show=D_K_10_1~", GruenbeckProtocol.query(listOf("D_K_10_1"), code = "245"))
        assertEquals("id=2444&edit=D_C_5_1>2&show=D_C_5_1~", GruenbeckProtocol.query(listOf("D_C_5_1"), edit = "D_C_5_1" to "2"))
        val values = GruenbeckProtocol.parse("<data><code>ok</code><D_A_1_1>0.32</D_A_1_1><D_Y_5>4</D_Y_5><D_C_5_1>1</D_C_5_1></data>")
        assertEquals(mapOf("D_A_1_1" to "0.32", "D_Y_5" to "4", "D_C_5_1" to "1"), values)
        val state = GruenbeckProtocol.state(values, "Grünbeck softliQ:SC18", "E4_12h")
        assertEquals(ApplianceStatus.REGENERATING, state.status)
        assertEquals(RegenerationStep.BACKWASH, state.step)
        assertEquals(0.32, state.reading(ReadingKind.FLOW_M3_H)!!.value, 1e-9)
        assertEquals(1, (state.setting(SettingKey.OPERATING_MODE) as ApplianceSetting.Options).value)
        assertEquals(Alert(AlertKind.SALT_EMPTY, "E4", fatal = true), state.alerts.single())
        assertNull(GruenbeckProtocol.alert("0"))
    }

    @Test
    fun `Grünbeck mode change is confirmed by the echo and a fresh read`() = runBlocking {
        var mode = "0"
        FakeHttpServer { r ->
            r.body.substringAfter("edit=D_C_5_1>", "").takeIf { it.isNotEmpty() }?.let { mode = it.substringBefore('&') }
            val type = if ("code=290" in r.body) "<D_F_4>2</D_F_4>" else ""
            FakeHttpServer.ok("<data><code>ok</code>$type<D_C_5_1>$mode</D_C_5_1><D_Y_5>0</D_Y_5><D_Y_6>V01.01.06</D_Y_6></data>")
        }.use { server ->
            val driver = GruenbeckDriver(LocalHttp(server.connector), ApplianceAddress("softliq", server.port))
            val state = driver.read()
            assertEquals("Grünbeck softliQ:SC23", state.model)
            assertEquals(ApplianceStatus.IN_SERVICE, state.status)
            val result = driver.apply(ApplianceChange.SetOption(SettingKey.OPERATING_MODE, 2))
            assertTrue(result is ChangeResult.Confirmed)
            assertTrue(server.requests.any { it.body == "id=2444&edit=D_C_5_1>2&show=D_C_5_1~" })
            assertEquals("application/x-www-form-urlencoded", server.requests.first().headers["content-type"])
        }
    }

    // --- SYR NeoSoft ------------------------------------------------------------------------------

    private val neoSoft2500 = """
        {"getALA":"ff","getWRN":"02","getNOT":"ff","getRE1":395,"getSV1":19,"getSS1":32,"getFLO":0,"getVOL":13272,
         "getIWH":19,"getOWH":7,"getWHU":0,"getRG1":0,"getRMO":4,"getRPD":3,"getRTM":"01:00","getTYP":206,
         "getVER":"NSS.V.2.36","getSRV":"14.02.2027","getLAR":1774998000}
    """.trimIndent()

    @Test
    fun `SYR NeoSoft get-all is decoded`() {
        val state = SyrNeoSoftProtocol.parse(JSONObject(neoSoft2500))
        assertEquals("SYR NeoSoft 2500", state.model)
        assertEquals(395.0, state.reading(ReadingKind.REMAINING_CAPACITY_L)!!.value, 0.0)
        assertEquals(13.272, state.reading(ReadingKind.WATER_TOTAL_M3)!!.value, 1e-9)
        assertEquals(listOf(Alert(AlertKind.SALT_LOW, "02", fatal = false)), state.alerts)
        assertEquals(4, (state.setting(SettingKey.REGENERATION_MODE) as ApplianceSetting.Options).value)
        assertEquals(LocalTime.of(1, 0), (state.setting(SettingKey.REGENERATION_TIME) as ApplianceSetting.Time).value)
        assertEquals("14.02.2027", state.nextMaintenance)
        // Values arrive as numbers or strings depending on firmware.
        val strings = SyrNeoSoftProtocol.parse(JSONObject(neoSoft2500).put("getRE1", "395").put("getRG1", "1"))
        assertEquals(395.0, strings.reading(ReadingKind.REMAINING_CAPACITY_L)!!.value, 0.0)
        assertEquals(ApplianceStatus.REGENERATING, strings.status)
    }

    @Test
    fun `SYR NeoSoft 5000 has no interval or time`() {
        val state = SyrNeoSoftProtocol.parse(JSONObject(neoSoft2500).put("getRE2", 410))
        assertEquals("SYR NeoSoft 5000", state.model)
        assertEquals(listOf(SettingKey.REGENERATION_MODE), state.settings.map { it.key })
    }

    @Test
    fun `SYR set paths are lower case with literal colons`() {
        assertEquals("/neosoft/set/rtm/02:30", SyrNeoSoftProtocol.setPath("RTM", "02:30"))
        assertTrue(SyrNeoSoftProtocol.accepted("""{"setrmo2":"OK"}"""))
        assertFalse(SyrNeoSoftProtocol.accepted("""{"setrmo9":"MIMA"}"""))
    }

    @Test
    fun `SYR regeneration time is set and read back`() = runBlocking {
        var time = "01:00"
        FakeHttpServer { r ->
            if (r.path.startsWith("/neosoft/set/rtm/")) {
                time = r.path.substringAfterLast('/')
                FakeHttpServer.ok("""{"setrtm${time}":"OK"}""")
            } else {
                FakeHttpServer.ok(JSONObject(neoSoft2500).put("getRTM", time).toString())
            }
        }.use { server ->
            val driver = SyrNeoSoftDriver(LocalHttp(server.connector), ApplianceAddress("neosoft", server.port))
            driver.read()
            val result = driver.apply(ApplianceChange.SetTime(SettingKey.REGENERATION_TIME, LocalTime.of(2, 30)))
            assertTrue(result is ChangeResult.Confirmed)
            assertTrue(server.requests.any { it.path == "/neosoft/set/rtm/02:30" })
        }
    }
}
