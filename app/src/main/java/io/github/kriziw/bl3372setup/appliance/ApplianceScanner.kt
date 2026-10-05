package io.github.kriziw.bl3372setup.appliance

import io.github.kriziw.bl3372setup.network.TcpConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Finds appliances without discovery support: a quick TCP probe of the brand's port across a
 * subnet, in paced batches. Callers then confirm each open host with the brand's own API.
 */
object ApplianceScanner {
    suspend fun openHosts(
        connector: TcpConnector,
        hosts: List<String>,
        port: Int,
        timeoutMillis: Int = 400,
        parallel: Int = 32,
        onProgress: (done: Int) -> Unit = {},
    ): List<String> = withContext(Dispatchers.IO) {
        var done = 0
        hosts.chunked(parallel).flatMap { batch ->
            coroutineScope {
                batch.map { host ->
                    async {
                        try {
                            connector.connect(host, port, timeoutMillis).close()
                            host
                        } catch (_: IOException) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }.also {
                done += batch.size
                onProgress(done)
            }
        }
    }
}
