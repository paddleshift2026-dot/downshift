package com.downshift.app.ui.start

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.downshift.app.ui.AppIcons
import com.downshift.app.ui.Clean
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Dim
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Gauge
import com.downshift.app.ui.GaugeMode
import com.downshift.app.ui.Ground
import com.downshift.app.ui.Heat
import com.downshift.app.ui.Muted
import com.downshift.app.ui.OnHeat
import com.downshift.app.ui.Rule
import com.downshift.app.ui.ScreenHeader
import com.downshift.app.ui.Sheet
import com.downshift.app.ui.Slate
import com.downshift.app.ui.garage.Plate
import com.downshift.app.ui.garage.dashedBorder
import com.downshift.app.ui.garage.points
import com.downshift.app.ui.garage.rememberAssetImage
import com.downshift.app.ui.holdGesture
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The start screen (design: Start.dc.html). The rpm dial with the build's engine on it, the plate and firing order, and
 * the 0.9 s START ring; once running, STOP, and the demo buttons for the console pedal and gear D. Two columns from
 * 1,200 dp, one below. The rpm is read while drawing; the engine's little kick, tilt and glow run only while parked here.
 */
@Composable
fun StartScreen(vm: StartViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().background(Ground)) {
        val wide = maxWidth >= 1200.dp
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("시동", wide, onBack = vm::back)
            if (wide) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(96.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dial(ui, vm, 600.dp)
                    Controls(ui, vm)
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Dial(ui, vm, 440.dp)
                    Controls(ui, vm)
                }
            }
        }
    }
}

/** The rpm dial with the engine render on it: a sweep on ignition, a kick, a tilt and a glow while revving. */
@Composable
private fun Dial(ui: StartUi, vm: StartViewModel, size: Dp) {
    val rpm = vm.rpm.collectAsStateWithLifecycle()
    val ignitions by vm.ignitions.collectAsStateWithLifecycle()
    val sweep = remember { Animatable(0f) }
    val kick = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(ignitions) {
        if (ignitions == 0) return@LaunchedEffect
        kick.animateTo(-4f, tween(140, easing = LinearOutSlowInEasing))
        kick.animateTo(0f, tween(650, easing = Overshoot))
    }
    LaunchedEffect(ignitions) {
        if (ignitions == 0) return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(700))
        sweep.animateTo(0f, tween(650))
    }
    var wasRevving by remember { mutableStateOf(false) }
    LaunchedEffect(ui.revving) {
        if (wasRevving && !ui.revving) {
            pop.snapTo(1f)
            delay(60)
            pop.animateTo(0f, tween(450))
        }
        wasRevving = ui.revving
    }
    val tilt by animateFloatAsState(if (ui.revving) -3f else 0f, tween(if (ui.revving) 520 else 650), label = "tilt")
    val glow by animateFloatAsState(if (!ui.on) 0f else if (ui.revving) 0.55f else 0.18f, tween(if (ui.revving) 500 else 700), label = "glow")

    val art = size * 0.56f
    val artLeft = size * 0.22f
    val artTop = size * 0.10f
    val hero = rememberAssetImage("engine3d/${ui.variant}/hero.webp", (art.value * 1.5f).roundToInt())
    Box(Modifier.size(size)) {
        Gauge(
            size = size, mode = GaugeMode.RPM, off = !ui.on,
            value = { maxOf(sweep.value, rpm.value.toFloat() / ui.fullScale) },
            center = { if (ui.on) points(rpm.value) else "—" },
            unit = "RPM",
            caption = { if (!ui.on) "" else if (ui.revving) "레브" else "공회전" },
            description = if (ui.on) "엔진 회전 게이지" else "엔진 꺼짐",
            redline = ui.redline,
            labels = ui.fullScale / 1000,
        )
        Box(
            Modifier.offset(size * 0.26f, size * 0.34f).size(size * 0.48f, size * 0.24f)
                .graphicsLayer { alpha = glow }
                .background(Brush.radialGradient(listOf(Heat.copy(alpha = 0.5f), Heat.copy(alpha = 0f)))),
        )
        if (hero != null) {
            Image(
                hero, contentDescription = null,
                modifier = Modifier.offset(artLeft, artTop).size(art).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.78f)
                    rotationZ = kick.value + tilt
                    translationY = if (ui.revving) -3.dp.toPx() * (tilt / -3f) else 0f
                    alpha = if (ui.on) 1f else 0.55f
                },
            )
        }
        // The backfire flash at the exhaust when a rev is let go.
        Box(
            Modifier.offset(artLeft + art * 0.26f - 15.dp, artTop + art * 0.8f - 15.dp).size(30.dp)
                .graphicsLayer { alpha = pop.value }
                .background(Brush.radialGradient(listOf(Color(0xFFFFE2C8), Heat.copy(alpha = 0.6f), Heat.copy(alpha = 0f)))),
        )
    }
}

