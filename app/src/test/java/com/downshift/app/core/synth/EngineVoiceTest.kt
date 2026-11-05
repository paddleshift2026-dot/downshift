package com.downshift.app.core.synth

import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.engine.Engines
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EngineVoiceTest {
    private val fs = 48_000
    private val block = 480

    private fun voice(id: EngineId = EngineId.I4, exhaust: ExhaustPart = ExhaustPart.SPORT, seed: Long = 3) =
        EngineVoice(fs, Engines.spec(id), exhaust, seed)

    private fun EngineVoice.play(seconds: Double, drive: (Double) -> PulseDrive): FloatArray {
        val n = (seconds * fs).toInt()
        val out = FloatArray(n)
        val buf = FloatArray(block)
        var i = 0
        while (i < n) {
            val k = minOf(block, n - i)
            render(drive(i.toDouble() / fs), buf, k)
            System.arraycopy(buf, 0, out, i, k)
            i += k
        }
        return out
    }

    private fun EngineVoice.play(seconds: Double, drive: PulseDrive) = play(seconds) { drive }

    private fun rms(x: FloatArray, from: Int = 0) = sqrt((from until x.size).sumOf { (x[it] * x[it]).toDouble() } / (x.size - from))

    /** Goertzel power of one frequency. */
    private fun power(x: FloatArray, from: Int, hz: Double): Double {
        val c = 2 * cos(2 * PI * hz / fs)
        var s1 = 0.0
        var s2 = 0.0
        for (i in from until x.size) {
            val s0 = x[i] + c * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - c * s1 * s2
    }

    private fun maxStep(x: FloatArray, from: Int = 1, to: Int = x.size) = (maxOf(from, 1) until to).maxOf { abs(x[it] - x[it - 1]) }

    private fun on(rpm: Double, load: Double) = PulseDrive(rpm, load, combustion = true, cranking = false)

    @Test
    fun silentUntilItTurns() = assertTrue(voice().play(0.5, PulseDrive.OFF).all { it == 0f })

    @Test
    fun firesOncePerFiringEvent() {
        // a four-stroke fires each cylinder once every two revolutions
        val i4 = voice().apply { play(2.0, on(3000.0, 0.5)) }
        assertEquals(200.0, i4.fired.toDouble(), 2.0)
        val v12 = voice(EngineId.V12).apply { play(2.0, on(3000.0, 0.5)) }
        assertEquals(600.0, v12.fired.toDouble(), 3.0)
    }

    @Test
    fun theNoteIsTheFiringFrequency() {
        val x = voice().play(1.5, on(3000.0, 0.6)) // 100 firings a second
        val from = fs / 2
        assertTrue(power(x, from, 100.0) > 20 * power(x, from, 75.0))
        assertTrue(power(x, from, 200.0) > 20 * power(x, from, 150.0))
    }

    @Test
    fun loadMakesItLouderAndAFuelCutQuieter() {
        val from = fs / 4
        val idle = rms(voice().play(1.0, on(3000.0, 0.0)), from)
        val full = rms(voice().play(1.0, on(3000.0, 1.0)), from)
        val cut = rms(voice().play(1.0, PulseDrive(3000.0, 0.0, combustion = false, cranking = false)), from)
        assertTrue("full $full idle $idle", full > 1.8 * idle)
        assertTrue("cut $cut idle $idle", cut < 0.5 * idle)
    }

    @Test
    fun higherRpmIsLouderAtTheSameLoad() {
        val from = fs / 4
        assertTrue(rms(voice().play(1.0, on(5000.0, 0.5)), from) > 1.1 * rms(voice().play(1.0, on(1500.0, 0.5)), from))
    }

    @Test
    fun theExtremesStayFiniteAndBounded() {
        EngineId.entries.forEach { id ->
            ExhaustPart.entries.forEach { ex ->
                val x = voice(id, ex).play(1.0, on(9000.0, 1.0))
                assertTrue("$id $ex", x.all { it.isFinite() && abs(it) < 1f })
            }
        }
    }

    @Test
    fun aSweepThroughTheRevRangeHasNoClicks() {
        // Sharp pulse onsets are the note; a click would be a step larger than the engine makes holding the top rpm.
        val sweep = voice().play(2.0) { t -> on(900.0 + 3050.0 * t, 0.8) }
        val steady = voice().play(1.0, on(7000.0, 0.8))
        val limit = 1.5f * maxStep(steady, fs / 4)
        assertTrue("sweep ${maxStep(sweep)} steady limit $limit", maxStep(sweep) < limit)
    }

    @Test
    fun theSameSeedSoundsTheSame() {
        val a = voice(seed = 9).play(0.5, on(2500.0, 0.4))
        val b = voice(seed = 9).play(0.5, on(2500.0, 0.4))
        assertArrayEquals(a, b, 0f)
    }

    @Test
    fun crankingAddsTheStarterMotor() {
        val from = fs / 4
        val cranking = voice().play(1.0, PulseDrive(250.0, 0.0, combustion = false, cranking = true))
        val turning = voice().play(1.0, PulseDrive(250.0, 0.0, combustion = false, cranking = false))
        val hz = EngineVoice.starterHz(250.0)
        assertTrue(power(cranking, from, hz) > 10 * power(turning, from, hz))
    }

    @Test
    fun changingTheExhaustIsCrossfaded() {
        val v = voice()
        val before = v.play(0.5, on(3000.0, 0.5))
        v.configure(Engines.spec(EngineId.I4), ExhaustPart.TRACK)
        val after = v.play(0.2, on(3000.0, 0.5))
        val seam = abs(after[0] - before.last())
        assertTrue("seam $seam", seam < 2 * maxStep(before, fs / 4) + 1e-3f)
        assertTrue(maxStep(after, 1, 2048) < 2 * maxStep(before, fs / 4) + 1e-3f)
    }

    private fun burst(hz: Double, ms: Double = 30.0): FloatArray {
        val n = (ms * fs / 1000).toInt()
        return FloatArray(n) { i -> (sin(2 * PI * hz * i / fs) * (0.5 - 0.5 * cos(2 * PI * i / (n - 1)))).toFloat() }
    }

    @Test
    fun grainsFireAsOftenAsPulses() {
        val idle = PulseDrive(900.0, 0.2, combustion = true, cranking = false)
        val pulse = voice().apply { play(1.0, idle) }
        val grain = voice().apply { setGrains(listOf(burst(300.0))); play(1.0, idle) }
        assertEquals(pulse.fired, grain.fired)
    }

    @Test
    fun grainsMakeSoundAndDifferFromThePulse() {
        val drive = PulseDrive(2500.0, 0.6, combustion = true, cranking = false)
        val a = voice().play(1.0, drive)
        val b = voice().apply { setGrains(listOf(burst(300.0))) }.play(1.0, drive)
        assertTrue(rms(b, fs / 2) > 1e-3)
        assertTrue(a.indices.any { abs(a[it] - b[it]) > 1e-3 })
    }

    @Test
    fun theFourGrainsTakeTurns() {
        val v = voice().apply { setGrains(List(4) { burst(300.0 + 200 * it) }) }
        v.play(0.5, PulseDrive(900.0, 0.2, combustion = true, cranking = false))
        assertEquals(4, v.grainsUsed)
    }

    @Test
    fun withoutGrainsItIsThePulseAgain() {
        val v = voice().apply { setGrains(listOf(burst(300.0))); setGrains(null) }
        assertEquals(0, v.grainsUsed)
        assertTrue(rms(v.play(1.0, PulseDrive(1500.0, 0.5, combustion = true, cranking = false)), fs / 2) > 1e-3)
    }
}
