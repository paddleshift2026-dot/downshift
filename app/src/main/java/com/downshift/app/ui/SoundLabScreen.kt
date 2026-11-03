package com.downshift.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.build.ExhaustPart
import com.downshift.app.core.build.IntakePart
import com.downshift.app.core.build.TransmissionPart
import com.downshift.app.core.input.ThrottleSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Development screen for tuning the engine sound while parked: start, parts, sliders, a manual pedal and recording. */
@Composable
fun SoundLabScreen(vm: SoundLabViewModel = viewModel()) {
    val c by vm.config.collectAsStateWithLifecycle()
    val b = c.build
    Column(
        Modifier.fillMaxSize().background(Ground).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Gauges(vm, c)
        Choice("엔진", EngineId.entries, b.engine, { it.name }) { vm.setBuild(b.copy(engine = it)) }
        Choice("흡기", IntakePart.entries, b.intake, ::intakeLabel) { vm.setBuild(b.copy(intake = it)) }
        Choice("배기", ExhaustPart.entries, b.exhaust, ::exhaustLabel) { vm.setBuild(b.copy(exhaust = it)) }
        Choice("변속기", TransmissionPart.entries, b.transmission, ::transmissionLabel) { vm.setBuild(b.copy(transmission = it)) }
        LabeledSlider("팝콘 ${"%.0f".format(b.pops * 100)}%", b.pops.toFloat(), 0f..1f) { vm.setBuild(b.copy(pops = it.toDouble())) }
        LabeledSlider("레드라인 ${if (b.redlineOffset >= 0) "+" else ""}${b.redlineOffset} rpm", b.redlineOffset.toFloat(), -500f..500f, steps = 9) {
            vm.setBuild(b.copy(redlineOffset = (it / 100).toInt() * 100))
        }
        LabeledSlider("볼륨 ${"%.0f".format(b.volume * 100)}%", b.volume.toFloat(), 0f..1f) { vm.setBuild(b.copy(volume = it.toDouble())) }
        ManualPedal(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val rec = c.recording
            Button(
                onClick = { if (rec == null) vm.record(true, "lab_" + SimpleDateFormat("MMdd_HHmmss", Locale.ROOT).format(Date())) else vm.record(false) },
                colors = ButtonDefaults.buttonColors(containerColor = if (rec == null) Panel else Heat),
            ) { Text(if (rec == null) "녹음 시작" else "녹음 끝 ($rec)", color = Ink, fontSize = 18.sp) }
            Text(c.audioInfo, color = Faint, fontSize = 14.sp)
        }
    }
}

/**
 * The only part that changes five times a second. The live values are read in the draw phase, so an update
 * redraws this box without recomposing or laying out the screen (a full frame cost about 25 ms on the emulator).
 */
