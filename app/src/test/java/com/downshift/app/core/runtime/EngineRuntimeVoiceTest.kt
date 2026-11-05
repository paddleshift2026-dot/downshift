package com.downshift.app.core.runtime

import com.downshift.app.core.build.Build
import com.downshift.app.core.build.TransmissionPart
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.model.DriveInputs
import com.downshift.app.core.model.FuelKind
import com.downshift.app.core.model.GearState
import com.downshift.app.core.pack.ProceduralPack
import com.downshift.app.core.voice.VoiceKit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class EngineRuntimeVoiceTest {
    private val fs = 48_000
    private val block = 480
    private val revving = { _: Double -> DriveInputs(0.4, 0.0, 0.0, GearState.PARK) }
    private val grains = List(4) { k -> FloatArray(1440) { i -> sin(2 * PI * (250.0 + 100 * k) * i / fs).toFloat() } }

    private fun runtime(build: Build = Build()) = EngineRuntime(ProceduralPack.build(Engines.spec(build.engine)), build, FuelKind.EV, fs)

    private fun EngineRuntime.play(seconds: Double, inputs: (Double) -> DriveInputs): FloatArray {
        val n = (seconds * fs).toInt()
        val out = FloatArray(n)
        val buf = FloatArray(block)
        var i = 0
        while (i < n) {
            renderBlock(inputs(i.toDouble() / fs), buf, block)
            System.arraycopy(buf, 0, out, i, minOf(block, n - i))
            i += block
        }
        return out
    }

    @Test
    fun withNoVoicesTheSoundIsUnchanged() {
        val a = runtime().apply { start() }.play(1.5, revving)
        val b = runtime().apply { setVoices(VoiceKit.NONE); start() }.play(1.5, revving)
        assertArrayEquals(a, b, 0f)
    }

    @Test
    fun engineGrainsChangeTheSound() {
        val a = runtime().apply { start() }.play(2.0, revving)
        val b = runtime().apply { setVoices(VoiceKit(engine = grains)); start() }.play(2.0, revving)
        assertTrue((fs until a.size).any { abs(a[it] - b[it]) > 1e-3 })
    }

    @Test
    fun aVoicedShiftPlaysWhenTheCarChangesGear() {
        val rt = runtime(Build(transmission = TransmissionPart.DCT))
        rt.setVoices(VoiceKit(shift = FloatArray(4800) { 0.2f }))
        rt.start()
        rt.play(1.5) { DriveInputs(0.0, 0.0, 0.0, GearState.PARK) }
        rt.play(10.0) { t -> DriveInputs(1.0, 0.0, minOf(30.0, 3.0 * t), GearState.DRIVE) }
        assertTrue("counts ${rt.voiceShotCounts}", (rt.voiceShotCounts[VoiceKit.SHIFT] ?: 0) >= 1)
    }
}
