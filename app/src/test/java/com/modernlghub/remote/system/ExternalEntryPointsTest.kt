package com.modernlghub.remote.system

import com.modernlghub.remote.data.TvAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exported shortcut activity is attack surface: only well-formed, known actions pass. */
class ExternalEntryPointsTest {

    @After
    fun tearDown() = Debounce.reset()

    @Test
    fun `shortcut intents from the launcher and routines resolve`() {
        val action = ShortcutActionActivity.ACTION_SHORTCUT
        assertEquals(TvAction.SCREEN, ShortcutActionActivity.actionOf(action, "screen_off"))
        assertEquals(TvAction.PC, ShortcutActionActivity.actionOf(action, "pc"))
        assertEquals(TvAction.AUDIO, ShortcutActionActivity.actionOf(action, "audio_toggle"))
        assertEquals(TvAction.REMOTE, ShortcutActionActivity.actionOf(action, "overlay"))
        // Explicit component launch without an action (some automation apps).
        assertEquals(TvAction.POWER, ShortcutActionActivity.actionOf(null, "power"))
    }

    @Test
    fun `malformed or foreign shortcut intents are ignored`() {
        val action = ShortcutActionActivity.ACTION_SHORTCUT
        assertNull(ShortcutActionActivity.actionOf("android.intent.action.MAIN", "power"))
        assertNull(ShortcutActionActivity.actionOf("com.evil.ACTION", "power"))
        assertNull(ShortcutActionActivity.actionOf(action, null))
        assertNull(ShortcutActionActivity.actionOf(action, 1))
        assertNull(ShortcutActionActivity.actionOf(action, arrayOf("power")))
        assertNull(ShortcutActionActivity.actionOf(action, "Power"))
        assertNull(ShortcutActionActivity.actionOf(action, "power\n"))
        assertNull(ShortcutActionActivity.actionOf(action, "x".repeat(10_000)))
    }

    @Test
    fun `routines can switch on and off explicitly`() {
        val action = ShortcutActionActivity.ACTION_SHORTCUT
        assertEquals(TvAction.POWER, ShortcutActionActivity.actionOf(action, "power_on"))
        assertEquals(TvAction.POWER, ShortcutActionActivity.actionOf(action, "power_off"))
        assertEquals(true, TvAction.explicitPower("power_on"))
        assertEquals(false, TvAction.explicitPower("power_off"))
        assertNull("plain power stays a toggle", TvAction.explicitPower("power"))
        assertNull(TvAction.explicitPower(null))
    }

    @Test
    fun `every documented and every pinned shortcut key resolves`() {
        // res/xml/shortcuts.xml and the README list; keys are stored in pinned shortcuts.
        listOf("overlay", "screen_off", "pc", "audio_toggle", "power", "mute", "play_pause", "home", "netflix", "youtube", "spotify")
            .forEach { assertTrue(it, TvAction.fromExternal(it) != null) }
    }

    @Test
    fun `the same action twice in quick succession runs once`() {
        assertTrue(Debounce.accept("screen_off", now = 10_000))
        assertFalse(Debounce.accept("screen_off", now = 10_400))
        assertTrue("other actions are independent", Debounce.accept("mute", now = 10_500))
        assertTrue(Debounce.accept("screen_off", now = 10_000 + Debounce.windowMs))
    }
}
