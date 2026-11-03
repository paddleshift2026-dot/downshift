package com.downshift.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.downshift.app.DownshiftApp
import com.downshift.app.R
import com.downshift.app.core.flow.Intro

/** The team mark (the launcher icon's wheel and paddles, drawable/brand_mark.xml) at a given height. Decorative. */
@Composable
internal fun BrandMark(height: Dp, modifier: Modifier = Modifier) {
    val mark = painterResource(R.drawable.brand_mark)
    Image(mark, contentDescription = null, modifier = modifier.height(height).aspectRatio(mark.intrinsicSize.width / mark.intrinsicSize.height))
}

/**
 * The team wordmark on black at a cold start (timing and rules in [Intro]). It lies over the app, so the screen the
 * flow picks is already composed underneath and shows through as the cover fades. A tap skips it, and it gives way at
 * once to the drive screen and the lock view. The opacities are read while drawing, so the fade does not recompose.
 */
@Composable
internal fun BrandIntro(onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as DownshiftApp
    val screen = app.drive.screen.collectAsStateWithLifecycle()
    val ux = app.host.ux.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(0L) }
    var skipped by remember { mutableStateOf(false) }
    val visible by remember { derivedStateOf { Intro.visible(elapsed, screen.value, skipped, ux.value) } }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (elapsed < Intro.TOTAL_MS) elapsed = withFrameMillis { it } - start
    }
    LaunchedEffect(visible) { if (!visible) onDone() }
    if (!visible) return
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = Intro.coverAlpha(elapsed) }.background(Ground)
            .pointerInput(Unit) { detectTapGestures { skipped = true } },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.brand_wordmark), contentDescription = "Paddle Shift",
            modifier = Modifier.width(maxWidth * if (maxWidth >= 1200.dp) 0.5f else 0.72f).graphicsLayer { alpha = Intro.markAlpha(elapsed) },
        )
    }
}
