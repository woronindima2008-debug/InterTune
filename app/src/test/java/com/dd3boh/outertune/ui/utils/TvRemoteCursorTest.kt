/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.ui.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRemoteCursorTest {

    @Test
    fun `cursor movement uses smaller steps while a direction key repeats`() {
        assertTrue(tvCursorStepDp(0) > tvCursorStepDp(1))
        assertTrue(tvCursorStepDp(1) > 0f)
    }


    @Test
    fun `first center press is not a toggle`() {
        val detector = DoubleCenterPressDetector()
        assertFalse(detector.onPress(1_000L))
    }

    @Test
    fun `second center press within window toggles once`() {
        val detector = DoubleCenterPressDetector(windowMs = 600L)
        assertFalse(detector.onPress(1_000L))
        assertTrue(detector.onPress(1_500L))
        assertFalse(detector.onPress(1_550L))
    }

    @Test
    fun `an intervening key resets the pending center press`() {
        val detector = DoubleCenterPressDetector(windowMs = 600L)
        assertFalse(detector.onPress(1_000L))
        detector.reset()
        assertFalse(detector.onPress(1_200L))
        assertTrue(detector.onPress(1_300L))
    }

    @Test
    fun `press after window starts a new pair`() {
        val detector = DoubleCenterPressDetector(windowMs = 600L)
        assertFalse(detector.onPress(1_000L))
        assertFalse(detector.onPress(1_601L))
        assertTrue(detector.onPress(2_000L))
    }

    @Test
    fun `second press at the window boundary toggles`() {
        val detector = DoubleCenterPressDetector(windowMs = 600L)
        assertFalse(detector.onPress(1_000L))
        assertTrue(detector.onPress(1_600L))
    }

    @Test
    fun `three presses form one completed pair and a new first press`() {
        val detector = DoubleCenterPressDetector(windowMs = 600L)
        assertFalse(detector.onPress(1_000L))
        assertTrue(detector.onPress(1_200L))
        assertFalse(detector.onPress(1_300L))
    }

    @Test
    fun `press outside window does not complete previous pair`() {
        val detector = DoubleCenterPressDetector(windowMs = 600L)
        assertFalse(detector.onPress(1_000L))
        assertFalse(detector.onPress(1_700L))
        assertTrue(detector.onPress(2_000L))
    }

    @Test
    fun `explicit disable leaves pointer mode off and never toggles it back on`() {
        val cursor = TvRemoteCursorState()
        cursor.toggle()
        assertTrue(cursor.enabled)

        cursor.disable()
        assertFalse(cursor.enabled)

        cursor.disable()
        assertFalse(cursor.enabled)
    }
}
