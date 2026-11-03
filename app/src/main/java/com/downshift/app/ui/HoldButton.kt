package com.downshift.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fires once the press has been held for HOLD_MS, like the ignition in the design. While held, [fill] sweeps across
 * the button; it is drawn, not laid out, so holding costs no relayout. A press let go early calls [onShortPress].
 */
@Composable
internal fun HoldButton(
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    background: Color,
    fill: Color,
    onHeld: () -> Unit,
    onShortPress: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val progress = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val held by rememberUpdatedState(onHeld)
    val short by rememberUpdatedState(onShortPress)
    val on by rememberUpdatedState(enabled)
    Box(
        modifier.clip(shape).background(background)
            .graphicsLayer()
            .drawBehind { drawRect(fill, size = size.copy(width = size.width * progress.floatValue)) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (!on) return@awaitEachGesture
                    var fired = false
                    val job = scope.launch {
                        val start = System.nanoTime()
                        while (progress.floatValue < 1f) {
                            progress.floatValue = ((System.nanoTime() - start) / (HOLD_MS * 1e6)).toFloat().coerceAtMost(1f)
                            delay(16)
                        }
                        fired = true
                        held()
                    }
                    val up = waitForUpOrCancellation()
                    job.cancel()
                    progress.floatValue = 0f
                    if (up != null && !fired) short()
                }
            },
        content = content,
    )
}

private const val HOLD_MS = 900L
