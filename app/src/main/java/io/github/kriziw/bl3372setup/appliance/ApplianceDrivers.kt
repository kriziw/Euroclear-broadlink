package io.github.kriziw.bl3372setup.appliance

import io.github.kriziw.bl3372setup.appliance.bwt.BwtPerlaDriver
import io.github.kriziw.bl3372setup.appliance.gruenbeck.GruenbeckDriver
import io.github.kriziw.bl3372setup.appliance.judo.JudoDriver
import io.github.kriziw.bl3372setup.appliance.syr.SyrNeoSoftDriver
import io.github.kriziw.bl3372setup.network.LocalHttp

object ApplianceDrivers {
    fun create(brand: Brand, http: LocalHttp, address: ApplianceAddress): ApplianceDriver = when (brand) {
        Brand.JUDO -> JudoDriver(http, address)
        Brand.BWT_PERLA -> BwtPerlaDriver(http, address)
        Brand.GRUENBECK -> GruenbeckDriver(http, address)
        Brand.SYR_NEOSOFT -> SyrNeoSoftDriver(http, address)
    }
}
