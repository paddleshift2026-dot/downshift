package com.downshift.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.downshift.app.adapters.audio.FocusMode
import com.downshift.app.core.probe.ProbeRow
import com.downshift.app.core.probe.ProbeStatus


@Composable
fun ProbeScreen(vm: ProbeViewModel = viewModel()) {
    // The probes poll and play only while this tab is on screen.
    DisposableEffect(vm) {
        vm.activate()
        onDispose { vm.deactivate() }
    }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val audio by vm.audio.collectAsStateWithLifecycle()
    var throttle by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxSize().background(Ground).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Downshift M0 프로브", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(audio, color = Muted, fontSize = 18.sp)
        Text("가상 페달 ${"%.0f".format(throttle * 100)}%", color = Heat, fontSize = 22.sp)
        Slider(value = throttle, onValueChange = { throttle = it; vm.setThrottle(it.toDouble()) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusMode.entries.forEach { m -> OutlinedButton(onClick = { vm.setFocus(m) }) { Text("포커스 ${m.name}") } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.playGuidance() }) { Text("안내음 재생") }
            Button(onClick = { vm.requestRoute() }) { Text("서울역 경로 요청") }
            Button(onClick = { vm.writeReport() }) { Text("보고서 저장") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.id }) { r -> ProbeRowView(r) }
        }
    }
}

@Composable
private fun ProbeRowView(r: ProbeRow) {
    val color = when (r.status) {
        ProbeStatus.CHANGING -> Clean
        ProbeStatus.RECEIVED -> Ink
        ProbeStatus.WAITING -> Faint
        ProbeStatus.DENIED, ProbeStatus.UNSUPPORTED -> Alert
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(r.status.ko, color = color, fontSize = 18.sp, modifier = Modifier.width(110.dp))
        Text("${r.label} · ${r.source}", color = Ink, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(r.lastValue ?: (r.note ?: "-"), color = Muted, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Text("${r.count}", color = Muted, fontSize = 16.sp)
    }
}
