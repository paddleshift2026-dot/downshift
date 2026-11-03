package com.downshift.app.core.trip

import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.GarageState
import com.downshift.app.core.garage.PartKind
import org.junit.Assert.assertEquals
import org.junit.Test

class TripViewTest {
    private val t3 = Catalog.part("t3")!!
    private val trip = TripTotals(distanceKm = 18.4, knownKm = 17.2, keptKm = 16.7, unknownKm = 1.2, placesPassed = 3, placesKept = 3, turns = 22, signalled = 21)

    @Test
    fun theRingFillsTowardTheTargetChosenAtTheStart() {
        val g = TripView.gauge(t3, balanceAtStart = 1240, tripPoints = 38)
        assertEquals(0.852, g.frac, 1e-3)
        assertEquals("+38", g.center)
        assertEquals("터보 Stage 3까지 222 P", g.caption)
    }

    @Test
    fun theRingSaysWhenTheTargetIsInReach() {
        assertEquals(DriveGauge(1.0, "+38", "터보 Stage 3 해금 가능"), TripView.gauge(t3, 1480, 38))
        assertEquals(DriveGauge(1.0, "+0", "모든 부품을 모았어요"), TripView.gauge(null, 9000, 0))
        assertEquals(0.04, TripView.gauge(Catalog.part("v12")!!, 0, 0).frac, 1e-9)
    }

    @Test
    fun theSummaryReadsLikeTheDesign() {
        val v = TripView.summary(GarageState.DEMO.copy(balance = 1382), trip, PointRules.of(trip))
        assertEquals(138, v.points)
        assertEquals("18.4", v.distanceKm)
        assertEquals("97", v.compliance)
        assertEquals(1382, v.balance)
        assertEquals(
            listOf(
                SummaryRow("안전 주행 거리", "18.4km · 5 P/km", 92),
                SummaryRow("제한속도 준수", "97%", 17),
                SummaryRow("스쿨존·단속 구간", "3곳 모두 지킴", 18),
                SummaryRow("방향지시등", "21 / 22", 11),
            ),
            v.rows,
        )
        assertEquals("제한속도 정보가 없던 1.2km는 거리 포인트만 반영했어요", v.unknownLine)
        assertEquals(NextCard("t3", "터보 Stage 3까지 118 P", "안전 주행 약 16km", 1382.0 / 1500), v.next)
        assertEquals("지금 해금할 수 있어요 · 트랙 · 수동 블립 · 슈퍼차저", v.unlockLine)
    }

    @Test
    fun emptyCategoriesSayWhy() {
        val t = TripTotals(distanceKm = 2.0, unknownKm = 2.0)
        val v = TripView.summary(GarageState.FIRST_RUN.copy(balance = 10), t, PointRules.of(t))
        assertEquals("—", v.compliance)
        assertEquals(listOf("2.0km · 5 P/km", "정보 없음", "지나간 곳 없음", "회전 안내 없음"), v.rows.map { it.detail })
        assertEquals(listOf(10, 0, 0, 0), v.rows.map { it.points })
        assertEquals("제한속도 정보가 없던 2.0km는 거리 포인트만 반영했어요", v.unknownLine)
        assertEquals("해금할 수 있는 부품이 아직 없어요", v.unlockLine)
        assertEquals(NextCard("t1", "터보 Stage 1까지 290 P", "안전 주행 약 39km", 10.0 / 300), v.next)
    }

    @Test
    fun richOrCompleteGaragesSayEnough() {
        val rich = TripView.summary(GarageState.DEMO.copy(balance = 9000), trip, PointRules.of(trip))
        assertEquals(NextCard("v12", "V12 지금 해금 가능", "포인트가 충분해요", 1.0), rich.next)
        assertEquals("지금 해금할 수 있어요 · V12 · 터보 Stage 3 · 트랙 외 2개", rich.unlockLine)
        val all = Catalog.ALL.filter { it.kind == PartKind.NORMAL }.map { it.id }.toSet()
        val done = TripView.summary(GarageState.DEMO.copy(owned = all), trip, PointRules.of(trip))
        assertEquals(NextCard(null, "모든 부품을 모았어요", "개러지에서 조합을 바꿔 보세요", 1.0), done.next)
        assertEquals("", done.unlockLine)
    }
}
