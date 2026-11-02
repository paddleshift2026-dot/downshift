package com.downshift.app.core.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeLedgerTest {
    private fun ledger() = ProbeLedger().apply {
        expect("aaos.speed", "차속", "AAOS PERF_VEHICLE_SPEED")
        expect("aaos.pedal", "가속 페달", "AAOS ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE")
    }

    private fun ProbeLedger.row(id: String) = rows().first { it.id == id }

    @Test
    fun expectedSignalWithoutValuesIsWaiting() {
        val row = ledger().row("aaos.speed")
        assertEquals(ProbeStatus.WAITING, row.status)
        assertEquals(0, row.count)
        assertNull(row.lastValue)
    }

    @Test
    fun repeatedSameValueIsReceivedNotChanging() {
        val l = ledger()
        l.record("aaos.speed", "0.0", 1_000)
        l.record("aaos.speed", "0.0", 2_000)
        val row = l.row("aaos.speed")
        assertEquals(ProbeStatus.RECEIVED, row.status)
        assertEquals(2, row.count)
        assertEquals("0.0", row.lastValue)
        assertEquals(2_000L, row.lastAtMs)
    }

    @Test
    fun twoDistinctValuesProveTheSignalChanges() {
        val l = ledger()
        l.record("aaos.speed", "0.0", 1_000)
        l.record("aaos.speed", "16.7", 2_000)
        assertEquals(ProbeStatus.CHANGING, l.row("aaos.speed").status)
    }

    @Test
    fun deniedKeepsTheReasonUntilAValueArrives() {
        val l = ledger()
        l.deny("aaos.pedal", "android.car.permission.CAR_POWERTRAIN")
        assertEquals(ProbeStatus.DENIED, l.row("aaos.pedal").status)
        assertEquals("android.car.permission.CAR_POWERTRAIN", l.row("aaos.pedal").note)
        l.record("aaos.pedal", "12.5", 3_000)
        assertEquals(ProbeStatus.RECEIVED, l.row("aaos.pedal").status)
    }

    @Test
    fun unsupportedIsReportedWithItsReason() {
        val l = ledger()
        l.unsupported("aaos.pedal", "차량이 제공 안 함")
        assertEquals(ProbeStatus.UNSUPPORTED, l.row("aaos.pedal").status)
        assertEquals("차량이 제공 안 함", l.row("aaos.pedal").note)
    }

    @Test
    fun valuesForAnUnexpectedIdAddARow() {
        val l = ledger()
        l.record("navi.onCameraAlert", "CameraAlert(distance=300)", 5_000)
        assertEquals(listOf("aaos.speed", "aaos.pedal", "navi.onCameraAlert"), l.rows().map { it.id })
    }

    @Test
    fun markdownHasOneLinePerRowInExpectOrder() {
        val l = ledger()
        l.record("aaos.speed", "0.0", 1_000)
        l.record("aaos.speed", "16.7", 2_000)
        val lines = l.toMarkdown().trim().lines()
        assertEquals("| 신호 | 출처 | 상태 | 마지막 값 | 횟수 | 메모 |", lines[0])
        assertTrue(lines[2].startsWith("| 차속 | AAOS PERF_VEHICLE_SPEED | 변화 확인 | 16.7 | 2 |"))
        assertTrue(lines[3].startsWith("| 가속 페달 |"))
        assertTrue(lines[3].contains("| 대기 |"))
    }

    @Test
    fun pipesAndNewlinesInValuesDoNotBreakTheTable() {
        val l = ledger()
        l.record("aaos.speed", "a|b\nc", 1_000)
        assertTrue(l.toMarkdown().contains("| a/b c |"))
    }
}
