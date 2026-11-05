package com.downshift.app.core.voice

import com.downshift.app.core.garage.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceKitTest {
    private val fs = 48_000
    private fun tone(sec: Double) = FloatArray((sec * fs).toInt()) { (0.5 * sin(2 * PI * 200.0 * it / fs)).toFloat() }
    private val all = VoiceSlot.entries.associateWith { tone(if (it.sustained) 1.0 else 0.3) }

    @Test
    fun onlyTheCardsThatAreOnPlay() {
        val k = VoiceKit.of(all, setOf(Category.ENGINE, Category.TRANSMISSION), fs)
        assertEquals(4, k.engine!!.size)
        assertNull(k.whistle)
        assertNull(k.blowOff)
        assertNull(k.pop)
        assertNotNull(k.shift)
        assertEquals(setOf(VoiceKit.SHIFT), k.clips().keys)
    }

    @Test
    fun nothingOnIsEmpty() = assertTrue(VoiceKit.of(all, emptySet(), fs).empty)

    @Test
    fun aCardWithoutItsRecordingStaysSynthetic() {
        val k = VoiceKit.of(mapOf(VoiceSlot.BLOW_OFF to tone(0.3)), setOf(Category.INTAKE), fs)
        assertNull(k.whistle)
        assertNotNull(k.blowOff)
    }

    @Test
    fun materialsMoveToTheEngineRate() {
        val k = VoiceKit.of(all, Category.entries.toSet(), 44_100)
        assertEquals(1440 * 44_100 / fs, k.engine!!.first().size)
        assertEquals(14_400 * 44_100 / fs, k.pop!!.size)
    }
}
