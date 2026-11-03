package com.downshift.app.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.Tune
import com.downshift.app.ui.AppIcons
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.Rule
import com.downshift.app.ui.RuleStrong
import com.downshift.app.ui.Sheet
import com.downshift.app.ui.SheetRaised

/**
 * The 세팅 tab: pops, redline (shown as the fitted engine's absolute rpm) and volume in steps with −/+ buttons,
 * applied at once; then the two demo buttons. Changes need the car parked, like the rest of the garage.
 */
@Composable
internal fun TuneTab(ui: GarageUi, vm: GarageViewModel) {
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
        Spacer(Modifier.height(8.dp))
        DemoButtons(vm)
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