@Composable
private fun Controls(ui: StartUi, vm: StartViewModel) {
    var holding by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val ignitions by vm.ignitions.collectAsStateWithLifecycle()
    LaunchedEffect(ignitions) {
        if (ignitions == 0) return@LaunchedEffect
        starting = true
        delay(760)
        starting = false
    }
    val status = when {
        !ui.permitted -> "차량 신호 권한이 없어 미리 듣기만 돼요"
        ui.failed -> "엔진음을 재생할 수 없어요"
        !ui.ready -> "엔진음을 준비하고 있어요"
        ui.on && starting -> "시동 중"
        ui.on && ui.revving -> "손을 떼면 회전이 떨어지며 팝콘"
        ui.on -> "D로 바꾸면 주행 화면으로 바뀌어요"
        holding -> "계속 누르고 있어요"
        else -> "START를 길게 눌러 시동을 걸어요"
    }
    Column(Modifier.width(560.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Plate(ui.plate, 28)
        Text(ui.engineLine, color = Faint, fontSize = 20.sp, maxLines = 1)
        Text(status, Modifier.height(34.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = Muted, fontSize = 24.sp)
        if (ui.on) {
            StopButton(vm::stop)
        } else {
            StartRing(enabled = ui.permitted && ui.ready, onHeld = vm::start, onPressed = { holding = it })
        }
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.on) {
                DemoPedal(Modifier.weight(1f), vm::demoPedal)
                Box(
                    Modifier.width(168.dp).height(84.dp).clip(RoundedCornerShape(18.dp)).dashedBorder(2.dp, Clean, 18.dp)
                        .clickable(role = Role.Button, onClick = vm::demoDrive),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("시연용 · 기어 D", color = Clean, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The START ring: a 260 dp track that fills with heat over the 0.9 s hold, around the ENGINE START STOP button. */
@Composable
private fun StartRing(enabled: Boolean, onHeld: () -> Unit, onPressed: (Boolean) -> Unit) {
    val context = LocalContext.current
    val progress = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val held by rememberUpdatedState(onHeld)
    val pressed by rememberUpdatedState(onPressed)
    val on by rememberUpdatedState(enabled)
    Box(
        Modifier.size(260.dp).graphicsLayer().drawBehind {
            val stroke = 8.dp.toPx()
            val r = 110.dp.toPx()
            val topLeft = Offset(center.x - r, center.y - r)
            drawArc(Rule, 0f, 360f, useCenter = false, topLeft = topLeft, size = Size(2 * r, 2 * r), style = Stroke(stroke))
            if (progress.floatValue > 0f) {
                drawArc(Heat, -90f, 360f * progress.floatValue, useCenter = false, topLeft = topLeft, size = Size(2 * r, 2 * r), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(200.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }.clip(CircleShape).background(Sheet).border(2.dp, Dim, CircleShape)
                .semantics {
                    contentDescription = "시동 걸기 — 길게 누르세요"
                    role = Role.Button
                }
                .holdGesture(
                    scope, progress, enabled = { on }, onHeld = { held() },
                    onShortPress = { Toast.makeText(context, "길게 누르면 시동이 걸려요", Toast.LENGTH_SHORT).show() },
                    onPressed = { pressed(it) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(AppIcons.Power, contentDescription = null, tint = Heat, modifier = Modifier.size(40.dp))
                Text("ENGINE", color = Muted, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.2.em)
                Text("START", color = Heat, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 46.sp, letterSpacing = 0.06.em)
                Text("STOP", color = Muted, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.2.em)
            }
        }
    }
}

/** STOP while running: the heat button with a dark halo; a tap stops the engine. */
@Composable
private fun StopButton(onStop: () -> Unit) {
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(224.dp).clip(CircleShape).background(StopHalo).padding(12.dp).clip(CircleShape).background(Heat)
                .semantics { contentDescription = "시동 끄기" }
                .clickable(role = Role.Button, onClick = onStop),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(AppIcons.Power, contentDescription = null, tint = OnHeat, modifier = Modifier.size(40.dp))
                Text("ENGINE", color = StopInk, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.2.em)
                Text("STOP", color = OnHeat, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 46.sp, letterSpacing = 0.06.em)
                Text("눌러서 끄기", color = StopInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Demo: presses the accelerator while held (the console's on the emulator), lets it go on release. */
@Composable
private fun DemoPedal(modifier: Modifier, onPedal: (Boolean) -> Unit) {
    var down by remember { mutableStateOf(false) }
    val press by rememberUpdatedState(onPedal)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.height(84.dp).clip(shape).background(if (down) RevSheet else Color.Transparent).dashedBorder(2.dp, if (down) Heat else Slate, 18.dp)
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    down = true
                    press(true)
                    waitForUpOrCancellation()
                    down = false
                    press(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("시연용 · 가속 페달 누르기", color = if (down) Heat else Muted, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val StopHalo = Color(0xFF2A1A10)
private val StopInk = Color(0xFF5A3216)
private val RevSheet = Color(0xFF22160E)
