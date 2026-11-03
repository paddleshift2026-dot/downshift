package com.downshift.app.ui.garage

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.downshift.app.core.build.Build
import com.downshift.app.core.build.EngineId
import com.downshift.app.core.engine.Engines
import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.Category
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.Part
import com.downshift.app.core.garage.PartKind
import com.downshift.app.core.garage.PartStatus
import com.downshift.app.core.garage.SoundCharacter
import com.downshift.app.ui.Clean
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Dim
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Gold
import com.downshift.app.ui.Ground
import com.downshift.app.ui.Heat
import com.downshift.app.ui.Ink
import com.downshift.app.ui.lineIcon
import com.downshift.app.ui.Muted
import com.downshift.app.ui.OnClean
import com.downshift.app.ui.OnHeat
import com.downshift.app.ui.Rule
import com.downshift.app.ui.RuleStrong
import com.downshift.app.ui.Sheet
import com.downshift.app.ui.SheetRaised
import com.downshift.app.ui.Slate
import java.util.Locale

/**
 * The garage (design: Main.dc.html). Two columns from 1,200 dp (the car screen): the build on the left, the parts on
 * the right; one column below that. Everything here changes only on a tap or a parked/moving switch; the engine's rpm
 * is the one live value and is read while drawing.
 */
@Composable
fun GarageScreen(vm: GarageViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().background(Ground)) {
        val wide = maxWidth >= 1200.dp
        Column(Modifier.fillMaxSize()) {
            Header(ui.state.balance, ui.parked, wide)
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    BuildColumn(ui, vm, Modifier.width(600.dp).fillMaxHeight())
                    PartsSection(ui, vm, wide = true, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    BuildStrip(ui, vm)
                    PartsSection(ui, vm, wide = false, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Header(balance: Int, parked: Boolean, wide: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(104.dp).bottomRule().padding(horizontal = if (wide) 48.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("개러지", color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.height(44.dp).border(1.dp, RuleStrong, CircleShape).padding(start = 7.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(if (parked) Ink else Heat), contentAlignment = Alignment.Center) {
                    Text(if (parked) "P" else "D", color = Ground, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
                Text(if (parked) "정차 중" else "주행 중", color = Muted, fontSize = 20.sp)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("안전 주행 포인트", color = Faint, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(points(balance), color = Clean, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, lineHeight = 46.sp)
                Text("P", color = Clean, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
            }
        }
    }
}

/** The engine shown on the stage: the picked engine in the engine tab, otherwise the fitted one. */
internal fun shownEngine(ui: GarageUi): Part {
    val picked = ui.selectedPart?.takeIf { it.category == Category.ENGINE && it.kind == PartKind.NORMAL }
    return picked ?: Catalog.part(ui.state.engine)!!
}

/** The picked part when it is a real part that is not fitted: the build then shows what it would sound like. */
internal fun previewedPart(ui: GarageUi): Part? =
    ui.selectedPart?.takeIf { it.kind == PartKind.NORMAL && it.id != Garage.fitted(ui.state, it.category) }

@Composable
private fun BuildColumn(ui: GarageUi, vm: GarageViewModel, modifier: Modifier) {
    val preview = previewedPart(ui)
    val shown: Build = if (preview != null) Garage.preview(ui.state, preview.id) else Garage.build(ui.state)
    Column(
        modifier.rightRule().padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("내 빌드", color = Faint, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            if (preview != null) Text("선택한 부품으로 미리보기", color = Heat, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Plate(Garage.plate(shown), 32)
        EngineStage(ui, wide = true)
        EngineName(shownEngine(ui))
        SoundCharacterBars(SoundCharacter.of(ui.state), SoundCharacter.of(ui.state, preview?.id), wide = true)
        Spacer(Modifier.weight(1f))
        StartButton(ui, vm, 96.dp)
    }
}

/** One column: the stage beside the plate, the bars and the start button, above the parts. */
@Composable
private fun BuildStrip(ui: GarageUi, vm: GarageViewModel) {
    val preview = previewedPart(ui)
    val shown = if (preview != null) Garage.preview(ui.state, preview.id) else Garage.build(ui.state)
    Row(Modifier.fillMaxWidth().bottomRule().padding(horizontal = 32.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        EngineStage(ui, wide = false)
        Column(Modifier.weight(1f).height(290.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Plate(Garage.plate(shown), 26)
            SoundCharacterBars(SoundCharacter.of(ui.state), SoundCharacter.of(ui.state, preview?.id), wide = false)
            Spacer(Modifier.weight(1f))
            StartButton(ui, vm, 72.dp)
        }
    }
}

/** The build's name tag, shrunk to fit on one line when a long build would overflow it. */
@Composable
internal fun Plate(text: String, fontSize: Int) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sheet).border(1.dp, RuleStrong, RoundedCornerShape(14.dp))
            .drawBehind {
                // The four screw heads of a licence plate.
                val r = 3.dp.toPx()
                val m = 9.dp.toPx() + r
                listOf(Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m))
                    .forEach { drawCircle(Dim, r, it) }
            }
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        val style = TextStyle(color = Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = fontSize.sp, letterSpacing = 0.06.em)
        val room = constraints.maxWidth
        val needed = remember(text, style) { measurer.measure(text, style, softWrap = false).size.width }
        val fitted = if (needed <= room) style else style.copy(fontSize = (fontSize * room.toFloat() / needed).sp)
        Text(text, style = fitted, maxLines = 1, softWrap = false)
    }
}

internal val StageEdge = Color(0xFF1F2227)
internal val StageBackdrop = Brush.radialGradient(
    0f to Color(0xFF2C3036), 0.55f to Color(0xFF1A1C20), 1f to Color(0xFF131417),
    center = Offset(151f, 58f), radius = 520f,
)

internal fun engineLabel(engine: Part) = when (engine.id) {
    "i4" -> "직렬 4기통"
    "v8" -> "V8 · 8기통"
    "v12" -> "V12 · 12기통"
    else -> engine.name
}

@Composable
private fun EngineName(engine: Part) {
    val id: EngineId = Catalog.apply(Build(), engine).engine
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(engineLabel(engine), color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("점화 순서 ", color = Faint, fontSize = 20.sp)
            Text(Engines.spec(id).firingOrder, color = Muted, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 0.03.em)
        }
    }
}

/**
 * Opens the start screen, where START is held to start this build (the design's 시동 button). While the engine runs it
 * says so, with the rpm drawn beside the label.
 */
@Composable
internal fun StartButton(ui: GarageUi, vm: GarageViewModel, height: Dp) {
    val rpm = vm.rpm.collectAsStateWithLifecycle()
    val measurer = rememberTextMeasurer()
    val rpmStyle = TextStyle(color = Muted, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
    val on = ui.engineOn
    val shape = RoundedCornerShape(if (height > 80.dp) 20.dp else 16.dp)
    Box(
        Modifier.fillMaxWidth().height(height).clip(shape).background(if (on) SheetRaised else Heat)
            .then(if (on) Modifier.border(1.dp, RuleStrong, shape) else Modifier)
            .clickable(role = Role.Button, onClick = vm::openStart),
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(GarageIcons.Power, contentDescription = null, tint = if (on) Ink else OnHeat, modifier = Modifier.size(32.dp))
            Text(if (on) "시동 켜짐" else "이 빌드로 시동", color = if (on) Ink else OnHeat, fontSize = if (height > 80.dp) 30.sp else 26.sp, fontWeight = FontWeight.Bold)
        }
        if (on) {
            Spacer(
                Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).size(150.dp, 40.dp).graphicsLayer().drawBehind {
                    val layout = measurer.measure(String.format(Locale.ROOT, "%,d RPM", rpm.value), rpmStyle)
                    drawText(layout, topLeft = Offset(size.width - layout.size.width, (size.height - layout.size.height) / 2))
                },
            )
        }
    }
}

@Composable
private fun PartsSection(ui: GarageUi, vm: GarageViewModel, wide: Boolean, modifier: Modifier) {
    Column(modifier) {
        Tabs(ui.tab, wide, vm::selectTab)
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(if (wide) PaddingValues(horizontal = 40.dp, vertical = 32.dp) else PaddingValues(horizontal = 32.dp, vertical = 20.dp)),
        ) {
            val category = ui.tab.category
            if (category == null) TuneTab(ui, vm) else PartsGrid(ui, category, wide, vm)
        }
        ActionBar(ui, wide, vm)
    }
}

private val TAB_LABELS = mapOf(
    GarageTab.ENGINE to "엔진", GarageTab.INTAKE to "흡기", GarageTab.EXHAUST to "배기", GarageTab.TRANSMISSION to "변속기", GarageTab.TUNE to "세팅",
)

@Composable
private fun Tabs(tab: GarageTab, wide: Boolean, onPick: (GarageTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(96.dp).bottomRule().padding(horizontal = if (wide) 40.dp else 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GarageTab.entries.forEach { t ->
            val on = t == tab
            Box(
                Modifier.then(if (wide) Modifier else Modifier.weight(1f)).defaultMinSize(minWidth = 104.dp).fillMaxHeight()
                    .drawBehind { if (on) drawRect(Heat, Offset(0f, size.height - 4.dp.toPx()), Size(size.width, 4.dp.toPx())) }
                    .semantics { selected = on }
                    .clickable(role = Role.Tab) { onPick(t) }
                    .padding(start = 22.dp, end = 22.dp, top = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(TAB_LABELS.getValue(t), color = if (on) Ink else Faint, fontSize = 28.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PartsGrid(ui: GarageUi, category: Category, wide: Boolean, vm: GarageViewModel) {
    val cols = if (wide) 2 else 1
    val gap = if (wide) 20.dp else 12.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Catalog.of(category).chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { p ->
                    PartCard(
                        part = p, status = Garage.status(ui.state, p), balance = ui.state.balance,
                        selected = ui.selected[category] == p.id, playing = ui.playing == p.id, canPreview = ui.parked && ui.ready,
                        wide = wide, onPick = { vm.selectPart(p.id) }, onPlay = { vm.preview(p.id) }, modifier = Modifier.weight(1f),
                    )
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun DemoButtons(vm: GarageViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            Triple("시연용 · 포인트 +1,000", Clean, { vm.demoAddPoints() }),
            Triple("시연용 · 처음 상태로", Slate, { vm.demoReset() }),
        ).forEach { (label, color, onClick) ->
            Box(
                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(18.dp)).dashedBorder(2.dp, color, 18.dp)
                    .clickable(role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (color == Clean) Clean else Muted, fontSize = 22.sp, fontWeight = if (color == Clean) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** The design's action bar: what the picked card is, one line of guidance, preview, and the main move. */
private class Primary(val label: String, val fg: Color, val bg: Color, val line: Color, val enabled: Boolean, val dim: Boolean, val onClick: () -> Unit)

@Composable
private fun ActionBar(ui: GarageUi, wide: Boolean, vm: GarageViewModel) {
    val context = LocalContext.current
    val parked = ui.parked
    val p = ui.selectedPart
    val name: String
    var line: String
    var lineColor = Muted
    val previewId: String?
    var primary: Primary? = null
    if (p == null) {
        name = "지금 빌드"
        line = "세팅은 바꾸는 즉시 적용돼요"
        previewId = GarageViewModel.BUILD_PREVIEW
    } else {
        name = p.name
        previewId = p.id.takeIf { p.kind == PartKind.NORMAL }
        val price = points(p.price)
        when (Garage.status(ui.state, p)) {
            PartStatus.PLACEHOLDER -> line = "다음 엔진을 고르는 중이에요"
            PartStatus.LIMITED -> {
                line = "포인트와 별도로 판매하는 한정 팩"
                lineColor = Gold
                primary = Primary("팩 보기", Gold, Color.Transparent, Gold, enabled = true, dim = false) {
                    Toast.makeText(context, "한정 콜라보 팩은 예시예요. 출시 때 열려요", Toast.LENGTH_SHORT).show()
                }
            }
            PartStatus.EQUIPPED -> {
                line = "지금 장착된 부품이에요"
                primary = Primary("장착됨", Heat, Color.Transparent, Heat, enabled = false, dim = false) {}
            }
            PartStatus.OWNED -> {
                line = "보유 중 · 장착하면 엔진에 바로 붙어요"
                primary = Primary("장착", OnHeat, Heat, Heat, enabled = true, dim = false) { vm.equip(p.id) }
            }
            PartStatus.UNLOCKABLE -> {
                line = "모은 포인트로 지금 해금할 수 있어요"
                lineColor = Clean
                primary = Primary("해금 · $price P", OnClean, Clean, Clean, enabled = true, dim = false) { vm.unlock(p.id) }
            }
            PartStatus.LOCKED -> {
                val need = p.price - ui.state.balance
                line = "${points(need)} P 부족 · 안전 주행 약 ${Garage.kmFor(need)}km"
                lineColor = Clean
                primary = Primary("해금 · $price P", OnClean, Clean, Clean, enabled = false, dim = true) {}
            }
        }
    }
    if (!parked) {
        line = "정차 중에만 바꿀 수 있어요"
        lineColor = Muted
    }
    val playing = previewId != null && ui.playing == previewId
    val canPreview = previewId != null && parked && ui.ready
    Row(
        Modifier.fillMaxWidth().height(140.dp).topRule().padding(horizontal = if (wide) 40.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = lineColor, fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier.height(80.dp).defaultMinSize(minWidth = 80.dp).alpha(if (canPreview || playing) 1f else 0.35f).clip(shape)
                    .background(if (playing) Heat else Color.Transparent).border(1.dp, RuleStrong, shape)
                    .semantics { contentDescription = if (playing) "재생 중" else "미리 듣기" }
                    .clickable(enabled = canPreview || playing, role = Role.Button) { previewId?.let(vm::preview) }
                    .padding(horizontal = if (wide) 28.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                val fg = if (playing) OnHeat else Ink
                Icon(if (playing) GarageIcons.Bars else GarageIcons.Play, contentDescription = null, tint = fg, modifier = Modifier.size(30.dp))
                if (wide) Text(if (playing) "재생 중" else "미리 듣기", color = fg, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            }
            primary?.let { b ->
                val enabled = b.enabled && parked
                Box(
                    Modifier.height(80.dp).alpha(if (b.dim || (!parked && b.enabled)) 0.4f else 1f).clip(shape).background(b.bg).border(2.dp, b.line, shape)
                        .clickable(enabled = enabled, role = Role.Button, onClick = b.onClick)
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(b.label, color = b.fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

internal fun points(n: Int): String = String.format(Locale.ROOT, "%,d", n)

internal fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp, dash: Dp = 8.dp, gap: Dp = 6.dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(radius.toPx() - w / 2),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
    )
}

private fun Modifier.bottomRule() = drawBehind { drawRect(Rule, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }

private fun Modifier.topRule() = drawBehind { drawRect(Rule, Offset.Zero, Size(size.width, 1.dp.toPx())) }

private fun Modifier.rightRule() = drawBehind { drawRect(Rule, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) }

/** The design's line icons (24-unit viewBox), tinted where they are used. */
internal object GarageIcons {
    val Check = icon(2.6f, "M5 12.5l4.5 4.5L19 7.5")
    val Lock = icon(2.2f, RECT, "M8 11V8a4 4 0 0 1 8 0v3")
    val Unlock = icon(2.2f, RECT, "M8 11V8a4 4 0 0 1 7.6-1.8")
    val Bars = icon(2.6f, "M6 17v-6", "M10 17V7", "M14 17V9", "M18 17v-4")
    val Rotate = icon(2f, "M20 12a8 8 0 1 1-2.3-5.6", "M20 4v4h-4")
    val Power = icon(2.4f, "M12 3v8", "M6.4 6.6a8 8 0 1 0 11.2 0")
    val Play = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(PathParser().parsePathString("M8 5.6v12.8a.8.8 0 0 0 1.2.7l10.2-6.4a.8.8 0 0 0 0-1.4L9.2 4.9A.8.8 0 0 0 8 5.6z").toNodes(), fill = SolidColor(Color.White))
    }.build()

    private const val RECT = "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z"

    private fun icon(stroke: Float, vararg paths: String) = lineIcon(stroke, *paths)
}
