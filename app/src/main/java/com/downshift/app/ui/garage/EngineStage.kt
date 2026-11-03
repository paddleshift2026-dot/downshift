package com.downshift.app.ui.garage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.downshift.app.core.garage.Catalog
import com.downshift.app.core.garage.Category
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.Part
import com.downshift.app.core.garage.TurnSet
import com.downshift.app.core.garage.Turntable
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Heat
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.RuleStrong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where things sit on the stage, in dp, from the design: the stage, the frame square (it pokes out above the stage),
 * the chip's width and top, and the point the stage tilts around when a part is equipped.
 */
private class StageGeometry(
    val w: Float, val h: Float, val imgLeft: Float, val imgTop: Float, val imgSize: Float,
    val chipW: Float, val chipTop: Float, val pivotX: Float, val pivotY: Float,
)

private val WIDE = StageGeometry(504f, 320f, 82f, -34f, 340f, 220f, 262f, 252f, 179f)
private val NARROW = StageGeometry(340f, 290f, 25f, -40f, 290f, 316f, 234f, 170f, 154f)
private val CATEGORY_LABEL = mapOf(Category.INTAKE to "흡기", Category.EXHAUST to "배기", Category.TRANSMISSION to "변속기")
private val ChipGround = Color(0xEB131417)
private val DotRing = Color(0xFF131417)
private val KickBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * The 3D stage, from 24 pre-rendered frames of the shown engine (and the supercharger or twin-turbo set over it when
 * that intake is fitted or previewed). Drag sideways to turn it; choosing a tab spins it the short way to the frame
 * that shows that part, where the part's spot, a lead line and a chip appear. Equipping flashes the spot and tilts the
 * stage. A turn changes only the frame index, which is read while drawing: nothing recomposes or lays out.
 */
@Composable
internal fun EngineStage(ui: GarageUi, wide: Boolean, modifier: Modifier = Modifier) {
    val geo = if (wide) WIDE else NARROW
    val px = with(LocalDensity.current) { geo.imgSize.dp.roundToPx() }
    val engine = shownEngine(ui).id
    val preview = previewedPart(ui)
    val next = if (preview != null) Garage.preview(ui.state, preview.id) else Garage.build(ui.state)
    val overlay = Turntable.variant(engine, Catalog.idOf(next, Category.INTAKE)).takeIf { it != engine }
    // The overlay set is kept ready for engines that have one, so fitting the part only fades it in.
    val overlaySet = when (engine) {
        "v8" -> "v8sc"
        "v12" -> "v12t"
        else -> null
    }
    val base = rememberFrames(engine, px)
    val top = rememberFrames(overlaySet, px)
    val baseHero = rememberAssetImage("engine3d/$engine/hero.webp", px)
    val topHero = rememberAssetImage(overlaySet?.let { "engine3d/$it/hero.webp" }, px)
    val turn = rememberTurnSet(overlay ?: engine)
    val category = ui.tab.category?.takeIf { it != Category.ENGINE }
    val target = category?.let { turn?.focus?.get(it) } ?: 0

    val frame = remember { mutableIntStateOf(target) }
    var dragging by remember { mutableStateOf(false) }
    var backAfterDrag by remember { mutableStateOf(false) }
    LaunchedEffect(target, dragging) {
        if (dragging) return@LaunchedEffect
        if (backAfterDrag) {
            delay(900)
            backAfterDrag = false
        }
        while (frame.intValue != target) {
            frame.intValue = Turntable.spinStep(frame.intValue, target)
            delay(34)
        }
    }
    val topAlpha = animateFloatAsState(if (overlay != null) 1f else 0f, tween(280, easing = LinearOutSlowInEasing), label = "overlay")

    // Equipping: the stage tilts -2.5° and springs back, and the slot's spot and chip flash.
    val kick = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val seen = remember { mutableIntStateOf(ui.equipSeq) }
    LaunchedEffect(ui.equipSeq) {
        if (ui.equipSeq == seen.intValue) return@LaunchedEffect
        seen.intValue = ui.equipSeq
        launch {
            kick.animateTo(-2.5f, tween(110, easing = LinearOutSlowInEasing))
            kick.animateTo(0f, tween(650, easing = KickBack))
        }
        glow.snapTo(0f)
        glow.animateTo(1f, tween(120))
        glow.animateTo(0f, tween(900))
    }

    val facing by remember(target) { derivedStateOf { frame.intValue == target } }
    val chip = category.takeIf { facing && !dragging && turn != null }
    val anchor = chip?.let { turn?.anchors?.get(it) }?.let { (x, y) -> Offset(geo.imgLeft + x.toFloat() * geo.imgSize, geo.imgTop + y.toFloat() * geo.imgSize) }
    val chipX = anchor?.let { (it.x - geo.chipW / 2).coerceIn(12f, geo.w - geo.chipW - 12f) } ?: 0f
    val shownPart = chip?.let { c -> ui.selected[c]?.let(Catalog::part)?.takeIf { it.id != Garage.fitted(ui.state, c) } }
    val chipPart = shownPart ?: chip?.let { Catalog.part(Garage.fitted(ui.state, it)) }
    val previewing = shownPart != null
    val glowing = chip != null && ui.equipCategory == chip

    Box(
        modifier.size(geo.w.dp, geo.h.dp).clip(RoundedCornerShape(16.dp)).background(StageBackdrop).border(1.dp, StageEdge, RoundedCornerShape(16.dp))
            .semantics {
                contentDescription = "엔진 돌려보기"
                stateDescription = if (frame.intValue == 0) "기본 각도" else "${frame.intValue * 15}도 돌림"
            }
            .pointerInput(geo) {
                val perFrame = geo.w.dp.toPx() / Turntable.FRAMES
                var start = 0
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        start = frame.intValue
                        dx = 0f
                        dragging = true
                    },
                    onDragEnd = {
                        backAfterDrag = true
                        dragging = false
                    },
                    onDragCancel = {
                        backAfterDrag = true
                        dragging = false
                    },
                ) { change, amount ->
                    change.consume()
                    dx += amount
                    frame.intValue = Turntable.dragFrame(start, dx, perFrame)
                }
            },
    ) {
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val k = frame.intValue
                val dst = IntOffset(geo.imgLeft.dp.roundToPx(), geo.imgTop.dp.roundToPx())
                val size = IntSize(px, px)
                rotate(kick.value, pivot = Offset(geo.pivotX.dp.toPx(), geo.pivotY.dp.toPx())) {
                    (base.value.getOrNull(k) ?: baseHero)?.let { drawImage(it, dstOffset = dst, dstSize = size) }
                    val a = topAlpha.value
                    if (a > 0f) (top.value.getOrNull(k) ?: topHero)?.let { drawImage(it, dstOffset = dst, dstSize = size, alpha = a) }
                }
                if (anchor != null) {
                    val a = Offset(anchor.x.dp.toPx(), anchor.y.dp.toPx())
                    val lx = anchor.x.coerceIn(chipX + 24f, chipX + geo.chipW - 24f)
                    drawLine(
                        Heat, Offset(lx.dp.toPx(), geo.chipTop.dp.toPx()), a, strokeWidth = 2.dp.toPx(),
                        pathEffect = if (previewing) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                    )
                    val g = if (glowing) glow.value else 0f
                    if (g > 0f) drawCircle(Heat.copy(alpha = 0.32f * g), (7f + 10f * g).dp.toPx(), a)
                    drawCircle(DotRing, 7.dp.toPx(), a)
                    drawCircle(Heat, 4.dp.toPx(), a)
                }
            },
        )
        if (chip != null && chipPart != null && anchor != null) {
            StageChip(
                chipPart, CATEGORY_LABEL.getValue(chip), previewing, glow.takeIf { glowing },
                Modifier.offset(chipX.dp, geo.chipTop.dp).size(geo.chipW.dp, 48.dp),
            )
        }
        Row(
            Modifier.offset(12.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(GarageIcons.Rotate, contentDescription = null, tint = Faint, modifier = Modifier.size(20.dp))
            Text("끌어서 돌려보기", color = Faint, fontSize = 16.sp)
        }
    }
}

