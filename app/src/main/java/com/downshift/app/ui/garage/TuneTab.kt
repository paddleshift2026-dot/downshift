package com.downshift.app.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.Tune
import com.downshift.app.ui.Alert
import com.downshift.app.ui.AppIcons
import com.downshift.app.ui.Clean
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.Rule
import com.downshift.app.ui.RuleStrong
import com.downshift.app.ui.Sheet
import com.downshift.app.ui.SheetRaised
import com.downshift.app.ui.Slate
import kotlinx.coroutines.delay

/**
 * The 세팅 tab: pops, redline (shown as the fitted engine's absolute rpm) and volume in steps with −/+ buttons,
 * applied at once; then the safety note, demo mode and, while it is on, the demo tools. Changes need the car parked,
 * like the rest of the garage.
 */
@Composable
internal fun TuneTab(ui: GarageUi, vm: GarageViewModel) {
    val demoOn by vm.demoOn.collectAsStateWithLifecycle()
    val t = ui.state.tune
    val redline = Engines.spec(Garage.build(ui.state).engine).redlineRpm.toInt()
    val parked = ui.parked
    fun set(next: Tune) = vm.setTune(next)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TuneRow(
            "팝콘 강도", "감속할 때 터지는 소리", "${t.pops} / ${Garage.MAX_POPS}", parked,
            canDec = t.pops > 0, canInc = t.pops < Garage.MAX_POPS,
            onDec = { set(t.copy(pops = t.pops - 1)) }, onInc = { set(t.copy(pops = t.pops + 1)) },
        )
        TuneRow(
            "레드라인", "변속 직전 최고 회전", "${points(redline + t.redlineOffset)} RPM", parked,
            canDec = t.redlineOffset > -Garage.MAX_REDLINE_OFFSET, canInc = t.redlineOffset < Garage.MAX_REDLINE_OFFSET,
            onDec = { set(t.copy(redlineOffset = t.redlineOffset - REDLINE_STEP)) }, onInc = { set(t.copy(redlineOffset = t.redlineOffset + REDLINE_STEP)) },
        )
        TuneRow(
            "볼륨", "주행 중엔 큰 음소거 버튼만 보여요", "${t.volume}%", parked,
            canDec = t.volume > 0, canInc = t.volume < 100,
            onDec = { set(t.copy(volume = t.volume - VOLUME_STEP)) }, onInc = { set(t.copy(volume = t.volume + VOLUME_STEP)) },
        )
        SafetyNoteRow(vm)
        DemoModeRow(demoOn, parked, vm)
        if (demoOn) DemoSection(vm)
    }
}

/**
 * Demo mode (PRD feature G): the demo wallet and the demo tools. The switch is refused while a trip is being recorded,
 * and the row says so for a few seconds.
 */
@Composable
private fun DemoModeRow(on: Boolean, parked: Boolean, vm: GarageViewModel) {
    var refused by remember { mutableStateOf(false) }
    LaunchedEffect(refused) {
        if (refused) {
            delay(REFUSED_MS)
            refused = false
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 88.dp).alpha(if (parked) 1f else 0.35f).clip(shape).border(1.dp, Rule, shape)
            .toggleable(value = on, enabled = parked, role = Role.Switch) { if (!vm.setDemo(it)) refused = true }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("시연 모드", color = Ink, fontSize = 24.sp)
            Text(
                if (refused) "주행이 끝난 뒤 바꿀 수 있어요" else "공모전 시연용 · 포인트는 따로 쌓여요",
                Modifier.padding(top = 4.dp), color = if (refused) Alert else Muted, fontSize = 18.sp,
            )
        }
        OnOff(on)
    }
}

/** What demo mode brings: the dashed buttons on the screens, +1,000 P and the demo state, the distance lapse. */
@Composable
private fun DemoSection(vm: GarageViewModel) {
    val buttons by vm.demoButtons.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().dashedBorder(2.dp, Slate, 18.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = buttons, role = Role.Switch) { vm.setDemoButtons(it) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("화면에 시연 버튼 보이기", Modifier.weight(1f), color = Ink, fontSize = 22.sp)
            OnOff(buttons)
        }
        DemoButtons(vm)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("시연 배속", Modifier.weight(1f), color = Ink, fontSize = 22.sp)
                LAPSES.forEach { x -> LapseChip(x, live.lapse == x) { vm.setLapse(x) } }
            }
            Text(
                if (live.lapse > 1) "주행 거리를 ${live.lapse}배로 셉니다 · 주행계는 쓰지 않아요" else "실제 주행 거리로 셉니다",
                Modifier.padding(top = 8.dp), color = Muted, fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun LapseChip(x: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.height(56.dp).defaultMinSize(minWidth = 88.dp).clip(shape).border(2.dp, if (selected) Clean else Rule, shape)
            .semantics { contentDescription = "시연 배속 ${x}배" }
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("×$x", color = if (selected) Clean else Muted, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    }
}

/** A plain on/off switch in the design's colors; the row it sits in takes the tap. */
@Composable
private fun OnOff(on: Boolean) {
    Box(
        Modifier.size(width = 72.dp, height = 40.dp).clip(CircleShape).background(if (on) Clean else Rule).padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Ink))
    }
}

/** The first-run safety note, to read again (PRD feature F: "설정에서 다시 볼 수 있어요"). */
@Composable
private fun SafetyNoteRow(vm: GarageViewModel) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().height(72.dp).clip(shape).border(1.dp, Rule, shape)
            .clickable(role = Role.Button, onClick = vm::reviewSafety).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("안전 안내 다시 보기", Modifier.weight(1f), color = Ink, fontSize = 24.sp)
        Icon(AppIcons.Forward, contentDescription = null, tint = Muted, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun TuneRow(
    label: String, hint: String, value: String, parked: Boolean,
    canDec: Boolean, canInc: Boolean, onDec: () -> Unit, onInc: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Sheet).border(1.dp, Rule, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(hint, color = Muted, fontSize = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StepButton("−", "$label 낮추기", parked && canDec, onDec)
            Text(
                value, Modifier.defaultMinSize(minWidth = 168.dp), textAlign = TextAlign.Center,
                color = Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 40.sp,
            )
            StepButton("+", "$label 높이기", parked && canInc, onInc)
        }
    }
}

@Composable
private fun StepButton(sign: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.size(80.dp).alpha(if (enabled) 1f else 0.35f).clip(shape).background(SheetRaised).border(1.dp, RuleStrong, shape)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, color = Ink, fontSize = 40.sp)
    }
}

private const val REDLINE_STEP = 100
private const val VOLUME_STEP = 10
private const val REFUSED_MS = 3000L
private val LAPSES = listOf(1, 10, 20)
