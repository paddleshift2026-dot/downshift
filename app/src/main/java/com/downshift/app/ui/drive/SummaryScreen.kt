package com.downshift.app.ui.drive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.Category
import com.downshift.app.core.trip.NextCard
import com.downshift.app.core.trip.SummaryRow
import com.downshift.app.core.trip.SummaryView
import com.downshift.app.ui.AppIcons
import com.downshift.app.ui.Clean
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Gauge
import com.downshift.app.ui.GaugeMode
import com.downshift.app.ui.Ground
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.Rule
import com.downshift.app.ui.ScreenHeader
import com.downshift.app.ui.Sheet
import com.downshift.app.ui.SheetRaised
import com.downshift.app.ui.garage.points
import com.downshift.app.ui.garage.rememberAssetImage
import kotlin.math.roundToInt

/**
 * The trip summary (design: Summary.dc.html; PRD feature F): this trip's points counting up, distance, compliance and
 * balance, the points by kind, the next part to unlock, what can be unlocked now, and the way back to the garage.
 */
@Composable
fun SummaryScreen(vm: TripViewModel = viewModel()) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val v = summary
    BoxWithConstraints(Modifier.fillMaxSize().background(Ground)) {
        val wide = maxWidth >= 1200.dp
        val padX = if (wide) 48.dp else 40.dp
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("주행 결산", wide)
            if (v != null) {
                if (wide) {
                    Row(
                        Modifier.weight(1f).fillMaxWidth().padding(start = padX, end = padX, top = 32.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(64.dp),
                    ) {
                        Totals(v, live.lapse, Modifier.weight(700f))
                        Next(v, Modifier.weight(600f))
                    }
                } else {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = padX, end = padX, top = 32.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        Totals(v, live.lapse, Modifier.fillMaxWidth())
                        Next(v, Modifier.fillMaxWidth())
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("결산할 주행이 없어요", color = Faint, fontSize = 30.sp)
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = padX, end = padX, bottom = 40.dp), contentAlignment = Alignment.CenterEnd) {
                Row(
                    Modifier.then(if (wide) Modifier.width(560.dp) else Modifier.fillMaxWidth()).height(96.dp).clip(RoundedCornerShape(20.dp)).background(Ink)
                        .clickable(role = Role.Button, onClick = vm::toGarage),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    Text("개러지에서 해금하기", color = Ground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Icon(AppIcons.Forward, contentDescription = null, tint = Ground, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

/** The left column: the count-up, the three numbers, the rows by kind and the unknown-limit note. */
@Composable
private fun Totals(v: SummaryView, lapse: Int, modifier: Modifier) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(v) {
        shown.snapTo(0f)
        shown.animateTo(v.points.toFloat(), tween(900, easing = EaseOutCubic))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("+${points(shown.value.roundToInt())}", color = Clean, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 112.sp, lineHeight = 112.sp)
                Text("P", Modifier.padding(bottom = 12.dp), color = Clean, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 44.sp)
            }
            Text("이번 주행으로 모은 안전 주행 포인트", Modifier.padding(top = 8.dp), color = Muted, fontSize = 24.sp)
            if (lapse > 1) Text("시연 배속 ×${lapse}으로 센 주행이에요", Modifier.padding(top = 6.dp), color = Faint, fontSize = 20.sp)
        }
        Row(Modifier.fillMaxWidth().rules(top = true, bottom = true)) {
            Stat("거리", v.distanceKm, "km", Ink, Modifier.weight(1f))
            Stat("준수율", v.compliance, if (v.compliance == "—") "" else "%", Ink, Modifier.weight(1f).rules(left = true).padding(start = 24.dp))
            Stat("포인트 잔액", points(v.balance), "P", Clean, Modifier.weight(1f).rules(left = true).padding(start = 24.dp))
        }
        Column {
            v.rows.forEach { PointsRow(it) }
        }
        v.unknownLine?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(AppIcons.Info, contentDescription = null, tint = Faint, modifier = Modifier.size(24.dp))
                Text(it, color = Faint, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(vertical = 18.dp)) {
        Text(label, color = Faint, fontSize = 20.sp)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, color = color, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 48.sp)
            if (unit.isNotEmpty()) {
                Text(unit, Modifier.padding(start = 6.dp, bottom = 6.dp), color = if (color == Clean) Clean else Muted, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun PointsRow(r: SummaryRow) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).drawBehind { drawRect(SheetRaised, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(r.label, Modifier.weight(1f), color = Ink, fontSize = 24.sp)
        Text(r.detail, color = Muted, fontSize = 22.sp)
        Text(
            "+${r.points}", Modifier.width(88.dp), textAlign = TextAlign.End,
            color = Clean, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 32.sp,
        )
    }
}

/** The right column: the next part to unlock on a small ring, and what can be unlocked right now. */
@Composable
private fun Next(v: SummaryView, modifier: Modifier) {
    val next = v.next
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Sheet).border(1.dp, Rule, RoundedCornerShape(20.dp))
                .padding(start = 20.dp, end = 28.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            NextRing(next)
            Column {
                Text("다음 해금", color = Faint, fontSize = 20.sp)
                Text(next.title, Modifier.padding(top = 4.dp), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(next.line, Modifier.padding(top = 6.dp), color = Muted, fontSize = 22.sp)
            }
        }
        if (v.unlockLine.isNotEmpty()) Text(v.unlockLine, color = Clean, fontSize = 22.sp, lineHeight = 32.sp)
    }
}

@Composable
private fun NextRing(next: NextCard) {
    val pct = (next.frac * 100).roundToInt()
    val part = next.partId?.let(Catalog::part)
    val path = when {
        part == null -> "engine3d/v12t/hero.webp"
        part.category == Category.ENGINE -> "engine3d/${part.id}/hero.webp"
        else -> "parts/${part.id}.webp"
    }
    val art = rememberAssetImage(path, 160)
    Box(Modifier.size(200.dp)) {
        Gauge(
            size = 200.dp, mode = GaugeMode.POINTS, value = { next.frac.toFloat() }, center = { "$pct%" }, unit = "", caption = { "" },
            description = "다음 해금까지 $pct%",
        )
        if (art != null) Image(art, contentDescription = null, modifier = Modifier.offset(46.dp, 28.dp).size(108.dp, 88.dp))
    }
}

/** Hairlines on the chosen sides, as the design's bordered stats grid. */
private fun Modifier.rules(top: Boolean = false, bottom: Boolean = false, left: Boolean = false) = drawBehind {
    val w = 1.dp.toPx()
    if (top) drawRect(Rule, Offset.Zero, Size(size.width, w))
    if (bottom) drawRect(Rule, Offset(0f, size.height - w), Size(size.width, w))
    if (left) drawRect(Rule, Offset.Zero, Size(w, size.height))
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
