package com.downshift.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The start and summary screens' header: an optional back button, the title and the "P 정차 중" chip. */
@Composable
internal fun ScreenHeader(title: String, wide: Boolean, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(104.dp)
            .drawBehind { drawRect(Rule, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = if (wide) 48.dp else 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) {
            Box(
                Modifier.offset(x = (-14).dp).size(64.dp).clip(RoundedCornerShape(16.dp))
                    .semantics { contentDescription = "개러지로 돌아가기" }
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Back, contentDescription = null, tint = Muted, modifier = Modifier.size(32.dp))
            }
        }
        Text(title, color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
        ParkChip()
    }
}

@Composable
internal fun ParkChip() {
    Row(
        Modifier.height(44.dp).border(1.dp, RuleStrong, CircleShape).padding(start = 7.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
            Text("P", color = Ground, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        }
        Text("정차 중", color = Muted, fontSize = 20.sp)
    }
}
