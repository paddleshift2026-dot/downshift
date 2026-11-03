package com.downshift.app.ui.garage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.downshift.app.core.garage.Category
import com.downshift.app.core.garage.Garage
import com.downshift.app.core.garage.Part
import com.downshift.app.core.garage.PartKind
import com.downshift.app.core.garage.PartStatus
import com.downshift.app.ui.Clean
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Dim
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Gold
import com.downshift.app.ui.Heat
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.OnHeat
import com.downshift.app.ui.Rule
import com.downshift.app.ui.RuleStrong
import com.downshift.app.ui.Sheet
import com.downshift.app.ui.SheetRaised
import com.downshift.app.ui.Slate

/** A card's status line and its colour (the design's stText and stColor); no performance figures, only the price. */
internal fun statusLine(status: PartStatus, part: Part): Pair<String, Color> = when (status) {
    PartStatus.EQUIPPED -> "장착됨" to Heat
    PartStatus.OWNED -> "보유" to Muted
    PartStatus.UNLOCKABLE -> "${points(part.price)} P · 해금 가능" to Clean
    PartStatus.LOCKED -> "${points(part.price)} P" to Clean
    PartStatus.PLACEHOLDER -> "고르는 중" to Faint
    PartStatus.LIMITED -> "한정 콜라보 · [가격]" to Gold
}

/**
 * One part: picture, name, tier marks, what it sounds like, and its status. Tapping picks it for the action bar; the
 * round button plays its preview (not for placeholders and limited packs, which have nothing to hear yet).
 */
@Composable
internal fun PartCard(
    part: Part,
    status: PartStatus,
    balance: Int,
    selected: Boolean,
    playing: Boolean,
    canPreview: Boolean,
    wide: Boolean,
    onPick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = part.kind == PartKind.PLACEHOLDER
    val bg = if (selected) SheetRaised else Sheet
    val line = if (selected) Heat else if (placeholder) Dim else Rule
    val shape = RoundedCornerShape(20.dp)
    val imgW = if (wide) 200.dp else 186.dp
    Box(modifier.height(if (wide) 188.dp else 128.dp)) {
        Box(
            Modifier.fillMaxSize().clip(shape).background(bg)
                .then(if (placeholder) Modifier.dashedBorder(2.dp, line, 20.dp) else Modifier.border(2.dp, line, shape))
                .semantics { this.selected = selected }
                .clickable(role = Role.Button, onClick = onPick),
        ) {
            CardImage(part, bg, imgW, Modifier.width(imgW).fillMaxHeight())
            Column(
                Modifier.fillMaxSize().padding(
                    start = if (wide) 196.dp else 182.dp, top = if (wide) 22.dp else 14.dp, end = 22.dp, bottom = if (wide) 20.dp else 14.dp,
                ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(part.name, color = Ink, fontSize = if (wide) 28.sp else 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    if (part.tier > 0) TierMarks(part.tier)
                }
                Text(part.desc, color = Muted, fontSize = if (wide) 22.sp else 20.sp, lineHeight = 1.35.em, maxLines = 2)
                StatusRow(status, part, balance)
            }
        }
        PreviewDot(
            playing = playing, enabled = canPreview && part.kind == PartKind.NORMAL, name = part.name, onPlay = onPlay,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

@Composable
private fun CardImage(part: Part, bg: Color, width: Dp, modifier: Modifier) {
    when (part.kind) {
        PartKind.PLACEHOLDER -> Box(modifier.padding(28.dp).dashedBorder(2.dp, Dim, 14.dp), contentAlignment = Alignment.Center) {
            Text("?", color = Slate, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 56.sp)
        }
        PartKind.LIMITED -> Box(modifier.padding(28.dp).border(2.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text("LTD", color = Gold, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = 0.06.em)
        }
        PartKind.NORMAL -> {
            val path = if (part.category == Category.ENGINE) "engine3d/${part.id}/hero.webp" else "parts/${part.id}.webp"
            val image = rememberAssetImage(path, with(LocalDensity.current) { width.roundToPx() })
            Box(modifier) {
                if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                // The design fades the picture into the card on its right edge.
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.6f to Color.Transparent, 1f to bg)))
            }
        }
    }
}

@Composable
private fun TierMarks(tier: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i -> Box(Modifier.size(13.dp).clip(RoundedCornerShape(2.dp)).background(if (i < tier) Ink else RuleStrong)) }
    }
}

@Composable
private fun StatusRow(status: PartStatus, part: Part, balance: Int) {
    val (text, color) = statusLine(status, part)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val icon = when (status) {
            PartStatus.EQUIPPED -> GarageIcons.Check
            PartStatus.LOCKED -> GarageIcons.Lock
            PartStatus.UNLOCKABLE -> GarageIcons.Unlock
            else -> null
        }
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text, color = color, fontSize = 21.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        if (status == PartStatus.LOCKED) {
            Text("· 약 ${Garage.kmFor(part.price - balance)}km 더", color = Faint, fontSize = 21.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun PreviewDot(playing: Boolean, enabled: Boolean, name: String, onPlay: () -> Unit, modifier: Modifier) {
    Box(
        modifier.size(64.dp).alpha(if (enabled) 1f else 0.35f).clip(CircleShape)
            .background(if (playing) Heat else Color(0xCC0C0D0F)).border(1.dp, Dim, CircleShape)
            .semantics { contentDescription = (if (playing) "정지: " else "미리 듣기: ") + name }
            .clickable(enabled = enabled, role = Role.Button, onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) GarageIcons.Bars else GarageIcons.Play, contentDescription = null,
            tint = if (playing) OnHeat else Ink, modifier = Modifier.size(28.dp),
        )
    }
}
