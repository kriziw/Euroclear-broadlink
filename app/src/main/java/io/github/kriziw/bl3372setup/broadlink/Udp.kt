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
    /** Smallest subnet the app will sweep with unicast hellos (/22 = 1022 hosts). */
    const val MIN_SCAN_PREFIX = 22

    private val DOTTED_QUAD = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** Parses a dotted-quad IPv4 literal without any DNS lookup, or returns null. */
    fun parse(text: String): Inet4Address? {
        val parts = DOTTED_QUAD.matchEntire(text.trim())?.groupValues?.drop(1)?.map { it.toInt() } ?: return null
        if (parts.any { it > 255 }) return null
        return InetAddress.getByAddress(ByteArray(4) { parts[it].toByte() }) as Inet4Address
    }

    /** A subnet given as `a.b.c.d/nn` (any host address inside it is accepted). */
    data class Subnet(val network: Inet4Address, val prefixLength: Int) {
        /** Host addresses, excluding the network and broadcast addresses. */
        fun hosts(): List<Inet4Address> {
            val base = toInt(network)
            val size = 1L shl (32 - prefixLength)
            return (1 until size - 1).map { fromInt(base + it.toInt()) }
        }

        override fun toString() = "${network.hostAddress}/$prefixLength"
    }

    /** Parses `192.168.20.0/24`; null when malformed or larger than [MIN_SCAN_PREFIX] allows. */
    fun parseSubnet(text: String): Subnet? {
        val (ip, prefix) = text.trim().split('/').takeIf { it.size == 2 } ?: return null
        val address = parse(ip) ?: return null
        val length = prefix.toIntOrNull()?.takeIf { it in MIN_SCAN_PREFIX..30 } ?: return null
        return Subnet(fromInt(toInt(address) and mask(length)), length)
    }

    /** The /24 around [address]: where a device on another VLAN is most likely to reappear. */
    fun surrounding24(address: Inet4Address) = Subnet(fromInt(toInt(address) and mask(24)), 24)

    fun sameSubnet(a: Inet4Address, b: Inet4Address, prefixLength: Int): Boolean =
        prefixLength in 0..32 && (toInt(a) and mask(prefixLength)) == (toInt(b) and mask(prefixLength))

    private fun mask(prefixLength: Int): Int = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)

    private fun toInt(address: Inet4Address): Int =
        address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }

    private fun fromInt(value: Int): Inet4Address =
        InetAddress.getByAddress(ByteArray(4) { i -> (value shr (24 - 8 * i)).toByte() }) as Inet4Address

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
