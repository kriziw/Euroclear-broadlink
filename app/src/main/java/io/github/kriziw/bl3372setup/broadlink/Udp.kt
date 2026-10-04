package io.github.kriziw.bl3372setup.broadlink

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress

/** Opens a UDP socket that is already bound to the right network. Owned (closed) by the caller. */
fun interface SocketFactory {
    fun open(): DatagramSocket
}

internal val LIMITED_BROADCAST: InetAddress =
    InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))

/**
 * Sends [payload] to every destination. Individual failures are tolerated (for example a subnet
 * broadcast that the kernel rejects) as long as at least one send succeeds.
 *
 * @throws IOException the first error, if no destination could be reached.
 */
internal fun DatagramSocket.sendToAll(payload: ByteArray, destinations: List<InetSocketAddress>) {
    var firstError: IOException? = null
    var delivered = 0
    for (destination in destinations) {
        try {
            send(DatagramPacket(payload, payload.size, destination))
            delivered++
        } catch (e: IOException) {
            if (firstError == null) firstError = e
        }
    }
    if (delivered == 0) throw firstError ?: IOException("No destinations")
}

object Ipv4 {
    /** Directed broadcast address of [address]/[prefixLength], or null for /31 and /32. */
    fun broadcastAddress(address: Inet4Address, prefixLength: Int): Inet4Address? {
        if (prefixLength !in 0..30) return null
        val ip = address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        val hostMask = if (prefixLength == 0) -1 else (1 shl (32 - prefixLength)) - 1
        val broadcast = ip or hostMask
        val bytes = ByteArray(4) { i -> (broadcast shr (24 - 8 * i)).toByte() }
        return InetAddress.getByAddress(bytes) as Inet4Address
    }

    /**
     * Destinations for a BroadLink packet on one network: the limited broadcast that
     * python-broadlink uses, the subnet broadcast its README recommends as a fallback and,
     * optionally, a unicast address (the SoftAP's gateway, i.e. the module itself).
     */
    fun destinations(
        subnetBroadcast: InetAddress?,
        unicast: InetAddress? = null,
        port: Int = BroadlinkPackets.PORT,
    ): List<InetSocketAddress> =
        listOfNotNull(LIMITED_BROADCAST, subnetBroadcast, unicast)
            .distinct()
            .map { InetSocketAddress(it, port) }
}
