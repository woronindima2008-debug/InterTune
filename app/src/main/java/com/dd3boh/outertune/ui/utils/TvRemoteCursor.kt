/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.ui.utils

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Recognizes two consecutive OK/Enter presses. Kept independent of Android KeyEvent so the
 * timing rule can be unit-tested without an Activity or a TV device.
 */
internal class DoubleCenterPressDetector(
    private val windowMs: Long = 600L,
) {
    private var lastPressAt = Long.MIN_VALUE

    fun onPress(nowMs: Long): Boolean {
        val elapsed = nowMs - lastPressAt
        if (lastPressAt != Long.MIN_VALUE && elapsed in 0..windowMs) {
            lastPressAt = Long.MIN_VALUE
            return true
        }
        lastPressAt = nowMs
        return false
    }
}

/**
 * Activity-owned pointer state. Key events are handled by MainActivity, not Compose focus,
 * so the pointer remains usable even when no composable currently owns keyboard focus.
 */
class TvRemoteCursorState {
    var enabled by mutableStateOf(false)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    var size by mutableStateOf(IntSize.Zero)
        private set

    private var targetView: View? = null
    private var inputModeManager: InputModeManager? = null

    fun attach(view: View, inputModeManager: InputModeManager) {
        targetView = view
        this.inputModeManager = inputModeManager
    }

    fun detach(view: View) {
        if (targetView === view) {
            targetView = null
            inputModeManager = null
        }
    }

    /** Keep TV remote navigation in keyboard mode after synthetic pointer input. */
    fun requestKeyboardMode() {
        inputModeManager?.requestInputMode(InputMode.Keyboard)
    }

    /** Restores a usable Compose focus target when navigation has left the screen unfocused. */
    fun focusFirstTarget(): Boolean = targetView?.let(::focusFirstDpadTarget) ?: false

    fun toggle() {
        enabled = !enabled
        if (!enabled) requestKeyboardMode()
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled) return false
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 0
            KeyEvent.KEYCODE_DPAD_DOWN -> 1
            KeyEvent.KEYCODE_DPAD_LEFT -> 2
            KeyEvent.KEYCODE_DPAD_RIGHT -> 3
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> 4
            else -> return false
        }

        if (event.action == KeyEvent.ACTION_UP) return true
        if (event.action != KeyEvent.ACTION_DOWN) return true

        val view = targetView
        val density = view?.resources?.displayMetrics?.density ?: 1f
        val step = 32f * density
        when (direction) {
            0 -> position = Offset(position.x, (position.y - step).coerceAtLeast(0f))
            1 -> position = Offset(position.x, (position.y + step).coerceAtMost((size.height - 1).coerceAtLeast(0).toFloat()))
            2 -> position = Offset((position.x - step).coerceAtLeast(0f), position.y)
            3 -> position = Offset((position.x + step).coerceAtMost((size.width - 1).coerceAtLeast(0).toFloat()), position.y)
            4 -> if (event.repeatCount == 0) click()
        }
        return true
    }

    private fun click() {
        val view = targetView ?: return
        if (size == IntSize.Zero) return
        val x = position.x.coerceIn(0f, (size.width - 1).coerceAtLeast(0).toFloat())
        val y = position.y.coerceIn(0f, (size.height - 1).coerceAtLeast(0).toFloat())
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 1L, MotionEvent.ACTION_UP, x, y, 0)
        try {
            view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    fun updateSize(newSize: IntSize) {
        size = newSize
        position = if (position == Offset.Zero) {
            Offset(newSize.width / 2f, newSize.height / 2f)
        } else {
            Offset(
                position.x.coerceIn(0f, (newSize.width - 1).coerceAtLeast(0).toFloat()),
                position.y.coerceIn(0f, (newSize.height - 1).coerceAtLeast(0).toFloat()),
            )
        }
    }
}

/**
 * Draws the pointer over the app. Input is deliberately not handled here: Activity dispatches
 * D-pad and OK events before Android/Compose attempts to deliver them to the focused child.
 */
fun Modifier.tvRemoteCursor(cursor: TvRemoteCursorState): Modifier = composed {
    val view = LocalView.current
    val inputModeManager = LocalInputModeManager.current
    val density = LocalDensity.current
    val pointerWidth = with(density) { 28.dp.toPx() }
    val pointerHeight = with(density) { 42.dp.toPx() }

    androidx.compose.runtime.DisposableEffect(view, inputModeManager) {
        cursor.attach(view, inputModeManager)
        onDispose { cursor.detach(view) }
    }

    this
        .onSizeChanged(cursor::updateSize)
        .drawWithContent {
            drawContent()
            if (cursor.enabled && cursor.size != IntSize.Zero) {
                val x = cursor.position.x
                val y = cursor.position.y
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
