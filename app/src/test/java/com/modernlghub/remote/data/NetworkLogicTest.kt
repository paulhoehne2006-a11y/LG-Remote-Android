package com.modernlghub.remote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4Test {

    @Test
    fun `parse and format`() {
        assertEquals(0xC0A8B22AL, Ipv4.parse("192.168.178.42"))
        assertEquals("192.168.178.42", Ipv4.format(0xC0A8B22AL))
        assertNull(Ipv4.parse("192.168.178"))
        assertNull(Ipv4.parse("192.168.178.256"))
        assertNull(Ipv4.parse("192.168.-1.1"))
        assertNull(Ipv4.parse("tv.local"))
        assertNull(Ipv4.parse("1.2.3.4.5"))
        assertNull(Ipv4.parse("fe80::1"))
    }

    @Test
    fun `broadcast for various prefixes`() {
        assertEquals("192.168.178.255", Ipv4.broadcastAddress("192.168.178.42", 24))
        assertEquals("10.0.15.255", Ipv4.broadcastAddress("10.0.12.7", 22))
        assertEquals("172.16.255.255", Ipv4.broadcastAddress("172.16.3.9", 16))
        assertEquals("192.168.1.7", Ipv4.broadcastAddress("192.168.1.7", 32))
        assertEquals("", Ipv4.broadcastAddress("192.168.1.7", 33))
    }

    @Test
    fun `same subnet`() {
        assertTrue(Ipv4.sameSubnet("192.168.178.42", "192.168.178.60", 24))
        assertFalse(Ipv4.sameSubnet("192.168.178.42", "192.168.179.101", 24))
        assertTrue(Ipv4.sameSubnet("10.0.12.7", "10.0.15.1", 22))
        assertFalse(Ipv4.sameSubnet("10.0.12.7", "tv.local", 22))
    }

    @Test
    fun `private ranges`() {
        listOf("10.1.2.3", "172.16.0.1", "172.31.255.1", "192.168.0.1").forEach { assertTrue(it, Ipv4.isPrivate(it)) }
        listOf("172.32.0.1", "100.64.0.1", "8.8.8.8", "192.169.0.1", "nonsense").forEach { assertFalse(it, Ipv4.isPrivate(it)) }
    }

    @Test
    fun `scan covers the own slash 24 without the phone itself`() {
        val hosts = Ipv4.scanCandidates(listOf(LanAddress("192.168.178.42", 24)))
        assertEquals(253, hosts.size)
        assertFalse("192.168.178.42" in hosts)
        assertEquals("192.168.178.1", hosts.first())
        assertEquals("192.168.178.254", hosts.last())
    }

    @Test
    fun `remembered host goes first`() {
        val hosts = Ipv4.scanCandidates(listOf(LanAddress("192.168.178.42", 24)), preferred = "192.168.178.60")
        assertEquals("192.168.178.60", hosts.first())
        assertEquals(1, hosts.count { it == "192.168.178.60" })
        val notLocal = Ipv4.scanCandidates(listOf(LanAddress("192.168.178.42", 24)), preferred = "10.0.0.5")
        assertEquals("192.168.178.1", notLocal.first())
    }

    @Test
    fun `two interfaces are both scanned, duplicates once`() {
        val hosts = Ipv4.scanCandidates(
            listOf(LanAddress("192.168.178.42", 24), LanAddress("192.168.178.43", 24), LanAddress("10.0.0.2", 16))
        )
        assertEquals(252 + 253, hosts.size)
        assertTrue("10.0.0.1" in hosts)
        assertFalse("192.168.178.43" in hosts)
    }

    @Test
    fun `no wifi, public or carrier networks mean nothing to scan`() {
        assertTrue(Ipv4.scanCandidates(emptyList()).isEmpty())
        assertTrue(Ipv4.scanCandidates(listOf(LanAddress("100.70.1.2", 10))).isEmpty())
        assertTrue(Ipv4.scanCandidates(listOf(LanAddress("85.10.1.2", 24))).isEmpty())
        // A VPN tunnel address with a /32 is not a LAN to scan.
        assertTrue(Ipv4.scanCandidates(listOf(LanAddress("10.8.0.2", 32))).isEmpty())
    }
}

class WakeOnLanTargetsTest {

    @Test
    fun `directed broadcasts first, then limited broadcast and unicast`() {
        val t = WakeOnLan.targets(listOf(LanAddress("192.168.178.42", 24), LanAddress("10.0.12.7", 22)), "192.168.178.60")
        assertEquals(listOf("192.168.178.255", "10.0.15.255", "255.255.255.255", "192.168.178.60"), t)
    }

    @Test
    fun `without wifi only the limited broadcast and a literal tv address remain`() {
        assertEquals(listOf("255.255.255.255"), WakeOnLan.targets(emptyList(), "tv.local"))
        assertEquals(listOf("255.255.255.255"), WakeOnLan.targets(emptyList(), null))
        assertEquals(listOf("255.255.255.255", "192.168.1.9"), WakeOnLan.targets(emptyList(), "192.168.1.9"))
    }

