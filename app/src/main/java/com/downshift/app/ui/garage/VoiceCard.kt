package com.downshift.app.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.downshift.app.core.build.IntakePart
import com.downshift.app.core.garage.Category
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.voice.VoiceSlot
import com.downshift.app.ui.Dim
import com.downshift.app.ui.Heat
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.OnHeat
import com.downshift.app.ui.Rule
import com.downshift.app.ui.RuleStrong
import com.downshift.app.ui.Sheet
import com.downshift.app.ui.SheetRaised

internal fun voiceTab(c: Category) = when (c) {
    Category.ENGINE -> "엔진"
    Category.INTAKE -> "흡기"
    Category.EXHAUST -> "배기"
    Category.TRANSMISSION -> "변속기"
}

// One line each, like the part cards' (Korean wraps at any syllable, so a longer line leaves one alone below).
private fun voiceDesc(c: Category, intake: IntakePart) = when (c) {
    Category.ENGINE -> "엔진 소리를 내 목소리로"
    Category.INTAKE -> if (intake == IntakePart.NA) "터보나 슈퍼차저를 달면 들려요" else "휘슬과 블로오프를 내 목소리로"
    Category.EXHAUST -> "감속할 때 팝을 내 목소리로"
    Category.TRANSMISSION -> "변속할 때마다 내 목소리로"
}

/** Spec 4: nothing recorded, recorded but off, or on (on needs a take). */
internal enum class VoiceCardState { EMPTY, RECORDED, ON }

internal fun voiceState(ui: GarageUi, c: Category): VoiceCardState = when {
    VoiceSlot.of(c).none { it in ui.recorded } -> VoiceCardState.EMPTY
    c in ui.state.voiceOn -> VoiceCardState.ON
    else -> VoiceCardState.RECORDED
}

/** The last card of each part tab (spec 4): a mic tile, the name, what it does, its state; the round button plays it. */
@Composable
internal fun VoiceCard(ui: GarageUi, category: Category, wide: Boolean, vm: GarageViewModel, modifier: Modifier = Modifier) {
    val state = voiceState(ui, category)
    val selected = ui.selected[category] == GarageViewModel.VOICE_ID
    val playing = ui.playing == GarageViewModel.voiceId(category)
    val bg = if (selected) SheetRaised else Sheet
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.height(if (wide) 188.dp else 128.dp)) {
        Box(
            Modifier.fillMaxSize().clip(shape).background(bg).border(2.dp, if (selected) Heat else Rule, shape)
                .semantics { this.selected = selected }
                .clickable(role = Role.Button) { vm.selectVoice(category) },
        ) {
            Box(Modifier.width(if (wide) 200.dp else 186.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Icon(GarageIcons.Mic, contentDescription = null, tint = if (state == VoiceCardState.ON) Heat else Muted, modifier = Modifier.size(if (wide) 88.dp else 64.dp))
            }
            Column(
                Modifier.fillMaxSize().padding(start = if (wide) 196.dp else 182.dp, top = if (wide) 22.dp else 14.dp, end = 22.dp, bottom = if (wide) 20.dp else 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("내 목소리 ${voiceTab(category)}", color = Ink, fontSize = if (wide) 28.sp else 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                Text(voiceDesc(category, Garage.build(ui.state).intake), color = Muted, fontSize = if (wide) 22.sp else 20.sp, lineHeight = 1.35.em, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state == VoiceCardState.ON) Icon(GarageIcons.Check, contentDescription = null, tint = Heat, modifier = Modifier.size(22.dp))
                    val text = when (state) {
                        VoiceCardState.EMPTY -> "녹음해서 만들어요"
                        VoiceCardState.RECORDED -> "녹음됨"
                        VoiceCardState.ON -> "켜짐"
                    }
                    Text(text, color = if (state == VoiceCardState.ON) Heat else Muted, fontSize = 21.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        }
        val canPlay = state != VoiceCardState.EMPTY && ui.parked && ui.ready
        Box(
            Modifier.align(Alignment.BottomStart).padding(12.dp).size(64.dp).alpha(if (canPlay || playing) 1f else 0.35f).clip(CircleShape)
                .background(if (playing) Heat else Color(0xCC0C0D0F)).border(1.dp, Dim, CircleShape)
                .semantics { contentDescription = (if (playing) "정지: " else "미리 듣기: ") + "목소리 ${voiceTab(category)}" }
                .clickable(enabled = canPlay || playing, role = Role.Button) { vm.previewVoice(category) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) GarageIcons.Bars else GarageIcons.Play, contentDescription = null, tint = if (playing) OnHeat else Ink, modifier = Modifier.size(28.dp))
        }
    }
}

/** The action bar while a voice card is picked (spec 4): its state, 들어보기, 다시 녹음, and 녹음하기 / 켜기 / 끄기. */
@Composable
internal fun VoiceActionBar(ui: GarageUi, category: Category, wide: Boolean, vm: GarageViewModel, onRecord: (VoiceSlot) -> Unit) {
    val state = voiceState(ui, category)
    val parked = ui.parked
    val first = VoiceSlot.of(category).first()
    val line = when {
        !parked -> "정차 중에만 녹음하고 바꿀 수 있어요"
        state == VoiceCardState.EMPTY -> "정차 중에 녹음해서 만들어요"
        state == VoiceCardState.RECORDED -> "녹음됨 · 켜면 이 소리가 내 목소리로 바뀌어요"
        else -> "켜짐 · 장착한 부품 그대로, 소리만 내 목소리"
    }
    val playing = ui.playing == GarageViewModel.voiceId(category)
    Row(
        Modifier.fillMaxWidth().height(140.dp).drawBehind { drawRect(Rule, Offset.Zero, Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = if (wide) 40.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("내 목소리 ${voiceTab(category)}", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = if (state == VoiceCardState.ON && parked) Heat else Muted, fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BarButton(if (playing) "재생 중" else "들어보기", enabled = playing || (state != VoiceCardState.EMPTY && parked && ui.ready), filled = playing) { vm.previewVoice(category) }
            if (state != VoiceCardState.EMPTY) BarButton("다시 녹음", enabled = parked) { onRecord(first) }
            when (state) {
                VoiceCardState.EMPTY -> BarButton("녹음하기", enabled = parked, filled = true) { onRecord(first) }
                VoiceCardState.RECORDED -> BarButton("켜기", enabled = parked, filled = true) { vm.setVoiceOn(category, true) }
                VoiceCardState.ON -> BarButton("끄기", enabled = parked) { vm.setVoiceOn(category, false) }
            }
        }
    }
}

/** The bar and sheet buttons: an outline, or Heat-filled for the main move. */
@Composable
internal fun BarButton(label: String, enabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.height(80.dp).defaultMinSize(minWidth = 120.dp).alpha(if (enabled) 1f else 0.4f).clip(shape)
            .background(if (filled) Heat else Color.Transparent).border(if (filled) 2.dp else 1.dp, if (filled) Heat else RuleStrong, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) OnHeat else Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** "내 목소리" beside the build while any voice card is heard. */
@Composable
internal fun VoiceChip() {
    Row(
        Modifier.height(36.dp).clip(CircleShape).border(1.dp, Heat, CircleShape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(GarageIcons.Mic, contentDescription = null, tint = Heat, modifier = Modifier.size(20.dp))
        Text("내 목소리", color = Heat, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}
