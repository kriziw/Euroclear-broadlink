package io.github.kriziw.bl3372setup.appliance

/** How WaterCare relates to a softener family found in the Home Assistant community review. */
enum class BrandSupport {
    /** Local API with controls in WaterCare, experimental until confirmed on hardware. */
    LOCAL_CONTROL,

    /** Local API that only reports status, shown in WaterCare. */
    LOCAL_MONITOR,

    /** Reachable only through the vendor cloud; WaterCare stays local, so not supported. */
    CLOUD_ONLY,

    /** Bluetooth Low Energy; not supported by WaterCare yet. */
    BLUETOOTH,

    /** No connectivity of its own; community DIY sensor projects only. */
    DIY,
}

data class BrandEntry(val name: String, val models: String, val support: BrandSupport, val sourceUrl: String)

/**
 * Popular softeners discussed in the Home Assistant community and HACS, reviewed 2026-10-05.
 * Sources are the integrations or official API documents each entry was checked against.
 */
object BrandCatalogue {
    val entries: List<BrandEntry> = listOf(
        BrandEntry(
            "JUDO", "i-soft, i-soft K, i-soft SAFE+, i-soft K SAFE+, i-soft PRO, SOFTwell (Connectivity Module)",
            BrandSupport.LOCAL_CONTROL, "https://judo.eu/app/uploads/2024/11/API-KOMMANDOZEILEN.pdf",
        ),
        BrandEntry(
            "Grünbeck", "softliQ:SC18, SC23, MC16, MC32 (built-in web server)",
            BrandSupport.LOCAL_CONTROL, "https://github.com/tizianodeg/gruenbeck_softliQ_SC",
        ),
        BrandEntry(
            "SYR", "NeoSoft 2500 Connect, NeoSoft 5000 Connect",
            BrandSupport.LOCAL_CONTROL, "https://iotsyrpublicapi.z1.web.core.windows.net/",
        ),
        BrandEntry(
            "BWT", "Perla One, Perla Duplex, PerlaMAXX (firmware 2.02+, Local API enabled)",
            BrandSupport.LOCAL_MONITOR, "https://github.com/dkarv/ha-bwt-perla",
        ),
        BrandEntry(
            "EcoWater / iQua", "EcoWater, Rheem, Whirlpool, Kenmore and other iQua-app softeners",
            BrandSupport.CLOUD_ONLY, "https://github.com/barleybobs/homeassistant-ecowater-softener",
        ),
        BrandEntry(
            "Culligan", "Culligan Connect / Smart HE",
            BrandSupport.CLOUD_ONLY, "https://github.com/rewardone/homeassistant-culligan-water-softener",
        ),
        BrandEntry(
            "Pentair / Erie", "Erie IQSoft (Erie Connect)",
            BrandSupport.CLOUD_ONLY, "https://github.com/tgebarowski/erie-watertreatment-homeassistant",
        ),
        BrandEntry("RainSoft", "RainSoft Remind", BrandSupport.CLOUD_ONLY, "https://github.com/MathewUlanowski/ha-rainsoft"),
        BrandEntry(
            "SYR", "LEX Plus 10 Connect, LEX 1500 Connect and other models without the local API",
            BrandSupport.CLOUD_ONLY, "https://github.com/alexhass/syr_connect",
        ),
        BrandEntry(
            "Grünbeck", "softliQ:SD (myGrünbeck cloud)",
            BrandSupport.CLOUD_ONLY, "https://github.com/ironbiff/ha-gruenbeck-softliq",
        ),
        BrandEntry(
            "Chandler Systems / Culligan", "Legacy and CS Meter Soft valves (Bluetooth)",
            BrandSupport.BLUETOOTH, "https://github.com/toekneestuck/hass_chandler_systems",
        ),
        BrandEntry(
            "BWT", "AQA Perla (Bluetooth)",
            BrandSupport.BLUETOOTH, "https://community.home-assistant.io/t/bwt-aqa-perla-bluetooth-full-home-assistant-integration-via-ble-mqtt/1000014",
        ),
        BrandEntry(
            "Clack / Fleck", "WS1, 5600SXT and similar valves without connectivity",
            BrandSupport.DIY, "https://github.com/fonske/clack-reader-v3",
        ),
    )

    fun bySupport(): Map<BrandSupport, List<BrandEntry>> = entries.groupBy { it.support }
}
