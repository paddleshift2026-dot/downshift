package com.downshift.app.core.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PedalLinkTest {
    @Test
    fun aChangeIsPassedOnAndARepeatIsNot() {
        val l = PedalLink()
        assertEquals(40, l.onPedal(40, 0))
        assertNull(l.onPedal(40, 10))
        assertEquals(0, l.onPedal(0, 20))
        assertEquals(0, l.pct)
    }

    @Test
    fun aQuietLinkLetsGoOnce() {
        val l = PedalLink(staleMs = 1500)
        l.onPedal(80, 0)
        assertNull(l.tick(1_000))
        assertEquals(0, l.tick(1_600))
        assertNull(l.tick(2_000))
        assertEquals(0, l.pct)
    }

    @Test
    fun pingsKeepAHeldPedalDown() {
        val l = PedalLink(staleMs = 1500)
        l.onPedal(80, 0)
        l.onMessage(1_000)
        assertNull(l.tick(2_000))
        assertEquals(0, l.tick(2_600))
    }

    @Test
    fun aQuietLinkWithThePedalUpHasNothingToDo() {
        val l = PedalLink()
        assertNull(l.onPedal(0, 0))
        assertNull(l.tick(60_000))
    }

    @Test
    fun closingThePageLetsGoOfAHeldPedal() {
        val l = PedalLink()
        l.onPedal(60, 0)
        assertEquals(0, l.onClose())
        assertNull(l.onClose())
    }

    @Test
    fun afterLettingGoTheNextPressCounts() {
        val l = PedalLink(staleMs = 1500)
        l.onPedal(80, 0)
        l.tick(1_600)
        assertEquals(80, l.onPedal(80, 1_700))
    }

    @Test
    fun withTwoPagesTheDeeperPressDrives() {
        val m = PedalMix<String>()
        assertEquals(30, m.onPedal("a", 30, 0))
        assertEquals(70, m.onPedal("b", 70, 0))
        assertNull(m.onPedal("a", 50, 10))
        assertEquals(50, m.onPedal("b", 0, 20))
    }

    @Test
    fun aPageThatClosesLetsGoOfItsOwnPressOnly() {
        val m = PedalMix<String>()
        m.onPedal("a", 30, 0)
        m.onPedal("b", 80, 0)
        assertEquals(30, m.onClose("b"))
        assertEquals(0, m.onClose("a"))
        assertNull(m.onClose("a"))
    }

    @Test
    fun aQuietPageIsLetGoWhileTheOtherKeepsPinging() {
        val m = PedalMix<String>(staleMs = 1500)
        m.onPedal("a", 80, 0)
        m.onPedal("b", 20, 0)
        m.onMessage("b", 1_000)
        assertEquals(20, m.tick(1_600))
        m.onMessage("b", 2_000)
        assertNull(m.tick(2_600))
        assertEquals(20, m.pct)
    }
}
