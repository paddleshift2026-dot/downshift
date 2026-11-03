package com.downshift.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class GaugeMode { RPM, POINTS }

/**
 * The design's gauge (Gauge.dc.html, a 520-unit drawing scaled to [size]): a 240° track from 150° and its filled part,
 * ticks, and in rpm mode a redline arc and 0..[labels] (×1000) labels; no needle. One gauge, two meanings: heat rpm when
 * parked, clean points when driving. [value], [center] and [caption] are read while drawing, so a moving value
 * redraws this layer only. [off] greys the rpm dial out with the engine stopped.
 */
@Composable
internal fun Gauge(
    size: Dp,
    mode: GaugeMode,
    value: () -> Float,
    center: () -> String,
    unit: String,
    caption: () -> String,
    description: String,
    modifier: Modifier = Modifier,
    redline: Float = 0.9f,
    labels: Int = 8,
    off: Boolean = false,
) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val points = mode == GaugeMode.POINTS
    Spacer(
        modifier.size(size).semantics { contentDescription = description }.graphicsLayer().drawBehind {
            val k = this.size.minDimension / 520f
            val c = Offset(260f * k, 260f * k)
            val r = 220f * k
            arc(c, r, START, SWEEP, GaugeTrack, 18f * k)
            val v = if (off) 0f else value().coerceIn(0f, 1f)
            if (v > 0f) arc(c, r, START, SWEEP * v, if (points) Clean else Heat, 18f * k)
            val majors = if (points) 10 else labels
            if (!points) {
                arc(c, r + 17f * k, START + SWEEP * redline, SWEEP * (1 - redline), Alert.copy(alpha = if (off) 0.45f else 1f), 6f * k)
                for (i in 0 until majors) tick(c, START + SWEEP * (i + 0.5f) / majors, r - 25f * k, r - 14f * k, Dim, 3f * k)
                for (n in 0..labels) {
                    val color = if (n.toFloat() / labels >= redline - 0.001f) Alert else if (off) Slate else Muted
                    label(measurer, n.toString(), polar(c, START + SWEEP * n / labels, 170f * k), condensed(28f * k, color))
                }
            }
            for (i in 0..majors) tick(c, START + SWEEP * i / majors, r - 32f * k, r - 14f * k, if (points) Dim else TickRpm, 4f * k)

            // The readout sits low in the dial, the caption at the bottom edge.
            val big = measurer.measure(center(), condensed((if (points) 84f else 80f) * k, if (points) Clean else if (off) Slate else Ink))
            val small = measurer.measure(unit, TextStyle(color = Muted, fontSize = ((if (points) 28f else 22f) * k).toSp(), fontWeight = FontWeight.Medium, letterSpacing = 0.04.em))
            val top = this.size.height * (if (points) 0.60f else 0.63f)
            drawText(big, topLeft = Offset((this.size.width - big.size.width) / 2, top))
            drawText(small, topLeft = Offset((this.size.width - small.size.width) / 2, top + big.size.height + 10f * k))
            val cap = caption()
            if (cap.isNotEmpty()) {
                val capLayout = measurer.measure(cap, TextStyle(color = if (points) Muted else Faint, fontSize = ((if (points) 30f else 24f) * k).toSp(), fontWeight = FontWeight.Medium))
                drawText(capLayout, topLeft = Offset((this.size.width - capLayout.size.width) / 2, this.size.height * 0.97f - capLayout.size.height))
            }
        },
    )
}

private const val START = 150f
private const val SWEEP = 240f

private fun DrawScope.arc(c: Offset, radius: Float, start: Float, sweep: Float, color: Color, width: Float) =
    drawArc(color, start, sweep, useCenter = false, topLeft = Offset(c.x - radius, c.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(width))

private fun DrawScope.tick(c: Offset, deg: Float, from: Float, to: Float, color: Color, width: Float) =
    drawLine(color, polar(c, deg, from), polar(c, deg, to), strokeWidth = width, cap = StrokeCap.Round)

private fun DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

private fun polar(c: Offset, deg: Float, radius: Float): Offset {
    val t = deg * PI / 180
    return Offset((c.x + radius * cos(t)).toFloat(), (c.y + radius * sin(t)).toFloat())
}

private fun DrawScope.condensed(px: Float, color: Color) =
    TextStyle(color = color, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = px.toSp())
