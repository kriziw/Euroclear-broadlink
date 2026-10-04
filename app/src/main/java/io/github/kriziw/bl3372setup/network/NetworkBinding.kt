package io.github.kriziw.bl3372setup.network

import android.content.Context
import android.net.Network
import android.net.wifi.WifiManager
import io.github.kriziw.bl3372setup.broadlink.SocketFactory
import java.io.IOException
import java.net.DatagramSocket

/**
 * Sockets bound to one specific [Network] with [Network.bindSocket].
 *
 * When the phone is on a Wi-Fi network without Internet (such as the WiFi-BL3372 AP), Android
 * keeps cellular data as the default network. An unbound socket would send even the
 * 255.255.255.255 broadcast out of the default (cellular) interface. Binding only this socket is
 * narrower than `ConnectivityManager.bindProcessToNetwork`, which would re-route all app traffic.
 */
class NetworkBoundSockets(private val network: Network) : SocketFactory {
    override fun open(): DatagramSocket {
        val socket = DatagramSocket()
        try {
            network.bindSocket(socket)
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        return socket
    }
}

/**
 * Holds a [WifiManager.MulticastLock] while [block] runs, so the Wi-Fi driver's packet filter
 * does not drop broadcast replies.
 */
suspend fun <T> Context.withMulticastLock(block: suspend () -> T): T {
    val wifi = applicationContext.getSystemService(WifiManager::class.java)
    val lock = wifi.createMulticastLock("bl3372-setup").apply {
        setReferenceCounted(false)
        acquire()
    }
    try {
        return block()
    } finally {
        if (lock.isHeld) lock.release()
    }
}
