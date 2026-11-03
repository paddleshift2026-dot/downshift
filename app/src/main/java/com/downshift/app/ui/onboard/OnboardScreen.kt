package com.downshift.app.ui.onboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.downshift.app.DownshiftApp
import com.downshift.app.ui.AppIcons
import com.downshift.app.ui.Calm
import com.downshift.app.ui.CalmLine
import com.downshift.app.ui.CalmSheet
import com.downshift.app.ui.Condensed
import com.downshift.app.ui.Faint
import com.downshift.app.ui.Ground
import com.downshift.app.ui.Heat
import com.downshift.app.ui.Ink
import com.downshift.app.ui.Muted
import com.downshift.app.ui.OnHeat
import com.downshift.app.ui.Rule
import com.downshift.app.ui.Sheet
import com.downshift.app.ui.Slate
import com.downshift.app.ui.garage.GarageIcons

/**
 * The first-run safety note (design: Onboard.dc.html, PRD feature F): four promises and a consent box; the garage
 * opens once it is ticked and agreed to. The 세팅 tab brings it back.
 */
@Composable
fun OnboardScreen() {
    val drive = (LocalContext.current.applicationContext as DownshiftApp).drive
    var agreed by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Ground), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxHeight().padding(start = 40.dp, end = 40.dp, top = 56.dp, bottom = 40.dp)) {
            Image(rememberVectorPainter(Logo), contentDescription = null, modifier = Modifier.size(72.dp))
            Text(
                "차 안에서만 들리는\n내 엔진", Modifier.padding(top = 28.dp),
                color = Ink, fontSize = 52.sp, lineHeight = 63.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em,
            )
            Text(
                "안전하게 달린 만큼 부품이 열려요.\n시작하기 전에 네 가지만 확인해 주세요.", Modifier.padding(top = 18.dp),
                color = Muted, fontSize = 26.sp, lineHeight = 39.sp,
            )
            Column(Modifier.padding(top = 44.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Promise(AppIcons.Cabin, "실내 스피커로만 나와요", "바깥으로는 소리가 나지 않아요. 전기차 보행자 경고음은 그대로예요.")
                Promise(AppIcons.Bubble, "안내와 경고가 먼저예요", "내비 안내나 경고음이 나오면 엔진음이 저절로 작아져요.")
                Promise(AppIcons.Quiet, "조용히 지나갈 곳에선 줄여요", "스쿨존·주거지에 들어서면, 동승자가 타면 볼륨을 알아서 낮춰요.", calm = true)
                Promise(null, "튜닝은 정차 중에만", "달리는 동안엔 큰 음소거 버튼과 이번 주행 포인트만 보여요.")
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 76.dp).toggleable(agreed, role = Role.Checkbox) { agreed = it },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val box = RoundedCornerShape(8.dp)
                Box(
                    Modifier.size(36.dp).clip(box).background(if (agreed) Heat else Color.Transparent).border(2.dp, if (agreed) Heat else Slate, box),
                    contentAlignment = Alignment.Center,
                ) {
                    if (agreed) Icon(GarageIcons.Check, contentDescription = null, tint = OnHeat, modifier = Modifier.size(26.dp))
                }
                Text("위 내용을 확인했고 안전 안내에 동의해요", color = Ink, fontSize = 24.sp)
            }
            Box(
                Modifier.padding(top = 16.dp).fillMaxWidth().height(96.dp).clip(RoundedCornerShape(20.dp)).background(if (agreed) Ink else Rule)
                    .clickable(enabled = agreed, role = Role.Button) { drive.agree() },
                contentAlignment = Alignment.Center,
            ) {
                Text("동의하고 개러지로", color = if (agreed) Ground else Faint, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "처음 한 번만 보여요 · 설정에서 다시 볼 수 있어요", Modifier.fillMaxWidth().padding(top = 18.dp),
                color = Faint, fontSize = 20.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

/** One of the four promises: an icon tile (or the P letter), a title and a line of detail. */
@Composable
private fun Promise(icon: ImageVector?, title: String, body: String, calm: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        val tile = RoundedCornerShape(20.dp)
        Box(
            Modifier.size(72.dp).clip(tile).background(if (calm) CalmSheet else Sheet).border(1.dp, if (calm) CalmLine else Rule, tile),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (calm) Calm else Ink, modifier = Modifier.size(36.dp))
            } else {
                Text("P", color = Ink, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 34.sp)
            }
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 22.sp, lineHeight = 33.sp)
        }
    }
}

/** The app mark: a gauge arc, the heat part of it filled, and the needle (48-unit viewBox, three colours). */
private val Logo: ImageVector = ImageVector.Builder(defaultWidth = 72.dp, defaultHeight = 72.dp, viewportWidth = 48f, viewportHeight = 48f).apply {
    listOf(
        Triple("M10.14 34 A 16 16 0 1 1 37.86 34", Color(0xFF2A2E34), 5f),
        Triple("M10.14 34 A 16 16 0 0 1 32 12.14", Heat, 5f),
        Triple("M24 26 L 30 17", Ink, 3f),
    ).forEach { (d, color, width) ->
        addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(color), strokeLineWidth = width, strokeLineCap = StrokeCap.Round)
    }
}.build()
