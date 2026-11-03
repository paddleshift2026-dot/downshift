package com.downshift.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File

// Design system tokens ("계기판 바탕 + 게임 문법"): one dark ground, heat for the engine, clean for the safe-driving side.
internal val Ground = Color(0xFF0C0D0F)
internal val Panel = Color(0xFF16181C)
internal val Ink = Color(0xFFF2F3F5)
internal val Muted = Color(0xFFA9AFB8)
internal val Faint = Color(0xFF858B95)
internal val Heat = Color(0xFFFF7A30)
internal val Clean = Color(0xFF26C590)
internal val Alert = Color(0xFFE5484D)

// Garage surfaces, rules and accents. Rule: earn in green (clean), spend in orange (heat).
internal val Sheet = Color(0xFF141619)
internal val SheetRaised = Color(0xFF1B1E22)
internal val Rule = Color(0xFF262A30)
internal val RuleStrong = Color(0xFF343941)
internal val Dim = Color(0xFF3A3F47)
internal val Slate = Color(0xFF5A606A)
internal val Gold = Color(0xFFD9C48F) // limited collaboration packs
internal val OnHeat = Color(0xFF160A03)
internal val OnClean = Color(0xFF04140D)

// Calm: quiet mode and guidance (the drive screen's chips, the first run). Then the gauge's track and rpm ticks.
internal val Calm = Color(0xFF8DB2FF)
internal val CalmSheet = Color(0xFF0F1A2E)
internal val CalmLine = Color(0xFF2B3F66)
internal val GaugeTrack = Color(0xFF23272D)
internal val TickRpm = Color(0xFF6B717C)

/**
 * Numbers and part codes, condensed, until Barlow Semi Condensed is approved for download. The system's
 * "sans-serif-condensed" is the variable Roboto at 75% width, and asking for it by name came out at full width
 * (emulator, M2), so the width axis is set on the system Roboto file directly; without that file, plain sans.
 */
internal val Condensed: FontFamily = File("/system/fonts/Roboto-Regular.ttf").let { roboto ->
    if (!roboto.canRead()) return@let FontFamily.SansSerif
    FontFamily(
        listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
            Font(roboto, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight), FontVariation.width(75f)))
        },
    )
}
