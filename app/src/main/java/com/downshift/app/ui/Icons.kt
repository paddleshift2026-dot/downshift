package com.downshift.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** A line icon from the design's 24-unit SVG paths, tinted where it is drawn. */
internal fun lineIcon(stroke: Float, vararg paths: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach {
            addPath(
                PathParser().parsePathString(it).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** The start, drive, summary and first-run screens' icons (the design's SVGs). */
internal object AppIcons {
    private const val SPEAKER = "M4 9.5h3.5L12 5.5v13l-4.5-4H4z"

    val Back = lineIcon(2.4f, "M15 6l-6 6 6 6")
    val Forward = lineIcon(2.4f, "M9 6l6 6-6 6")
    val Power = lineIcon(2.4f, "M12 3v8", "M6.4 6.6a8 8 0 1 0 11.2 0")
    val Speaker = lineIcon(2f, SPEAKER, "M15.5 9.5a3.5 3.5 0 0 1 0 5")
    val Mute = lineIcon(2f, SPEAKER, "M16 9.5l5 5", "M21 9.5l-5 5")
    val SoundOn = lineIcon(2f, SPEAKER, "M15.5 9a4.5 4.5 0 0 1 0 6", "M18.5 6.5a8 8 0 0 1 0 11")
    val Info = lineIcon(2f, "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18", "M12 11v6", "M12 7.5v.01")
    val Cabin = lineIcon(1.8f, "M7 5h10a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4z", "M8 10.5h2l2.5-2v7l-2.5-2H8z", "M15 10.5a2 2 0 0 1 0 3")
    val Bubble = lineIcon(1.8f, "M4 5h16v11H10l-5 4v-4H4z", "M8.5 10.5h7")
    val Quiet = lineIcon(1.8f, SPEAKER, "M15.5 9.5a3.5 3.5 0 0 1 0 5")
}
