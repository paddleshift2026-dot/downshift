package com.downshift.app.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.downshift.app.core.garage.SoundBars
import com.downshift.app.core.garage.SoundCharacter
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Ground
import com.downshift.app.ui.Heat
import com.downshift.app.ui.Muted
import com.downshift.app.ui.Rule
import com.downshift.app.ui.Slate

private val BarFill = Color(0xFFE6E8EB)

/**
 * The four sound-character bars (bass, rasp, whistle, pops; 0..10 cells). With a part previewed, what it adds shows in
 * heat, what it takes away in slate, and the change as +2 or −1.
 */
@Composable
internal fun SoundCharacterBars(now: SoundBars, next: SoundBars, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 10.dp)) {
        if (wide) Text("소리 성향", color = Faint, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        listOf(
            Triple("저음", now.bass, next.bass), Triple("고음", now.rasp, next.rasp),
            Triple("휘슬", now.whistle, next.whistle), Triple("팝콘", now.pop, next.pop),
        ).forEach { (label, a, b) -> BarRow(label, a, b, wide) }
    }
}

@Composable
private fun BarRow(label: String, a: Int, b: Int, wide: Boolean) {
    val d = b - a
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 12.dp)) {
        Text(label, Modifier.width(if (wide) 60.dp else 40.dp), color = Muted, fontSize = if (wide) 24.sp else 20.sp)
        Spacer(
            Modifier.weight(1f).height(if (wide) 18.dp else 14.dp).clip(RoundedCornerShape(if (wide) 4.dp else 3.dp)).drawBehind {
                val cell = size.width / SoundCharacter.MAX
                fun span(from: Int, cells: Int, color: Color) {
                    if (cells > 0) drawRect(color, Offset(from * cell, 0f), Size(cells * cell, size.height))
                }
                drawRect(Rule)
                span(0, minOf(a, b), BarFill)
                span(a, maxOf(0, d), Heat)
                span(b, maxOf(0, -d), Slate)
                // Cut the bar into ten cells, as the design's repeating gradient does.
                val gap = 6.dp.toPx()
                for (i in 1..SoundCharacter.MAX) drawRect(Ground, Offset(i * cell - gap, 0f), Size(gap, size.height))
            },
        )
        Text(
            when {
                d > 0 -> "+$d"
                d < 0 -> "−${-d}"
                else -> ""
            },
            Modifier.width(if (wide) 44.dp else 32.dp), textAlign = TextAlign.End,
            color = if (d > 0) Heat else Muted, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = if (wide) 28.sp else 22.sp,
        )
    }
}