@Composable
private fun Gauges(vm: SoundLabViewModel, c: LabConfig) {
    val live = vm.live.collectAsStateWithLifecycle()
    val running by remember { derivedStateOf { live.value.telemetry.running } }
    val measurer = rememberTextMeasurer()
    val big = TextStyle(color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
    val line = TextStyle(color = Muted, fontSize = 18.sp)
    val small = TextStyle(color = Faint, fontSize = 16.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        HoldButton(label = if (running) "STOP" else "START", color = if (running) Faint else Heat, enabled = c.ready) {
            if (running) vm.stop() else vm.start()
        }
        Spacer(
            Modifier.fillMaxWidth().height(140.dp).drawBehind {
                val l = live.value
                val rpm = "%,.0f rpm".format(l.telemetry.rpm)
                drawText(measurer, rpm, style = if (l.telemetry.atLimiter) big.copy(color = Heat) else big)
                drawText(measurer, telemetryLine(l), Offset(0f, 58.dp.toPx()), style = line)
                drawText(measurer, sourceLine(l, c), Offset(0f, 86.dp.toPx()), style = small)
                if (l.latency.isNotEmpty()) drawText(measurer, l.latency, Offset(0f, 112.dp.toPx()), style = small)
            },
        )
    }
}

private fun telemetryLine(s: LabLive): String {
    val t = s.telemetry
    val gear = when {
        !t.running -> "-"
        t.gear > 0 -> "${t.gear}단"
        t.gear < 0 -> "R"
        else -> "P/N"
    }
    val boost = if (t.boost > 0.01) " · 부스트 %.2f bar".format(t.boost) else ""
    return "$gear · 부하 ${"%.0f".format(t.load * 100)}%$boost · ${"%.0f".format(s.speedKph)} km/h${if (t.overrun) " · 오버런" else ""}"
}

private fun sourceLine(s: LabLive, c: LabConfig): String {
    val src = when {
        s.manual -> "화면 페달"
        s.source == ThrottleSource.CONSOLE -> "콘솔 페달"
        s.source == ThrottleSource.MANUAL -> "adb 페달"
        else -> "차속 추정"
    }
    val console = if (c.consoleConnected) "콘솔 연결" else "콘솔 없음"
    return "입력 $src ${"%.0f".format(s.throttle * 100)}% · $console · 팩 ${c.packSource} · 연료 ${c.fuel}"
}

private fun intakeLabel(p: IntakePart) = when (p) {
    IntakePart.NA -> "자연흡기"
    IntakePart.TURBO1 -> "터보 1"
    IntakePart.TURBO2 -> "터보 2"
    IntakePart.TURBO3 -> "터보 3"
    IntakePart.SUPERCHARGER -> "슈퍼차저"
}

private fun exhaustLabel(p: ExhaustPart) = when (p) {
    ExhaustPart.STOCK -> "순정"
    ExhaustPart.SPORT -> "스포츠"
    ExhaustPart.TRACK -> "트랙"
}

private fun transmissionLabel(p: TransmissionPart) = when (p) {
    TransmissionPart.AT -> "자동"
    TransmissionPart.DCT -> "DCT"
    TransmissionPart.MT_BLIP -> "수동 블립"
}

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Muted, fontSize = 18.sp, modifier = Modifier.width(90.dp))
        options.forEach { o ->
            if (o == selected) {
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Heat)) { Text(label(o), color = Ground, fontSize = 18.sp) }
            } else {
                OutlinedButton(onClick = { onPick(o) }) { Text(label(o), color = Ink, fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun LabeledSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Muted, fontSize = 18.sp, modifier = Modifier.width(220.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, colors = heatSlider(), modifier = Modifier.weight(1f))
    }
}

/** Held while dragging; letting go hands the pedal back to the console or the estimate. */
@Composable
private fun ManualPedal(vm: SoundLabViewModel) {
    var v by remember { mutableFloatStateOf(0f) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("화면 페달 ${"%.0f".format(v * 100)}%", color = Heat, fontSize = 18.sp, modifier = Modifier.width(220.dp))
        Slider(
            value = v,
            onValueChange = { v = it; vm.setManualThrottle(it.toDouble()) },
            onValueChangeFinished = { v = 0f; vm.setManualThrottle(null) },
            colors = heatSlider(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun heatSlider() = SliderDefaults.colors(thumbColor = Heat, activeTrackColor = Heat, inactiveTrackColor = Panel)

/** Fires once the press has been held for HOLD_MS, like the ignition in the design. */
@Composable
private fun HoldButton(label: String, color: Color, enabled: Boolean, onHeld: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val held by rememberUpdatedState(onHeld)
    val on by rememberUpdatedState(enabled)
    Box(
        Modifier.size(width = 200.dp, height = 88.dp).clip(RoundedCornerShape(44.dp)).background(color.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (!on) return@awaitEachGesture
                    val job = scope.launch {
                        val start = System.nanoTime()
                        while (progress < 1f) {
                            progress = ((System.nanoTime() - start) / (HOLD_MS * 1e6)).toFloat().coerceAtMost(1f)
                            delay(16)
                        }
                        held()
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    progress = 0f
                }
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(color.copy(alpha = 0.6f)))
        Text(label, Modifier.align(Alignment.Center), color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

private const val HOLD_MS = 900L
