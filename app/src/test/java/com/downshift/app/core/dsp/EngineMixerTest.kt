package com.downshift.app.core.dsp

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.sound.LoopVoice
import com.downshift.app.core.sound.OneShot
import com.downshift.app.core.sound.Pop
import com.downshift.app.core.sound.SoundPlan
import com.downshift.app.core.sound.SynthVoices
import com.downshift.app.core.synth.EngineVoice
import com.downshift.app.core.synth.PulseDrive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class EngineMixerTest {
    private val fs = 44_100
    private val sine = FloatArray(441) { (0.5 * sin(2 * PI * 100.0 * it / fs)).toFloat() }

    private fun plan(gain: Double, master: Double = 1.0, shots: List<OneShot> = emptyList(), exhaust: ExhaustPart = ExhaustPart.STOCK) =
        SoundPlan(listOf(LoopVoice("tone", 1.0, gain)), shots, emptyList(), false, SynthVoices.SILENT, exhaust, master, loudness = 1.0)

    private fun EngineMixer.renderSeconds(p: SoundPlan, seconds: Double): FloatArray {
        val n = (seconds * fs).toInt()
        val out = FloatArray(n)
        val buf = FloatArray(256)
        var i = 0
        while (i < n) {
            val k = minOf(256, n - i)
            render(p, buf, k)
            System.arraycopy(buf, 0, out, i, k)
            i += k
        }
        return out
    }

    private fun rms(x: FloatArray, from: Int = 0) = sqrt((from until x.size).sumOf { (x[it] * x[it]).toDouble() } / (x.size - from))

    @Test
    fun silentWhenMasterIsZero() {
        val m = EngineMixer(fs, mapOf("tone" to sine))
        assertTrue(m.renderSeconds(plan(1.0, master = 0.0), 0.2).all { it == 0f })
    }

    @Test
    fun aSingleLoopComesOutAtItsOwnLevel() {
        val m = EngineMixer(fs, mapOf("tone" to sine))
        val out = m.renderSeconds(plan(1.0), 1.0)
        assertEquals(rms(sine), rms(out, fs / 2), rms(sine) * 0.06)
    }

    @Test
    fun gainChangesAreRampedNotStepped() {
        val m = EngineMixer(fs, mapOf("tone" to FloatArray(64) { 0.5f }))
        m.renderSeconds(plan(0.0), 0.05)
        val out = m.renderSeconds(plan(1.0), 0.05)
        val maxStep = (1 until out.size).maxOf { abs(out[it] - out[it - 1]) }
        assertTrue("step $maxStep", maxStep < 0.02f)
    }

    @Test
    fun exhaustSwapIsCrossfaded() {
        val m = EngineMixer(fs, mapOf("tone" to sine))
        m.renderSeconds(plan(1.0), 0.2)
        val out = m.renderSeconds(plan(1.0, exhaust = ExhaustPart.TRACK), 0.2)
        val maxStep = (1 until out.size).maxOf { abs(out[it] - out[it - 1]) }
        assertTrue("step $maxStep", maxStep < 0.05f)
    }

    @Test
    fun outputNeverClips() {
        val loud = FloatArray(441) { if (it % 2 == 0) 1f else -1f }
        val m = EngineMixer(fs, mapOf("a" to loud, "b" to loud, "c" to loud))
        val p = SoundPlan(listOf("a", "b", "c").map { LoopVoice(it, 1.0, 1.0) }, emptyList(), List(5) { Pop(1.0, 10.0, 5000.0) }, true,
            SynthVoices(3000.0, 1.0, 1000.0, 1.0), ExhaustPart.TRACK, 1.0, 1.0)
        assertTrue(m.renderSeconds(p, 0.5).all { it in -1f..1f })
    }

    @Test
    fun oneShotsPlayOnceThenStop() {
        val m = EngineMixer(fs, mapOf("tone" to FloatArray(64), "click" to FloatArray(200) { 0.8f }))
        val first = m.renderSeconds(plan(0.0, shots = listOf(OneShot("click", 1.0))), 0.02)
        val after = m.renderSeconds(plan(0.0), 0.05)
        assertTrue(rms(first) > 0.05)
        assertTrue(rms(after, 400) < 1e-3)
    }

    @Test
    fun consecutiveBlocksJoinWithoutASeam() {
        val a = EngineMixer(fs, mapOf("tone" to sine))
        val b = EngineMixer(fs, mapOf("tone" to sine))
        a.renderSeconds(plan(1.0), 0.1)
        b.renderSeconds(plan(1.0), 0.1)
        val whole = FloatArray(512)
        a.render(plan(1.0), whole, 512)
        val h1 = FloatArray(256)
        val h2 = FloatArray(256)
        b.render(plan(1.0), h1, 256)
        b.render(plan(1.0), h2, 256)
        assertArrayEquals(whole, h1 + h2, 1e-6f)
    }

    @Test
    fun clipsRecordedAtAnotherRateKeepTheirPitchAndLength() {
        val clipRate = 44_100
        val tone = FloatArray(441) { (0.5 * sin(2 * PI * 100.0 * it / clipRate)).toFloat() }
        val m = EngineMixer(48_000, mapOf("tone" to tone, "click" to FloatArray(4410) { 0.5f }), clipRate = clipRate)
        val out = FloatArray(48_000)
        val buf = FloatArray(480)
        for (b in 0 until 100) {
            m.render(plan(1.0), buf, 480)
            System.arraycopy(buf, 0, out, b * 480, 480)
        }
        val rising = (24_001 until 48_000).count { out[it - 1] < 0f && out[it] >= 0f }
        assertEquals(50, rising)

        val shot = EngineMixer(48_000, mapOf("tone" to tone, "click" to FloatArray(4410) { 0.5f }), clipRate = clipRate)
        val heard = FloatArray(9_600)
        shot.render(plan(0.0, shots = listOf(OneShot("click", 1.0))), buf, 480)
        System.arraycopy(buf, 0, heard, 0, 480)
        for (b in 1 until 20) {
            shot.render(plan(0.0), buf, 480)
            System.arraycopy(buf, 0, heard, b * 480, 480)
        }
        assertEquals(4_800.0, heard.count { it > 0.25f }.toDouble(), 30.0)
    }

    @Test
    fun aPulseVoiceIsMixedWhenThePlanDrivesIt() {
        val drive = PulseDrive(3000.0, 0.8, combustion = true, cranking = false)
        val p = SoundPlan(emptyList(), emptyList(), emptyList(), false, SynthVoices.SILENT, ExhaustPart.SPORT, 1.0, 1.0, drive)
        val heard = EngineMixer(fs, emptyMap()).apply { setVoice(EngineVoice(fs, Engines.spec(EngineId.I4), ExhaustPart.SPORT)) }
        assertTrue(rms(heard.renderSeconds(p, 0.5), fs / 4) > 0.01)
        val muted = EngineMixer(fs, emptyMap()).apply { setVoice(EngineVoice(fs, Engines.spec(EngineId.I4), ExhaustPart.SPORT)) }
        assertTrue(muted.renderSeconds(p.copy(master = 0.0), 0.3).all { it == 0f })
    }

    @Test
    fun unknownClipsAreIgnored() {
        assertTrue(EngineMixer(fs, emptyMap()).renderSeconds(plan(1.0), 0.05).all { it == 0f })
    }
}
