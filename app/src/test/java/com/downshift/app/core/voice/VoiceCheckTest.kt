package com.downshift.app.core.voice

import com.downshift.app.core.garage.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class VoiceCheckTest {
    private val fs = VoiceCheck.RATE
    private fun tone(sec: Double, amp: Double, hz: Double = 220.0) = FloatArray((sec * fs).toInt()) { (amp * sin(2 * PI * hz * it / fs)).toFloat() }
    private fun silence(sec: Double) = FloatArray((sec * fs).toInt())

    @Test
    fun theSlotsBelongToTheirTabs() {
        assertEquals(listOf(VoiceSlot.WHISTLE, VoiceSlot.BLOW_OFF), VoiceSlot.of(Category.INTAKE))
        assertEquals(VoiceSlot.BLOW_OFF, VoiceSlot.byKey("blowoff"))
        assertNull(VoiceSlot.byKey("horn"))
        assertEquals(setOf(Category.ENGINE, Category.TRANSMISSION), VoiceSlot.categories(setOf(VoiceSlot.ENGINE, VoiceSlot.SHIFT)))
    }

    @Test
    fun nothingHeardIsSilent() = assertEquals(VoiceVerdict.SILENT, VoiceCheck.clean(silence(2.5), VoiceSlot.ENGINE).verdict)

    @Test
    fun aWhisperIsQuiet() = assertEquals(VoiceVerdict.QUIET, VoiceCheck.clean(tone(1.0, 0.02), VoiceSlot.ENGINE).verdict)

    @Test
    fun aShoutThatClipsIsLoud() {
        val loud = FloatArray(fs) { (1.5 * sin(2 * PI * 220.0 * it / fs)).toFloat().coerceIn(-1f, 1f) }
        assertEquals(VoiceVerdict.LOUD, VoiceCheck.clean(loud, VoiceSlot.POP).verdict)
    }

    @Test
    fun aBlipIsTooShortForTheEngineButFineForAPop() {
        val blip = silence(0.5) + tone(0.1, 0.5) + silence(0.5)
        assertEquals(VoiceVerdict.SHORT, VoiceCheck.clean(blip, VoiceSlot.ENGINE).verdict)
        assertEquals(VoiceVerdict.OK, VoiceCheck.clean(blip, VoiceSlot.POP).verdict)
    }

    @Test
    fun aGoodTakeIsTrimmedAndSetToMinus3dB() {
        val c = VoiceCheck.clean(silence(0.5) + tone(1.0, 0.3) + silence(0.5), VoiceSlot.ENGINE)
        assertEquals(VoiceVerdict.OK, c.verdict)
        assertEquals(1.0, c.samples.size.toDouble() / fs, 0.02)
        assertEquals(0.708, c.samples.maxOf { abs(it) }.toDouble(), 0.01)
    }

    @Test
    fun aOneShotIsCappedToItsSlotAndFadesAtBothEnds() {
        val c = VoiceCheck.clean(tone(0.8, 0.3), VoiceSlot.POP)
        assertEquals((0.4 * fs).toInt(), c.samples.size)
        assertEquals(0f, c.samples.first(), 1e-6f)
        assertEquals(0f, c.samples.last(), 1e-6f)
    }
}
