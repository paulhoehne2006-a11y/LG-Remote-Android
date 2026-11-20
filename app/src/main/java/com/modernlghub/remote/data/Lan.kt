package com.modernlghub.remote.data

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** An IPv4 address of one of the phone's local networks, e.g. 192.168.178.42/24. */
data class LanAddress(val ip: String, val prefixLength: Int) {
    val broadcast: String get() = Ipv4.broadcastAddress(ip, prefixLength)
    fun contains(host: String): Boolean = Ipv4.sameSubnet(ip, host, prefixLength)
}

/** IPv4 arithmetic for discovery and Wake-on-LAN. Pure functions, unit-tested. */
object Ipv4 {
    fun parse(ip: String): Long? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        var acc = 0L
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            val b = p.toInt()
            if (b > 255) return null
            acc = (acc shl 8) or b.toLong()
        }
        return acc
    }

    fun format(addr: Long): String = (3 downTo 0).joinToString(".") { ((addr shr (it * 8)) and 0xFF).toString() }

    private fun mask(prefixLength: Int): Long =
        if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL

    fun broadcastAddress(ip: String, prefixLength: Int): String {
        val addr = parse(ip) ?: return ""
        if (prefixLength !in 0..32) return ""
        val m = mask(prefixLength)
        return format((addr and m) or (m.inv() and 0xFFFFFFFFL))
    }

    fun sameSubnet(a: String, b: String, prefixLength: Int): Boolean {
        val x = parse(a) ?: return false
        val y = parse(b) ?: return false
        if (prefixLength !in 0..32) return false
        val m = mask(prefixLength)
        return (x and m) == (y and m)
    }

    /** RFC 1918 private ranges (where a home hub can live). */
    fun isPrivate(ip: String): Boolean {
        val a = parse(ip) ?: return false
        val b1 = (a shr 24) and 0xFF
        val b2 = (a shr 16) and 0xFF
        return b1 == 10L || (b1 == 172L && b2 in 16L..31L) || (b1 == 192L && b2 == 168L)
    }

    /**
     * Hosts to probe for the hub: the /24 around each local address (a larger subnet is not scanned
     * in full – 254 probes is what a phone can do in a few seconds), own addresses excluded,
     * [preferred] first if it is one of them. Non-private networks (carrier-grade NAT, public
     * Wi-Fi with public addresses) are skipped.
     */
    fun scanCandidates(local: List<LanAddress>, preferred: String? = null): List<String> {
        val own = local.mapTo(HashSet()) { it.ip }
        val hosts = LinkedHashSet<String>()
        for (lan in local) {
            if (!isPrivate(lan.ip) || lan.prefixLength !in 8..30) continue
            val prefix = lan.ip.substringBeforeLast('.')
            for (i in 1..254) {
                val h = "$prefix.$i"
                if (h !in own) hosts += h
            }
        }
        val first = preferred?.takeIf { it in hosts }
        return if (first == null) hosts.toList() else listOf(first) + (hosts - first)
    }
}

/** Builds and sends Wake-on-LAN magic packets. Pure functions are unit-tested. */
object WakeOnLan {
    fun magicPacket(mac: String): ByteArray? {
        val hex = mac.trim().filter { it.isLetterOrDigit() }
        if (hex.length != 12 || !hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        val macBytes = ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        // All-zero or broadcast MACs are placeholders, not a TV.
        if (macBytes.all { it == 0.toByte() } || macBytes.all { it == 0xFF.toByte() }) return null
        return ByteArray(6 + 16 * 6).also { p ->
            for (i in 0 until 6) p[i] = 0xFF.toByte()
            for (r in 0 until 16) System.arraycopy(macBytes, 0, p, 6 + r * 6, 6)
        }
    }

    fun broadcastAddress(ip: String, prefixLength: Int): String = Ipv4.broadcastAddress(ip, prefixLength)

    /**
     * Where to send the packet: each local network's directed broadcast (most reliable – routers
     * and some Android builds drop 255.255.255.255), the limited broadcast, and the TV's last
     * known address as unicast (works while the TV's ARP entry is still cached).
     */
    fun targets(local: List<LanAddress>, tvHost: String?): List<String> = buildList {
        local.forEach { lan -> lan.broadcast.takeIf { it.isNotBlank() }?.let(::add) }
        add("255.255.255.255")
        tvHost?.takeIf { Ipv4.parse(it) != null }?.let(::add)
    }.distinct()

    val PORTS = intArrayOf(9, 7)

    /**
     * Sends [packet] to every host on ports 9 and 7. [prepare] can bind the socket to a network.
     * Returns true if at least one datagram left the phone.
     */
    fun send(packet: ByteArray, hosts: List<String>, prepare: (DatagramSocket) -> Unit = {}): Boolean {
        var anySent = false
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                runCatching { prepare(socket) }
                for (host in hosts) {
                    runCatching {
                        val addr = InetAddress.getByName(host)
                        for (port in PORTS) {
                            socket.send(DatagramPacket(packet, packet.size, addr, port))
                            anySent = true
                        }
                    }
                }
            }
        }
        return anySent
    }
}
