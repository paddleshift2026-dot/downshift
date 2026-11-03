package com.downshift.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What stands in for a parked-only screen (garage, start, summary, safety note) while the car asks for distraction
 * optimisation, e.g. in neutral at a standstill (UxGate). Two lines of large text, no controls, nothing moves; the
 * engine keeps its sound and the car's volume keys still turn it down.
 */
@Composable
fun DrivingLock() {
    Column(
        Modifier.fillMaxSize().background(Ground).padding(56.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("주행 중에는 쓸 수 없어요", color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("기어를 P로 바꾸면 다시 열려요", color = Muted, fontSize = 30.sp, textAlign = TextAlign.Center)
    }
}
