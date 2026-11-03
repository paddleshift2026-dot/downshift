package com.downshift.app.core.garage

import org.junit.Assert.assertEquals
import org.junit.Test

class TurntableTest {
    private val i4 = """{"az":35,"el":24,"anchors":{"intake":{"k":21,"p":[0.462,0.5632]},"exhaust":{"k":7,"p":[0.584,0.5228]},"trans":{"k":11,"p":[0.7307,0.6931]}}}"""

    @Test
    fun draggingMovesAFrameAStepAndWraps() {
        assertEquals(21, Turntable.dragFrame(0, dxPx = 30f, pxPerFrame = 10f))
        assertEquals(1, Turntable.dragFrame(22, dxPx = -30f, pxPerFrame = 10f))
        assertEquals(5, Turntable.dragFrame(5, dxPx = 4f, pxPerFrame = 10f))
    }

    @Test
    fun spinningTakesTheShortWay() {
        assertEquals(1, Turntable.spinStep(2, 21))
        assertEquals(21, Turntable.spinStep(20, 2))
        assertEquals(5, Turntable.spinStep(5, 5))
    }

    @Test
    fun theOverlayFollowsTheIntake() {
        assertEquals("v8sc", Turntable.variant("v8", "sc"))
        assertEquals("v8", Turntable.variant("v8", "t2"))
        assertEquals("v12t", Turntable.variant("v12", "t1"))
        assertEquals("v12", Turntable.variant("v12", "sc"))
        assertEquals("i4", Turntable.variant("i4", "t3"))
    }

    @Test
    fun anchorsComeFromTheRenderTool() {
        val t = Turntable.parse(i4)
        assertEquals(24, t.frames)
        assertEquals(mapOf(Category.ENGINE to 0, Category.INTAKE to 21, Category.EXHAUST to 7, Category.TRANSMISSION to 11), t.focus)
        assertEquals(0.462, t.anchors.getValue(Category.INTAKE).first, 1e-9)
        assertEquals(0.6931, t.anchors.getValue(Category.TRANSMISSION).second, 1e-9)
    }
}
