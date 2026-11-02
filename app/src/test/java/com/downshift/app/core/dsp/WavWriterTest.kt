package com.downshift.app.core.dsp

import com.downshift.app.core.audio.WavLoader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavWriterTest {
    @Test
    fun writesAWavTheLoaderReadsBack() {
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f)
        val w = WavLoader.load(WavWriter.pcm16Mono(samples, samples.size, 44_100))
        assertEquals(44_100, w.sampleRate)
        assertArrayEquals(samples, w.mono, 1f / 16_000)
    }
}
