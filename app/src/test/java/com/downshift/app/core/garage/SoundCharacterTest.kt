package com.downshift.app.core.garage

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundCharacterTest {
    @Test
    fun theBaseBuildIsALightRaspyFour() = assertEquals(SoundBars(3, 6, 0, 2), SoundCharacter.of(GarageState.FIRST_RUN))

    @Test
    fun partsAddTheirCharacter() {
        val s = GarageState.FIRST_RUN.copy(engine = "v8", intake = "t3", exhaust = "track", transmission = "mt")
        assertEquals(SoundBars(10, 6, 8, 7), SoundCharacter.of(s))
    }

    @Test
    fun thePopsSliderShiftsPopsAndEverythingStaysInRange() {
        val s = GarageState.FIRST_RUN.copy(engine = "v12", intake = "sc", exhaust = "track", transmission = "dct", tune = Tune(pops = 5))
        assertEquals(SoundBars(7, 10, 4, 8), SoundCharacter.of(s))
        assertEquals(0, SoundCharacter.of(GarageState.FIRST_RUN.copy(tune = Tune(pops = 0))).pop)
    }

    @Test
    fun aPreviewIsTheBuildWithThatPartIn() = assertEquals(SoundBars(8, 5, 5, 4), SoundCharacter.of(GarageState.DEMO, previewId = "v8"))

    @Test
    fun placeholdersPreviewAsTheCurrentBuild() = assertEquals(SoundCharacter.of(GarageState.DEMO), SoundCharacter.of(GarageState.DEMO, previewId = "e4"))
}
