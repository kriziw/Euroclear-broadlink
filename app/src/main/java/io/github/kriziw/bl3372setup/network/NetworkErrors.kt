package io.github.kriziw.bl3372setup.network

import io.github.kriziw.bl3372setup.broadlink.BroadlinkException
import java.io.IOException
import java.net.SocketTimeoutException

/** User-facing categories for socket and device failures. */
sealed interface NetworkError {
    /** EPERM/EACCES: Android 17 local-network permission missing, or a VPN blocking LAN access. */
    data object LocalNetworkBlocked : NetworkError
    data object NotConnected : NetworkError
    data object Timeout : NetworkError
    /** The module refused the default BroadLink login (e.g. after pairing with the vendor cloud). */
    data object AuthRejected : NetworkError
    /** An appliance's local API refused the stored user name, password or login code. */
    data object LoginRejected : NetworkError
    data class Other(val detail: String) : NetworkError

    companion object {
        fun of(e: IOException): NetworkError {
            if (e is BroadlinkException && e.code == BroadlinkException.AUTH_FAILED) return AuthRejected
            if (e is LoginRejectedException) return LoginRejected
            if (e is SocketTimeoutException) return Timeout
            val text = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
            return when {
                listOf("EPERM", "EACCES", "Operation not permitted", "Permission denied").any { it in text } -> LocalNetworkBlocked
                listOf("ENONET", "ENETUNREACH", "not on the network", "unreachable").any { it in text } -> NotConnected
                else -> Other("${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            }
        }
    }
}