    @Test
    fun `placeholder macs and odd separators`() {
        assertNull(WakeOnLan.magicPacket("00:00:00:00:00:00"))
        assertNull(WakeOnLan.magicPacket("ff:ff:ff:ff:ff:ff"))
        assertTrue(WakeOnLan.magicPacket(" A823.FE01.0203 ") != null)
        assertNull(WakeOnLan.magicPacket("a8:23:fe:01:02:03:04"))
    }

    @Test
    fun `mac normalisation for storage`() {
        assertEquals("a8:23:fe:01:02:03", TvPreferences.normalizeMac("A8-23-FE-01-02-03"))
        assertEquals("a8:23:fe:01:02:03", TvPreferences.normalizeMac("a823fe010203"))
        assertNull(TvPreferences.normalizeMac("a8:23"))
        assertNull(TvPreferences.normalizeMac("zz:23:fe:01:02:03"))
    }
}

class PollPolicyTest {

    @Test
    fun `online uses the shortest interval`() {
        assertEquals(3_000, PollPolicy.nextDelay(listOf(PollPolicy.UI, PollPolicy.BACKGROUND), online = true, consecutiveFailures = 0))
        assertEquals(5_000, PollPolicy.nextDelay(listOf(PollPolicy.BACKGROUND), online = true, consecutiveFailures = 3))
    }

    @Test
    fun `offline backs off exponentially up to the client's cap`() {
        val bg = listOf(PollPolicy.BACKGROUND)
        val delays = (1..8).map { PollPolicy.nextDelay(bg, online = false, consecutiveFailures = it) }
        assertEquals(listOf(10_000L, 20_000L, 40_000L, 60_000L, 60_000L, 60_000L, 60_000L, 60_000L), delays)
        // Visible UI never waits more than 10 s for a TV that was just switched on.
        assertEquals(10_000, PollPolicy.nextDelay(listOf(PollPolicy.UI, PollPolicy.BACKGROUND), online = false, consecutiveFailures = 9))
    }

    @Test
    fun `huge failure counts do not overflow`() {
        assertEquals(60_000, PollPolicy.nextDelay(listOf(PollPolicy.BACKGROUND), online = false, consecutiveFailures = Int.MAX_VALUE))
    }

    @Test
    fun `degenerate input stays sane`() {
        assertEquals(PollPolicy.UI.intervalMs, PollPolicy.nextDelay(emptyList(), online = false, consecutiveFailures = 3))
        assertEquals(500, PollPolicy.nextDelay(listOf(PollClient(0, 0)), online = true, consecutiveFailures = 0))
        assertEquals(500, PollPolicy.nextDelay(listOf(PollClient(0, 0)), online = false, consecutiveFailures = 2))
    }
}

class HubUrlTest {

    @Test
    fun `base urls`() {
        assertEquals("http://192.168.178.60:8080", HubApi.normalizeBaseUrl("192.168.178.60"))
        assertEquals("http://192.168.178.60:9000", HubApi.normalizeBaseUrl(" 192.168.178.60:9000/ "))
        assertEquals("http://192.168.178.60:8080", HubApi.normalizeBaseUrl("http://192.168.178.60:8080/api/status"))
        assertEquals("https://tv.local:8080", HubApi.normalizeBaseUrl("HTTPS://tv.local"))
        assertEquals("http://[fe80::1]:8080", HubApi.normalizeBaseUrl("[fe80::1]"))
        assertEquals("http://[fe80::1]:9000", HubApi.normalizeBaseUrl("[fe80::1]:9000"))
        assertEquals("", HubApi.normalizeBaseUrl("ftp://tv"))
        assertEquals("", HubApi.normalizeBaseUrl("http://"))
    }

    @Test
    fun `host of base url`() {
        assertEquals("192.168.178.60", HubApi.hostOf("http://192.168.178.60:8080"))
        assertEquals("fe80::1", HubApi.hostOf("http://[fe80::1]:8080"))
        assertEquals("", HubApi.hostOf(""))
    }

    @Test
    fun `external action keys are validated`() {
        assertEquals(TvAction.POWER, TvAction.fromExternal("power"))
        assertEquals(TvAction.SCREEN, TvAction.fromExternal("screen_off"))
        assertNull(TvAction.fromExternal(null))
        assertNull(TvAction.fromExternal(42))
        assertNull(TvAction.fromExternal(""))
        assertNull(TvAction.fromExternal("POWER"))
        assertNull(TvAction.fromExternal("power;rm"))
        assertNull(TvAction.fromExternal("p".repeat(100)))
        assertNull(TvAction.fromExternal("unknown_action"))
    }
}
