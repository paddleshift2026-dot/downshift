package com.downshift.app.ui.garage

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downshift.app.core.voice.VoiceSlot
import com.downshift.app.core.voice.VoiceVerdict
import com.downshift.app.ui.Alert
import com.downshift.app.ui.Clean
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Ground
import com.downshift.app.ui.Heat
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.OnHeat
import com.downshift.app.ui.Rule
import com.downshift.app.ui.Sheet

internal fun verdictLine(v: VoiceVerdict?) = when (v) {
    VoiceVerdict.OK -> "잘 녹음됐어요"
    VoiceVerdict.SILENT -> "소리가 안 들렸어요. 조금 더 가까이에서 해 볼까요?"
    VoiceVerdict.QUIET -> "조금 더 크게 해 볼까요?"
    VoiceVerdict.LOUD -> "조금 작게 해 볼까요?"
    VoiceVerdict.SHORT -> "2초 동안 길게 이어서 해 주세요"
    null -> ""
}

/** Over the whole garage while a take is made (spec 5); asks for RECORD_AUDIO the first time 녹음 시작 is tapped. */
@Composable
internal fun VoiceRecordSheet(rec: VoiceViewModel, parked: Boolean) {
    val ui by rec.ui.collectAsStateWithLifecycle()
    val slot = ui.slot ?: return
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) rec.record() else rec.permissionDenied()
    }
    val start: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) rec.record()
        else ask.launch(Manifest.permission.RECORD_AUDIO)
    }
    Box(Modifier.fillMaxSize().background(Ground).clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${slot.title} 녹음", Modifier.weight(1f), color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                BarButton("닫기", enabled = true, onClick = rec::close)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Sheet).border(1.dp, Rule, RoundedCornerShape(20.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(slot.how, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("쓰이는 곳: ${slot.use}", color = Muted, fontSize = 22.sp)
                Text("마이크 쪽을 보고 · 너무 크지 않게 · 주변을 조용히", color = Faint, fontSize = 22.sp)
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when (ui.phase) {
                    TakePhase.GUIDE -> RecordDot(enabled = parked, onClick = start)
                    TakePhase.COUNTDOWN -> Text("${ui.count}", color = Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 160.sp)
                    TakePhase.RECORDING -> Level(ui.level)
                    TakePhase.CHECKED -> Checked(ui, rec, parked, start)
                    TakePhase.FAILED -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        Text(ui.failure ?: VoiceViewModel.MIC_BUSY, color = Alert, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        BarButton("다시 해 보기", enabled = parked, filled = true, onClick = start)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (parked) "녹음하는 동안 엔진은 꺼져요" else "정차 중에만 녹음할 수 있어요", Modifier.weight(1f), color = Muted, fontSize = 22.sp)
                if (slot == VoiceSlot.BLOW_OFF) BarButton("건너뛰기", enabled = true, onClick = rec::skip)
            }
        }
    }
}

/** The circle and its label are one button, so a tap on the words starts the take too. */
@Composable
private fun RecordDot(enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.alpha(if (enabled) 1f else 0.35f).clip(RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(180.dp).clip(CircleShape).background(Heat), contentAlignment = Alignment.Center) {
            Icon(GarageIcons.Mic, contentDescription = null, tint = OnHeat, modifier = Modifier.size(84.dp))
        }
        Text("녹음 시작", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Level(level: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("녹음 중…", color = Heat, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.width(720.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(Sheet).border(1.dp, Rule, RoundedCornerShape(14.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f)).background(if (level > 0.9f) Alert else Clean))
        }
    }
}

@Composable
private fun Checked(ui: TakeUi, rec: VoiceViewModel, parked: Boolean, again: () -> Unit) {
    val ok = ui.verdict == VoiceVerdict.OK
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Text(verdictLine(ui.verdict), color = if (ok) Clean else Alert, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (ok) {
                BarButton(if (ui.playing) "재생 중" else "들어보기", enabled = parked || ui.playing, filled = ui.playing, onClick = rec::listen)
                BarButton("이걸로 쓸게요", enabled = true, filled = true, onClick = rec::keep)
            }
            BarButton("다시 녹음", enabled = parked, filled = !ok, onClick = again)
        }
    }
}
