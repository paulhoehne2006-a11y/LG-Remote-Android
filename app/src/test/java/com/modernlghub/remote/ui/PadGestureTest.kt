package com.modernlghub.remote.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.modernlghub.remote.ui.components.DPadControl
import com.modernlghub.remote.ui.components.Direction
import com.modernlghub.remote.ui.components.GesturePad
import com.modernlghub.remote.ui.theme.ModernLgTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pads are the product; these pin down how they turn touches into keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class PadGestureTest {
    @get:Rule val compose = createComposeRule()

    private val dirs = mutableListOf<Direction>()
    private var selects = 0
    private var longs = 0

    private fun swipePad() = compose.setContent {
        ModernLgTheme(reduceMotion = true) {
            GesturePad(Modifier.size(320.dp), onDirection = { dirs += it }, onSelect = { selects++ }, onSelectLong = { longs++ })
        }
    }

    private fun dpad() = compose.setContent {
        ModernLgTheme(reduceMotion = true) {
            DPadControl(Modifier.size(320.dp), onDirection = { dirs += it }, onSelect = { selects++ }, onSelectLong = { longs++ })
        }
    }

    @Test
    fun tapIsOk() {
        swipePad()
        compose.onRoot().performTouchInput { down(center); up() }
        compose.waitForIdle()
        assertEquals(1, selects)
        assertEquals(0, longs)
        assertTrue(dirs.isEmpty())
    }

    @Test
    fun cancelledTouchSendsNothing() {
        // The system taking the gesture (edge back swipe) arrives as a cancel, never as OK.
        swipePad()
        compose.onRoot().performTouchInput { down(center); cancel() }
        compose.waitForIdle()
        assertEquals(0, selects)
        assertTrue(dirs.isEmpty())
    }

    @Test
    fun slightlyDiagonalSwipeStaysOnItsAxis() {
        swipePad()
        // 120 px right with 40 px downward drift: three steps right, no stray DOWN.
        compose.onRoot().performTouchInput {
            down(center)
            repeat(12) { moveBy(Offset(10f + 0.5f * 0, 0f)); moveBy(Offset(0f, 10f / 3f)) }
            up()
        }
        compose.waitForIdle()
        assertTrue("got $dirs", dirs.isNotEmpty() && dirs.all { it == Direction.RIGHT })
        assertEquals(0, selects)
    }

    @Test
    fun holdIsLongOkNotOk() {
        swipePad()
        compose.onRoot().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, longs)
        assertEquals(0, selects)
    }

    @Test
    fun dpadRepeatsWhileHeldAndStopsOnRelease() {
        dpad()
        compose.onRoot().performTouchInput { down(Offset(centerX + width * 0.35f, centerY)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        val held = dirs.size
        assertTrue("expected repeats, got $held", held >= 4)
        assertTrue(dirs.all { it == Direction.RIGHT })
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals("repeat must stop on release", held, dirs.size)
    }

    @Test
    fun dpadCancelStopsRepeat() {
        dpad()
        compose.onRoot().performTouchInput { down(Offset(centerX, centerY - height * 0.35f)) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performTouchInput { cancel() }
        compose.waitForIdle()
        val held = dirs.size
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        assertEquals(held, dirs.size)
        assertEquals(0, selects)
    }
}
