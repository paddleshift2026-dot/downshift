package com.downshift.app.core.trip

import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.GarageState
import com.downshift.app.core.garage.Part
import com.downshift.app.core.garage.PartKind
import java.util.Locale
import kotlin.math.roundToInt

/** The drive screen's ring: how full, the trip's points in the middle, the target underneath. */
data class DriveGauge(val frac: Double, val center: String, val caption: String)

data class SummaryRow(val label: String, val detail: String, val points: Int)

/** The next part to aim for; partId is null once every part is owned. */
data class NextCard(val partId: String?, val title: String, val line: String, val frac: Double)

data class SummaryView(
    val points: Int,
    val distanceKm: String,
    val compliance: String,
    val balance: Int,
    val rows: List<SummaryRow>,
    val unknownLine: String?,
    val next: NextCard,
    val unlockLine: String,
)

/** The drive ring and the trip summary, worded as the design has them (Drive, Summary and Main's derive()). */
object TripView {
    private const val MIN_FRAC = 0.04 // the ring always shows a sliver
    private const val UNKNOWN_LINE_KM = 0.05

    /** The ring fills toward [target], the part chosen when the trip began, with the balance then plus this trip. */
    fun gauge(target: Part?, balanceAtStart: Int, tripPoints: Int): DriveGauge {
        val center = "+$tripPoints"
        if (target == null) return DriveGauge(1.0, center, "모든 부품을 모았어요")
        val have = balanceAtStart + tripPoints
        val left = target.price - have
        val frac = if (target.price > 0) (have.toDouble() / target.price).coerceIn(MIN_FRAC, 1.0) else 1.0
        return DriveGauge(frac, center, if (left > 0) "${target.name}까지 ${n(left)} P" else "${target.name} 해금 가능")
    }

    /** [after] is the garage with this trip's points already in the balance. */
    fun summary(after: GarageState, totals: TripTotals, p: TripPoints): SummaryView {
        val km = km(totals.distanceKm)
        val known = totals.knownKm > 0
        val compliance = if (known) (totals.keptKm / totals.knownKm * 100).roundToInt().toString() else "—"
        val rows = listOf(
            SummaryRow("안전 주행 거리", "${km}km · ${PointRules.PER_KM.roundToInt()} P/km", p.distance),
            SummaryRow("제한속도 준수", if (known) "$compliance%" else "정보 없음", p.limit),
            SummaryRow("스쿨존·단속 구간", places(totals), p.places),
            SummaryRow("방향지시등", if (totals.turns == 0) "회전 안내 없음" else "${totals.signalled} / ${totals.turns}", p.signals),
        )
        val unknown = if (totals.unknownKm >= UNKNOWN_LINE_KM) "제한속도 정보가 없던 ${km(totals.unknownKm)}km는 거리 포인트만 반영했어요" else null
        return SummaryView(p.total, km, compliance, after.balance, rows, unknown, next(after), unlockLine(after))
    }

    private fun places(t: TripTotals) = when {
        t.placesPassed == 0 -> "지나간 곳 없음"
        t.placesKept == t.placesPassed -> "${t.placesPassed}곳 모두 지킴"
        else -> "${t.placesPassed}곳 중 ${t.placesKept}곳"
    }

    private fun next(s: GarageState): NextCard {
        val t = Garage.nextTarget(s) ?: return NextCard(null, "모든 부품을 모았어요", "개러지에서 조합을 바꿔 보세요", 1.0)
        val need = (t.price - s.balance).coerceAtLeast(0)
        return if (need > 0) {
            NextCard(t.id, "${t.name}까지 ${n(need)} P", "안전 주행 약 ${Garage.kmFor(need)}km", s.balance.toDouble() / t.price)
        } else {
            NextCard(t.id, "${t.name} 지금 해금 가능", "포인트가 충분해요", 1.0)
        }
    }

    private fun unlockLine(s: GarageState): String {
        val names = Catalog.ALL.filter { it.kind == PartKind.NORMAL && it.id !in s.owned && it.price <= s.balance }
            .sortedByDescending { it.price }.map { it.name }
        return when {
            names.isNotEmpty() -> "지금 해금할 수 있어요 · " + names.take(3).joinToString(" · ") + (if (names.size > 3) " 외 ${names.size - 3}개" else "")
            Garage.nextTarget(s) != null -> "해금할 수 있는 부품이 아직 없어요"
            else -> ""
        }
    }

    private fun n(v: Int) = String.format(Locale.ROOT, "%,d", v)

    private fun km(v: Double) = String.format(Locale.ROOT, "%.1f", v)
}
