package com.modernlghub.remote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * smarthome_devices.json was produced by the hub's localhome.get_dashboard_devices()
 * (branch claude/smarthome-color) with simulated WiZ bulbs and Tapo plugs.
 */
class SmartHomeParsingTest {

    private val devices = SmartDevice.parseList(
        requireNotNull(javaClass.getResource("/hub/smarthome_devices.json")).readText()
    )

    @Test
    fun `rooms and capabilities are read`() {
        assertEquals(listOf("Wohnzimmer", "Wohnzimmer", "Schlafzimmer", "Büro"), devices.map { it.room })
        val bar = devices[0]
        assertTrue(bar.canColor && bar.canWhite && bar.canDim && bar.adjustable)
        assertEquals(55, bar.brightness)
        assertEquals(0xFF2800, bar.rgb)
        assertNull(bar.kelvin)
        val ceiling = devices[1]
        assertFalse(ceiling.canColor)
        assertTrue(ceiling.canWhite)
        assertEquals(2700, ceiling.kelvin)
    }

    @Test
    fun `plugs and offline devices have no light controls`() {
        val plug = devices[2]
        assertTrue(plug.isPlug && plug.isOn && plug.online)
        assertFalse(plug.adjustable)
        val offline = devices[3]
        assertFalse(offline.online)
        assertFalse(offline.adjustable)
    }

    @Test
    fun `older hub without capabilities still allows dimming`() {
        val old = SmartDevice.parseList(
            """[{"id":"a1","name":"Licht","type":"light","state":"on","brightness":40,"color":"#ffaa55"},
               {"id":"p1","name":"Bett","type":"plug","state":"off"},
               {"id":"s1","name":"ST","type":"light","state":"off"}]"""
        )
        assertEquals(setOf(SmartDevice.CAP_BRIGHTNESS), old[0].capabilities)
        assertFalse(old[0].canColor)
        assertTrue(old[1].capabilities.isEmpty())
        assertFalse("SmartThings-era lamp without brightness stays a plain switch", old[2].adjustable)
        assertEquals("", old[0].room)
    }

    @Test
    fun `colour helpers`() {
        assertEquals(0xFF8800, SmartDevice.parseHexColor("#FF8800"))
        assertNull(SmartDevice.parseHexColor("red"))
        assertNull(SmartDevice.parseHexColor(null))
        assertEquals("#00ff0a", SmartDevice.toHexColor(0x00FF0A))
        // Same approximation the hub uses: 2700 K -> #ffa657 (see fixture)
        assertEquals(0xFFA657, SmartDevice.kelvinToRgb(2700))
        val cool = SmartDevice.kelvinToRgb(6500)
        assertTrue("6500 K is near neutral white", (cool shr 16 and 0xFF) > 240 && (cool and 0xFF) > 230)
    }
}
