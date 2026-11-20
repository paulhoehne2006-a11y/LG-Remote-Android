package com.modernlghub.remote.data

import com.modernlghub.remote.data.OptimisticState.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimisticStateTest {

    private var now = 0L
    private val holds = OptimisticState(clock = { now }, holdMs = 4_000)

    private fun status(volume: Int = 20, muted: Boolean = false, screenOff: Boolean = false, app: String = "netflix") =
        TvStatus(power = "Active", screenOff = screenOff, currentAppId = app, audio = AudioStatus("external_optical", volume, muted))

    @Test
    fun `a poll that started before the command finished cannot undo the optimistic value`() {
        val pollStarted = holds.tick()          // poll is on its way …
        holds.begin(Field.VOLUME)               // … user presses volume up
        val local = status(volume = 21)
        val merged = holds.merge(local, status(volume = 20), pollStarted)
        assertEquals(21, merged.audio.volume)
        assertTrue(holds.isHeld(Field.VOLUME))
    }

    @Test
    fun `while the command is in flight even newer polls keep the local value`() {
        holds.begin(Field.SCREEN)
        val merged = holds.merge(status(screenOff = true), status(screenOff = false), holds.tick())
        assertTrue(merged.screenOff)
    }

    @Test
    fun `a poll that started after the command finished wins, even if it disagrees`() {
        holds.begin(Field.SCREEN)
        holds.end(Field.SCREEN, ok = true)
        val stalePoll = 0L
        assertTrue(holds.merge(status(screenOff = true), status(screenOff = false), stalePoll).screenOff)
        val merged = holds.merge(status(screenOff = true), status(screenOff = false), holds.tick())
        assertFalse("TV state is the truth once confirmed", merged.screenOff)
        assertFalse(holds.isHeld(Field.SCREEN))
    }

    @Test
    fun `a failed command releases the hold at once (rollback)`() {
        val pollStarted = holds.tick()
        holds.begin(Field.MUTE)
        holds.end(Field.MUTE, ok = false)
        assertFalse(holds.merge(status(muted = true), status(muted = false), pollStarted).audio.muted)
    }

    @Test
    fun `holds expire as a safety net`() {
        holds.begin(Field.APP)
        val pollStarted = 0L
        now = 3_999
        assertEquals("youtube", holds.merge(status(app = "youtube"), status(app = "netflix"), pollStarted).currentAppId)
        now = 4_000
        assertEquals("netflix", holds.merge(status(app = "youtube"), status(app = "netflix"), pollStarted).currentAppId)
    }

    @Test
    fun `several queued volume steps hold until the last one is done`() {
        repeat(3) { holds.begin(Field.VOLUME) }
        holds.end(Field.VOLUME, ok = true)
        holds.end(Field.VOLUME, ok = true)
        assertEquals(23, holds.merge(status(volume = 23), status(volume = 21), holds.tick()).audio.volume)
        holds.end(Field.VOLUME, ok = true)
        assertEquals(22, holds.merge(status(volume = 23), status(volume = 22), holds.tick()).audio.volume)
    }

    @Test
    fun `abandoned begin does not leave a hold behind forever`() {
        holds.begin(Field.VOLUME)
        holds.abandon(Field.VOLUME)
        assertEquals(20, holds.merge(status(volume = 25), status(volume = 20), holds.tick()).audio.volume)
    }

    @Test
    fun `fields without a hold always take the polled value`() {
        holds.begin(Field.VOLUME)
        val merged = holds.merge(status(volume = 30, app = "youtube", muted = true), status(volume = 20, app = "netflix"), holds.tick())
        assertEquals(30, merged.audio.volume)
        assertEquals("netflix", merged.currentAppId)
        assertFalse(merged.audio.muted)
    }

    @Test
    fun `clear drops every hold`() {
        holds.begin(Field.OUTPUT)
        holds.begin(Field.POWER)
        holds.clear()
        val remote = status()
        assertEquals(remote, holds.merge(status(screenOff = true).copy(power = "Power Off"), remote, 0))
    }
}
