package com.modernlghub.remote.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hub responses with missing, extra, null or wrongly typed fields must degrade gracefully.
 * JSON null is checked explicitly because Android's org.json turns it into the text "null"
 * for optString(), unlike the reference implementation these tests run against.
 */
class JsonToleranceTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/hub/$name")) { "missing fixture $name" }.readText()

    @Test
    fun `status with wrong types is coerced`() {
        val s = TvStatus.parse(fixture("status_wrong_types.json"))
        assertTrue(s.hasGeminiKey)
        assertFalse(s.screenOff)
        assertEquals("", s.currentAppId)
        assertEquals("", s.audio.soundOutput)
        assertEquals(17, s.audio.volume)
        assertTrue(s.audio.muted)
        assertEquals("", s.primaryMac)
        assertEquals("Active", s.power)
    }

    @Test
    fun `status with nulls everywhere parses to defaults`() {
        val s = TvStatus.parse(fixture("status_nulls.json"))
        assertEquals(TvStatus(), s)
        assertFalse(s.isStandby)
    }

    @Test
    fun `volume is clamped and unknown booleans fall back`() {
        val s = TvStatus.parse(fixture("status_out_of_range.json"))
        assertEquals(100, s.audio.volume)
        assertFalse(s.audio.muted)
        assertTrue(s.audio.isTvSpeaker)
    }

    @Test(expected = Exception::class)
    fun `html error page is not a status`() {
        TvStatus.parse(fixture("status_html.txt"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `error object without tv is not a status`() {
        TvStatus.parse(fixture("status_error_object.json"))
    }

    @Test
    fun `messy app list keeps only usable unique entries`() {
        val apps = LauncherApp.parseList(fixture("apps_messy.json"))
        assertEquals(listOf("netflix", "youtube.leanback.v4", "com.webos.app.hdmi1"), apps.map { it.id })
        val netflix = apps[0]
        assertEquals("netflix", netflix.title)
        assertEquals("app", netflix.type)
        assertFalse(netflix.visible)
        assertEquals(0xFFFFFFFF, netflix.brandColor)
        assertEquals(3.5, netflix.usage, 0.0)
        val youtube = apps[1]
        assertEquals("YouTube", youtube.title)
        assertNull("5-digit colour is rejected", youtube.brandColor)
        assertEquals(0.0, youtube.usage, 0.0)
        val pc = apps[2]
        assertEquals("com.webos.app.hdmi1", pc.title)
        assertTrue(pc.visible && pc.isInput)
    }

    @Test
    fun `app list without apps array is empty`() {
        assertTrue(LauncherApp.parseList("""{"count": 0}""").isEmpty())
        assertTrue(LauncherApp.parseList("""{"apps": null}""").isEmpty())
        assertTrue(LauncherApp.parseList("""{"apps": {"id": "x"}}""").isEmpty())
    }

    @Test
    fun `messy smart home list`() {
        val d = SmartDevice.parseList(fixture("smarthome_messy.json"))
        assertEquals(listOf("lamp-1", "plug-1"), d.map { it.id })
        val lamp = d[0]
        assertEquals("lamp-1", lamp.name)
        assertEquals("light", lamp.type)
        assertTrue(lamp.isOn)
        assertEquals(70, lamp.brightness)
        assertNull(lamp.color)
        assertEquals(2700, lamp.kelvin)
        assertEquals(setOf(SmartDevice.CAP_BRIGHTNESS, SmartDevice.CAP_WHITE), lamp.capabilities)
        assertTrue(lamp.online)
        assertEquals("", lamp.room)
        val plug = d[1]
        assertTrue(plug.isOn)
        assertFalse(plug.online)
        assertFalse(plug.adjustable)
    }

    @Test
    fun `smart home list wrapped in an object`() {
        val d = SmartDevice.parseList(fixture("smarthome_wrapped.json"))
        assertEquals(1, d.size)
        assertFalse(d[0].isOn)
    }

    @Test
    fun `assistant reply with wrong types`() {
        val r = AssistantReply.parse(fixture("command_wrong_types.json"))
        assertEquals("Okay, Netflix startet.", r.text)
        assertEquals(listOf("launch_app", "volume_up"), r.actions)
        assertEquals(listOf("Leiser"), r.suggestions)
        assertFalse(r.needsKey)
    }

    @Test
    fun `empty assistant reply says done`() {
        assertEquals("Erledigt.", AssistantReply.parse("""{"text": null, "actions": null}""").text)
    }

    @Test
    fun `error messages ignore null and non-json bodies`() {
        assertEquals("HTTP 500", HubApi.errorMessage("""{"error": null}""", 500))
        assertEquals("HTTP 404", HubApi.errorMessage("<html>nope</html>", 404))
        assertEquals("HTTP 400", HubApi.errorMessage("""["a"]""", 400))
        assertEquals("Leere Anfrage.", HubApi.errorMessage("""{"text": "Leere Anfrage.", "actions": []}""", 400))
        assertEquals("HTTP 502", HubApi.errorMessage("", 502))
    }

    @Test
    fun `json helpers`() {
        val o = JSONObject("""{"n": null, "s": "x", "i": "12", "d": 1.9, "b": "yes", "o": {}, "a": [], "nan": "NaN"}""")
        assertEquals("", o.str("n"))
        assertEquals("fb", o.str("missing", "fb"))
        assertEquals("", o.str("o"))
        assertEquals("", o.str("a"))
        assertEquals("1.9", o.str("d"))
        assertEquals(12, o.intOrNull("i"))
        assertEquals(1, o.intOrNull("d"))
        assertNull(o.intOrNull("n"))
        assertNull(o.intOrNull("s"))
        assertNull(o.doubleOrNull("nan"))
        assertTrue(o.bool("b"))
        assertTrue(o.bool("s", fallback = true))
        assertFalse(o.bool("n"))
        assertNull(parseObjectOrNull("[]"))
        assertNull(parseObjectOrNull(""))
    }
}
