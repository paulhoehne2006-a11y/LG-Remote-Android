package com.modernlghub.remote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures in src/test/resources/hub/ were recorded from the real Modern-LG-Hub server.py
 * (merge 03c2b08, "hardened backend") running against a simulated luna-send.
 */
class HubCompatibilityTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/hub/$name")) { "missing fixture $name" }.readText()

    @Test
    fun `recorded status parses`() {
        val s = TvStatus.parse(fixture("status.json"))
        assertEquals("netflix", s.currentAppId)
        assertEquals(23, s.audio.volume)
        assertTrue(s.audio.isOptical)
        assertFalse(s.hasGeminiKey)
        assertTrue(s.primaryMac.isNotBlank())
    }

    @Test
    fun `recorded app list parses with the PC input first`() {
        val apps = LauncherApp.parseList(fixture("apps.json"))
        assertTrue(apps.size > 20)
        assertTrue(apps.first().isInput)
        assertEquals("PC (HDMI 1)", apps.first().title)
        assertTrue(apps.any { it.id == "netflix" && it.visible })
        assertTrue("hidden system apps are flagged", apps.any { !it.visible })
    }

    @Test
    fun `error bodies become readable messages`() {
        // 400 for invalid input carries a text …
        assertEquals("Unbekannte Aktion: nope", HubApi.errorMessage(fixture("quick_unknown.json"), 400))
        // … while a TV refusal (502) only says success=false.
        assertEquals("HTTP 502", HubApi.errorMessage(fixture("quick_screen_off_refused.json"), 502))
    }

    @Test
    fun `hub with token is still found by discovery`() {
        val body = fixture("api_unauthorized.json")
        assertTrue(TvRepository.isHubAnswer(HubResult.Rejected(401, HubApi.errorMessage(body, 401))))
        assertFalse(TvRepository.isHubAnswer(HubResult.Rejected(401, "Unauthorized")))
        assertFalse(TvRepository.isHubAnswer(HubResult.Rejected(404, "Not found")))
        assertTrue(TvRepository.isHubAnswer(HubResult.Ok("""{"service": "Modern LG Hub Backend", "version": "2.1.0"}""")))
        assertFalse(TvRepository.isHubAnswer(HubResult.Ok("""{"service": "something else"}""")))
        assertFalse(TvRepository.isHubAnswer(HubResult.Unreachable))
    }

    @Test
    fun `missing gemini key is detected`() {
        val r = AssistantReply.parse(fixture("command_no_key.json"))
        assertTrue(r.needsKey)
        assertTrue(r.text.startsWith("Kein Gemini API-Key"))
    }

    @Test
    fun `assistant follow-up suggestions are read`() {
        val r = AssistantReply.parse(
            """{"text": "Netflix läuft.", "actions": [{"tool": "launch_app", "args": {}, "result": {}}],
               "suggestions": ["Licht dimmen", " ", "Lauter"], "prompt": "Netflix"}"""
        )
        assertEquals(listOf("Licht dimmen", "Lauter"), r.suggestions)
        assertEquals(listOf("launch_app"), r.actions)
        assertFalse(r.needsKey)
    }

    @Test
    fun `history uses the hub's role names`() {
        val json = AssistantTurn.toJson(
            listOf(AssistantTurn(true, "Bild aus"), AssistantTurn(false, "Erledigt."), AssistantTurn(true, " "))
        )
        assertEquals(2, json.length())
        assertEquals("user", json.getJSONObject(0).getString("role"))
        assertEquals("model", json.getJSONObject(1).getString("role"))
        assertEquals("Erledigt.", json.getJSONObject(1).getString("text"))
    }

    @Test
    fun `offline smartthings devices are marked`() {
        val d = SmartDevice.parseList(
            """[{"id": "a-1", "name": "Bett · Schlafzimmer", "raw_name": "Bett", "type": "plug", "state": "off", "online": false},
                {"id": "b-2", "name": "TV-Licht", "raw_name": "TV-Leiste", "type": "light", "state": "on", "online": true},
                {"id": "c-3", "name": "Alt", "type": "light", "state": "on"}]"""
        )
        assertFalse(d[0].online)
        assertTrue(d[1].online && d[1].isOn)
        assertTrue("older hubs without the field count as online", d[2].online)
    }
}
