/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.ui.utils

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val BACK_DOUBLE_TAP_MS = 380L

/**
 * In-app pointer for Android TV remotes.
 *
 * Two Back presses toggle pointer mode. A single Back is forwarded to the app's normal
 * back dispatcher after the double-press window, so existing BackHandlers keep their meaning.
 * In pointer mode the D-pad moves the pointer and OK sends a normal touch pair to the Compose
 * root view, letting existing buttons and click handlers work without accessibility access.
 */
fun Modifier.tvRemoteCursor(onBack: () -> Unit): Modifier = composed {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestOnBack by rememberUpdatedState(onBack)

    var enabled by rememberSaveable { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf(Offset.Zero) }
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var pendingBack by remember { mutableStateOf<Job?>(null) }

    val step = with(density) { 28.dp.toPx() }
    val pointerWidth = with(density) { 28.dp.toPx() }
    val pointerHeight = with(density) { 42.dp.toPx() }

    this
        .onSizeChanged { newSize ->
            size = newSize
            if (position == Offset.Zero) {
                position = Offset(newSize.width / 2f, newSize.height / 2f)
            } else {
                position = Offset(
                    position.x.coerceIn(0f, newSize.width.toFloat().coerceAtLeast(1f)),
                    position.y.coerceIn(0f, newSize.height.toFloat().coerceAtLeast(1f)),
                )
            }
        }
        .onPreviewKeyEvent { event ->
            val key = event.key
            val down = event.type == KeyEventType.KeyDown

            if (key == Key.Back) {
                if (!down) return@onPreviewKeyEvent true
                if (event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true

                val now = SystemClock.uptimeMillis()
                if (lastBackAt != 0L && now - lastBackAt <= BACK_DOUBLE_TAP_MS) {
                    pendingBack?.cancel()
                    pendingBack = null
                    lastBackAt = 0L
                    enabled = !enabled
                    if (enabled && size != IntSize.Zero) {
                        position = Offset(size.width / 2f, size.height / 2f)
                    }
                } else {
                    lastBackAt = now
                    pendingBack?.cancel()
                    pendingBack = scope.launch {
                        delay(BACK_DOUBLE_TAP_MS)
                        if (lastBackAt == now) {
                            lastBackAt = 0L
                            latestOnBack()
                        }
                    }
                }
                return@onPreviewKeyEvent true
            }

            if (!enabled) return@onPreviewKeyEvent false
            if (key !in setOf(
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft,
                    Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter
                )
            ) return@onPreviewKeyEvent false

            if (!down) return@onPreviewKeyEvent true

            when (key) {
                Key.DirectionUp -> position = Offset(position.x, (position.y - step).coerceAtLeast(0f))
                Key.DirectionDown -> position = Offset(position.x, (position.y + step).coerceAtMost(size.height.toFloat()))
                Key.DirectionLeft -> position = Offset((position.x - step).coerceAtLeast(0f), position.y)
                Key.DirectionRight -> position = Offset((position.x + step).coerceAtMost(size.width.toFloat()), position.y)
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (size != IntSize.Zero) {
                        val x = position.x.coerceIn(0f, (size.width - 1).coerceAtLeast(0).toFloat())
                        val y = position.y.coerceIn(0f, (size.height - 1).coerceAtLeast(0).toFloat())
                        val downTime = SystemClock.uptimeMillis()
                        val downEvent = MotionEvent.obtain(
                            downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0
                        )
                        val upEvent = MotionEvent.obtain(
                            downTime, downTime + 1L, MotionEvent.ACTION_UP, x, y, 0
                        )
                        try {
                            view.dispatchTouchEvent(downEvent)
                            view.dispatchTouchEvent(upEvent)
                        } finally {
                            downEvent.recycle()
                            upEvent.recycle()
                        }
                    }
                }
            }
            true
        }
        .drawWithContent {
            drawContent()
            if (enabled && size != IntSize.Zero) {
                val x = position.x
                val y = position.y
                val pointer = Path().apply {
                    moveTo(x, y)
                    lineTo(x + pointerWidth * 0.08f, y + pointerHeight)
                    lineTo(x + pointerWidth * 0.36f, y + pointerHeight * 0.72f)
                    lineTo(x + pointerWidth * 0.62f, y + pointerHeight * 1.08f)
                    lineTo(x + pointerWidth * 0.82f, y + pointerHeight * 0.98f)
                    lineTo(x + pointerWidth * 0.55f, y + pointerHeight * 0.64f)
                    lineTo(x + pointerWidth, y + pointerHeight * 0.60f)
                    close()
                }
                drawPath(pointer, Color.Black, style = Stroke(width = with(density) { 3.dp.toPx() }))
                drawPath(pointer, Color.White)
            }
        }
}
