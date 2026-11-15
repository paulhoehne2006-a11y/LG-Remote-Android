package com.modernlghub.remote.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMathTest {

    @Test
    fun fullSaturationIsThePureHue() {
        assertEquals(0xFF0000, hsvToRgb(0f, 1f))
        assertEquals(0x00FF00, hsvToRgb(120f, 1f))
        assertEquals(0x0000FF, hsvToRgb(240f, 1f))
    }

    @Test
    fun zeroSaturationIsWhite() {
        assertEquals(0xFFFFFF, hsvToRgb(200f, 0f))
    }

    @Test
    fun pastelRoundTrips() {
        val pink = hsvToRgb(330f, 0.4f)
        assertEquals(0.4f, saturationOf(pink), 0.01f)
        assertEquals(330f, hueOf(pink), 1.5f)
    }

    @Test
    fun saturationOfGreyAndBlack() {
        assertEquals(0f, saturationOf(0x808080), 0.001f)
        assertEquals(0f, saturationOf(0x000000), 0.001f)
    }
}
