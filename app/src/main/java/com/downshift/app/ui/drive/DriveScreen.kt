package com.downshift.app.ui.drive

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.downshift.app.DriveLive
import com.downshift.app.core.situation.Reason
import com.downshift.app.core.situation.Situation
import com.downshift.app.ui.AppIcons
import com.downshift.app.ui.Calm
import com.downshift.app.ui.CalmSheet
import com.downshift.app.ui.Dim
import com.downshift.app.ui.Gauge
import com.downshift.app.ui.GaugeMode
import com.downshift.app.ui.Ground
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.RuleStrong
import com.downshift.app.ui.SheetRaised
import com.downshift.app.ui.Slate
import com.downshift.app.ui.garage.Plate
import com.downshift.app.ui.garage.dashedBorder
import com.downshift.app.ui.garage.rememberAssetImage

/**
 * The drive screen (design: Drive.dc.html, Drive2.dc.html; PRD feature D). The safe-driving ring with this trip's
 * points and the part it fills toward, the stopped engine render, why the engine sounds as it does, and a mute button
 * as wide as the screen. No rpm, speed or gear, no animation: values swap when they change. Text is 30 sp and up,
 * except the dashed demo elements (demo mode only).
 */
@Composable
fun DriveScreen(vm: TripViewModel = viewModel()) {
    val live by vm.live.collectAsStateWithLifecycle()
    val build by vm.build.collectAsStateWithLifecycle()
    val demoButtons by vm.demoButtons.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().background(Ground)) {
        if (maxWidth >= 1200.dp) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.width(840.dp).fillMaxHeight()
                        .drawBehind { drawRect(SheetRaised, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Dial(live, build, 680.dp)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 56.dp, vertical = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically),
                ) {
                    Box(Modifier.alpha(if (live.muted) 0.5f else 1f)) { Plate(build.plate, 40) }
                    Chip(live.situation, wide = true)
                    MuteButton(live.muted, wide = true) { vm.setMuted(!live.muted) }
                    DemoRow(live.lapse, demoButtons, wide = true, onArrive = vm::arrive)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.alpha(if (live.muted) 0.5f else 1f)) { Plate(build.plate, 34) }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Dial(live, build, 560.dp) }
                Chip(live.situation, wide = false)
                MuteButton(live.muted, wide = false) { vm.setMuted(!live.muted) }
                DemoRow(live.lapse, demoButtons, wide = false, onArrive = vm::arrive)
            }
        }
    }
}

/** The points ring with the engine render resting on it (design positions: 314 dp at (123, 56) of a 560 dp ring). */
@Composable
private fun Dial(live: DriveLive, build: BuildLook, size: Dp) {
    val k = size / 560.dp
    val hero = rememberAssetImage("engine3d/${build.variant}/hero.webp", (314 * k * 1.5f).toInt())
    val g = live.gauge
    Box(Modifier.size(size)) {
        Gauge(
            size = size, mode = GaugeMode.POINTS,
            value = { g.frac.toFloat() }, center = { g.center }, unit = "P · 이번 주행", caption = { g.caption },
            description = "안전 주행 포인트 ${g.center} P · 이번 주행, ${g.caption}",
        )
        if (hero != null) {
            Image(hero, contentDescription = null, modifier = Modifier.offset(123.dp * k, 56.dp * k).size(314.dp * k).alpha(if (live.muted) 0.35f else 0.92f))
        }
    }
}

/** Why the engine sounds as it does: plain when nothing is turned down, calm when something is, white when muted. */
@Composable
private fun Chip(s: Situation, wide: Boolean) {
    val text = when (s.reason) {
        Reason.NORMAL -> "엔진음 ${s.volumePct}%"
        Reason.OFF -> "엔진음 꺼짐"
        Reason.MUTED -> "음소거됨"
        Reason.GUIDANCE -> "안내 음성 · 잠시 줄임"
        Reason.CALL -> "통화 · 잠시 줄임"
        Reason.ALERT -> "알림음 · 잠시 줄임"
        Reason.SCHOOL_ZONE -> "스쿨존 · 엔진음 줄임"
        Reason.QUIET_ZONE -> "주거지 · 엔진음 줄임"
        Reason.PASSENGER -> "동승자 · 볼륨 ${s.volumePct}%"
    }
    val (fg, line, bg) = when (s.reason) {
        Reason.NORMAL, Reason.OFF -> Triple(Muted, RuleStrong, Color.Transparent)
        Reason.MUTED -> Triple(Ink, Slate, SheetRaised)
        else -> Triple(Calm, Calm, CalmSheet)
    }
    Row(
        Modifier.height(if (wide) 76.dp else 68.dp).clip(CircleShape).background(bg).border(2.dp, line, CircleShape)
            .padding(horizontal = if (wide) 32.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 14.dp),
    ) {
        Icon(AppIcons.Speaker, contentDescription = null, tint = fg, modifier = Modifier.size(if (wide) 36.dp else 32.dp))
        Text(text, color = fg, fontSize = if (wide) 32.sp else 30.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MuteButton(muted: Boolean, wide: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(if (wide) 32.dp else 28.dp)
    val label = if (muted) "소리 켜기" else "음소거"
    Row(
        Modifier.fillMaxWidth().height(if (wide) 200.dp else 152.dp).clip(shape).background(if (muted) Ink else SheetRaised)
            .border(2.dp, if (muted) Ink else Dim, shape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 20.dp, Alignment.CenterHorizontally),
    ) {
        val fg = if (muted) Ground else Ink
        Icon(if (muted) AppIcons.SoundOn else AppIcons.Mute, contentDescription = null, tint = fg, modifier = Modifier.size(if (wide) 60.dp else 52.dp))
        Text(label, color = fg, fontSize = if (wide) 44.sp else 40.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Demo mode only: the distance lapse whenever it is on (also with the buttons hidden, so a recording never hides it),
 * and the button that stops and shifts into park, as arriving would.
 */
@Composable
private fun DemoRow(lapse: Int, buttons: Boolean, wide: Boolean, onArrive: () -> Unit) {
    if (lapse <= 1 && !buttons) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (lapse > 1) {
            Box(Modifier.fillMaxWidth().height(48.dp).dashedBorder(2.dp, Slate, 14.dp), contentAlignment = Alignment.Center) {
                Text("시연 배속 ×$lapse", color = Muted, fontSize = 24.sp)
            }
        }
        if (buttons) {
            Box(
                Modifier.fillMaxWidth().height(if (wide) 72.dp else 64.dp).clip(RoundedCornerShape(18.dp)).dashedBorder(2.dp, Slate, 18.dp)
                    .clickable(role = Role.Button, onClick = onArrive),
                contentAlignment = Alignment.Center,
            ) {
                Text("시연용 · 기어 P · 도착", color = Muted, fontSize = if (wide) 24.sp else 22.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
