package com.modernlghub.remote.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory

/**
 * Tracks the phone's local networks (Wi-Fi and Ethernet), independent of what the *default*
 * network is. With a VPN active, `activeNetwork` is the VPN, whose address and routes say nothing
 * about the home LAN – Wake-on-LAN, discovery and the hub connection use the underlying Wi-Fi.
 *
 * Wi-Fi without internet access counts as well (a router without uplink still reaches the TV).
 */
class LanMonitor(context: Context) {

    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val networks = ConcurrentHashMap<Network, LinkProperties>()
    private val known = ConcurrentHashMap.newKeySet<Network>()

    private val _available = MutableStateFlow(false)

    /** True while at least one Wi-Fi/Ethernet network is connected. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    @Volatile private var settled = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            known += network
            update()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            known += network
            networks[network] = linkProperties
            update()
        }

        override fun onLost(network: Network) {
            known -= network
            networks.remove(network)
            update()
        }
    }

    init {
        // Callbacks for networks that are already up arrive right after registration; until then
        // fall back to what the default network says.
        _available.value = runCatching {
            cm?.getNetworkCapabilities(cm.activeNetwork)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } ?: false
        }.getOrDefault(false)
        val registered = runCatching {
            cm?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        }.isSuccess
        // Without a callback (should not happen) don't block anything on the LAN state.
        if (!registered || cm == null) _available.value = true
    }

    private fun update() {
        settled = true
        _available.value = known.isNotEmpty()
    }

    /** IPv4 addresses of the local networks (empty if none or not yet known). */
    fun addresses(): List<LanAddress> = addressesByNetwork().values.flatten().distinct()

    /** IPv4 addresses per local network; networks without IPv4 are left out. */
    fun addressesByNetwork(): Map<Network, List<LanAddress>> =
        networks.mapValues { (_, lp) -> ipv4Of(lp) }.filterValues { it.isNotEmpty() }

    private fun ipv4Of(lp: LinkProperties): List<LanAddress> =
        lp.linkAddresses.mapNotNull { la ->
            val a = la.address
            if (a is Inet4Address && !a.isLoopbackAddress && !a.isLinkLocalAddress) {
                a.hostAddress?.let { LanAddress(it, la.prefixLength) }
            } else {
                null
            }
        }

    /** The local network whose subnet contains [ipv4], if any. */
    fun networkFor(ipv4: String): Network? =
        networks.entries.firstOrNull { (_, lp) ->
            lp.linkAddresses.any { la ->
                val a = la.address
                a is Inet4Address && a.hostAddress?.let { Ipv4.sameSubnet(it, ipv4, la.prefixLength) } == true
            }
        }?.key

    /** True when the system's default network is a VPN (then LAN sockets must be bound explicitly). */
    fun vpnActive(): Boolean = runCatching {
        cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    /**
     * Socket factory that routes to [host] over the local network, but only while a VPN is the
     * default network and [host] is an IPv4 address inside a local subnet. Otherwise null: normal
     * routing already goes over Wi-Fi, and hosts outside the LAN (e.g. reached through the VPN on
     * purpose) must not be forced onto it.
     */
    fun socketFactoryFor(host: String): SocketFactory? {
        if (Ipv4.parse(host) == null || !vpnActive()) return null
        return networkFor(host)?.socketFactory
    }

    /** Waits briefly for the first network callbacks in a freshly started process. */
    suspend fun awaitSettled(timeoutMs: Long = 600): Boolean {
        if (!settled) withTimeoutOrNull(timeoutMs) { available.first { it } }
        return available.value
    }
}
