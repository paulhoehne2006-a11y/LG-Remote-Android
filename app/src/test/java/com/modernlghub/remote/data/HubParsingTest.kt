package com.modernlghub.remote.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Payloads mirror what Modern-LG-Hub's server.py / tv.py actually return. */
class HubParsingTest {

    private val statusJson = """
        {"has_key": true, "tv": {"power": "Active", "screen_off": false, "current_app": "netflix",
          "audio": {"soundOutput": "external_optical", "volume": 23, "muted": false,
                    "is_denon_optical": true, "is_tv_speaker": false, "display_title": "Denon AVR-1803 (Optisch)"},
          "network": {"mac_addresses": {"eth0": "a8:23:fe:01:02:03"}, "primary_mac": "a8:23:fe:01:02:03"},
          "success": true}}
    """.trimIndent()

    @Test
    fun `status is parsed`() {
        val s = TvStatus.parse(statusJson)
        assertEquals("netflix", s.currentAppId)
        assertEquals(23, s.audio.volume)
        assertTrue(s.audio.isOptical)
        assertEquals("Denon AVR", s.audio.outputLabel)
        assertEquals("a8:23:fe:01:02:03", s.primaryMac)
        assertTrue(s.hasGeminiKey)
        assertFalse(s.isStandby)
    }

    @Test
    fun `unknown app and screen-off are handled`() {
        val s = TvStatus.parse(
            """{"has_key": false, "tv": {"power": "Screen Off", "screen_off": true, "current_app": "Unknown",
               "audio": {"soundOutput": "tv_speaker"}, "network": {}}}"""
        )
        assertEquals("", s.currentAppId)
        assertTrue(s.screenOff)
        assertFalse("screen off is not standby", s.isStandby)
        assertTrue(s.audio.isTvSpeaker)
        assertEquals("TV-Lautsprecher", s.audio.outputLabel)
    }

    @Test
    fun `active standby is detected`() {
        val s = TvStatus.parse("""{"tv": {"power": "Active Standby", "screen_off": false}}""")
        assertTrue(s.isStandby)
    }

    @Test
    fun `apps keep visibility and brand colour`() {
        val apps = LauncherApp.parseList(
            """{"apps": [
                {"id": "com.webos.app.hdmi1", "title": "PC (HDMI 1)", "type": "input", "alias": "pc"},
                {"id": "netflix", "title": "Netflix", "type": "native", "visible": true, "iconColor": "#ffffff", "usage": 12.5},
                {"id": "com.palm.app.settings", "title": "Einstellungen", "visible": false, "iconColor": null},
                {"id": "", "title": "broken"}
            ], "count": 4}"""
        )
        assertEquals(3, apps.size)
        assertTrue(apps[0].isInput)
        assertEquals(0xFFFFFFFF, apps[1].brandColor)
        assertEquals(12.5, apps[1].usage, 0.0)
        assertFalse(apps[2].visible)
        assertNull(apps[2].brandColor)
    }

    @Test
    fun `assistant reply uses the hub's text field`() {
        val r = AssistantReply.parse(
            """{"text": "Bildschirm ist aus.", "actions": [{"tool": "screen_off", "args": {}, "result": {"success": true}}]}"""
        )
        assertEquals("Bildschirm ist aus.", r.text)
        assertEquals(listOf("screen_off"), r.actions)
        assertFalse(r.isError)
    }

    @Test
    fun `smart devices are parsed`() {
        val d = SmartDevice.parseList(
            """[{"id": "abc", "name": "TV-Licht", "raw_name": "TV-Leiste", "type": "light", "state": "on"},
                {"id": "def", "name": "Bett", "type": "plug", "state": "off"}]"""
        )
        assertEquals(2, d.size)
        assertTrue(d[0].isOn)
        assertEquals("plug", d[1].type)
    }

    @Test
    fun `base url normalisation`() {
        assertEquals("http://192.168.178.60:8080", HubApi.normalizeBaseUrl("192.168.178.60"))
        assertEquals("http://192.168.178.60:9000", HubApi.normalizeBaseUrl(" 192.168.178.60:9000/ "))
        assertEquals("http://tv.local:8080", HubApi.normalizeBaseUrl("http://tv.local"))
        assertEquals("", HubApi.normalizeBaseUrl("   "))
    }

    @Test
    fun `legacy widget and shortcut keys still resolve`() {
        assertEquals(TvAction.SCREEN, TvAction.fromKey("screen_off"))
        assertEquals(TvAction.SCREEN, TvAction.fromKey("screen_toggle"))
        assertEquals(TvAction.POWER, TvAction.fromKey("power_on"))
        assertEquals(TvAction.REMOTE, TvAction.fromKey("overlay"))
        assertEquals(TvAction.AUDIO, TvAction.fromKey("audio_toggle"))
        assertNull(TvAction.fromKey("does_not_exist"))
    }

    @Test
    fun `app titles fall back sensibly`() {
        val state = TvState(apps = listOf(LauncherApp("youtube.leanback.v4", "YouTube")))
        assertEquals("YouTube", state.appTitle("youtube.leanback.v4"))
        assertEquals("PC (HDMI 1)", state.appTitle("com.webos.app.hdmi1"))
        assertEquals("Prime Video", state.appTitle("amazon"))
        assertEquals("Mediathek", state.appTitle("ard.mediathek"))
    }
}

class WakeOnLanTest {

    @Test
    fun `magic packet layout`() {
        val p = WakeOnLan.magicPacket("A8:23:FE:01:02:03")!!
        assertEquals(102, p.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, p.copyOfRange(0, 6))
        val mac = byteArrayOf(0xA8.toByte(), 0x23, 0xFE.toByte(), 0x01, 0x02, 0x03)
        for (i in 0 until 16) assertArrayEquals(mac, p.copyOfRange(6 + i * 6, 12 + i * 6))
    }

    @Test
    fun `invalid mac is rejected`() {
        assertNull(WakeOnLan.magicPacket(""))
        assertNull(WakeOnLan.magicPacket("zz:23:fe:01:02:03"))
        assertNull(WakeOnLan.magicPacket("a8:23:fe:01:02"))
        assertTrue(WakeOnLan.magicPacket("a8-23-fe-01-02-03") != null)
    }

    @Test
    fun `broadcast address from prefix`() {
        assertEquals("192.168.178.255", WakeOnLan.broadcastAddress("192.168.178.42", 24))
        assertEquals("10.0.15.255", WakeOnLan.broadcastAddress("10.0.12.7", 22))
        assertEquals("", WakeOnLan.broadcastAddress("nonsense", 24))
    }
}