@Composable
private fun StageChip(part: Part, label: String, previewing: Boolean, glow: Animatable<Float, *>?, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val image = rememberAssetImage("parts/${part.id}.webp", with(LocalDensity.current) { 32.dp.roundToPx() })
    Row(
        modifier
            .drawBehind {
                val g = glow?.value ?: 0f
                if (g > 0f) {
                    val spread = 10.dp.toPx() * g
                    drawRoundRect(
                        Heat.copy(alpha = 0.32f * g), Offset(-spread, -spread), Size(size.width + 2 * spread, size.height + 2 * spread),
                        CornerRadius(12.dp.toPx() + spread),
                    )
                }
            }
            .clip(shape).background(ChipGround)
            .then(if (previewing) Modifier.dashedBorder(2.dp, Heat, 12.dp, 6.dp, 5.dp) else Modifier.border(2.dp, Heat, shape))
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(RuleStrong)) {
            if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = Faint, fontSize = 15.sp, lineHeight = 16.sp)
            Text(part.code, color = Ink, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 22.sp, letterSpacing = 0.04.em, maxLines = 1, softWrap = false)
        }
        Text(if (previewing) "미리보기" else "장착됨", color = if (previewing) Heat else Muted, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

/** All 24 frames of one render set, decoded together off the main thread; empty while loading or without a set. */
@Composable
private fun rememberFrames(variant: String?, px: Int): State<List<ImageBitmap?>> {
    val context = LocalContext.current
    return produceState(initialValue = emptyList<ImageBitmap?>(), variant, px) {
        value = emptyList() // never show another engine's frames while this one loads
        if (variant == null) return@produceState
        value = coroutineScope {
            (0 until Turntable.FRAMES).map { k ->
                async(Dispatchers.IO) { GarageImages.decode(context, "engine3d/$variant/turn_%02d.webp".format(k), px) }
            }.awaitAll()
        }
    }
}

@Composable
private fun rememberTurnSet(variant: String): TurnSet? {
    val context = LocalContext.current
    return remember(variant) {
        runCatching { Turntable.parse(context.assets.open("engine3d/$variant/anchors.json").use { it.readBytes().decodeToString() }) }.getOrNull()
    }
}
