package com.downshift.app.core.flow

/**
 * The team wordmark on black each time the app starts cold, before the screen the flow picks (the safety note on the
 * first run, the garage after that). It never covers the drive screen: a start in gear D, or a trip that begins while
 * it is up, goes straight to driving. A tap skips it. The wordmark fades in and holds, then the cover fades out.
 */
object Intro {
    const val FADE_IN_MS = 400L
    const val HOLD_MS = 1_000L
    const val FADE_OUT_MS = 300L
    const val TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS

    fun visible(elapsedMs: Long, screen: Screen, skipped: Boolean): Boolean =
        !skipped && screen != Screen.DRIVE && elapsedMs < TOTAL_MS

    fun markAlpha(elapsedMs: Long): Float = (elapsedMs.toFloat() / FADE_IN_MS).coerceIn(0f, 1f)

    fun coverAlpha(elapsedMs: Long): Float = (1f - (elapsedMs - FADE_IN_MS - HOLD_MS).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
}
