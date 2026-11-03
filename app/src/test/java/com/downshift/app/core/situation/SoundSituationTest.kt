package com.downshift.app.core.situation

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundSituationTest {
    private val s = SoundSituation()

    private fun at(i: SituationInputs, steps: Int = 1): Situation {
        var r = s.step(i, 0.1)
        repeat(steps - 1) { r = s.step(i, 0.1) }
        return r
    }

    @Test
    fun normallyTheEnginePlaysAtTheDriversVolume() =
        assertEquals(Situation(1.0, Reason.NORMAL, 60), at(SituationInputs(userVolume = 0.6)))

    @Test
    fun guidanceDucksAndHoldsBrieflyAfterItEnds() {
        assertEquals(Situation(0.25, Reason.GUIDANCE, 15), at(SituationInputs(priority = PriorityAudio.GUIDANCE, userVolume = 0.6)))
        assertEquals(Reason.GUIDANCE, at(SituationInputs(userVolume = 0.6), steps = 5).reason)
        assertEquals(Situation(1.0, Reason.NORMAL, 60), at(SituationInputs(userVolume = 0.6), steps = 5))
    }

    @Test
    fun aSchoolZoneQuietsAndTakesTheChipOverAPassenger() {
        val r = at(SituationInputs(schoolZone = true, passenger = true, userVolume = 0.8))
        assertEquals(0.15, r.gain, 1e-9)
        assertEquals(Reason.SCHOOL_ZONE, r.reason)
        assertEquals(12, r.volumePct)
        assertEquals(Reason.QUIET_ZONE, at(SituationInputs(quietZone = true)).reason)
        assertEquals(0.3, at(SituationInputs(quietZone = true, schoolZone = true)).gain, 1e-9)
    }

    @Test
    fun aPassengerCapsTheVolumeAtFortyPercent() {
        assertEquals(Situation(0.5, Reason.PASSENGER, 40), at(SituationInputs(passenger = true, userVolume = 0.8)))
        assertEquals(Situation(1.0, Reason.NORMAL, 30), at(SituationInputs(passenger = true, userVolume = 0.3)))
    }

    @Test
    fun muteSilencesEverythingAndAStoppedEngineSaysSo() {
        assertEquals(Situation(0.0, Reason.MUTED, 0), at(SituationInputs(muted = true, schoolZone = true)))
        assertEquals(Reason.OFF, at(SituationInputs(running = false)).reason)
    }

    @Test
    fun callsAndAlarmsDuckLikeGuidance() {
        assertEquals(Reason.CALL, at(SituationInputs(priority = PriorityAudio.CALL)).reason)
        assertEquals(0.25, at(SituationInputs(priority = PriorityAudio.ALERT)).gain, 1e-9)
    }
}
