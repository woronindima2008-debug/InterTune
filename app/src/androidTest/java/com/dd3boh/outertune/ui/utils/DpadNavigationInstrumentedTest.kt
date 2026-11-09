/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dd3boh.outertune.R
import com.dd3boh.outertune.ui.menu.GridMenu
import com.dd3boh.outertune.ui.menu.GridMenuItem
import com.dd3boh.outertune.ui.menu.SleepTimerGridMenu

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DpadNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rightFromNavigationRailMovesIntoPageContent() {
        val railFocus = FocusRequester()
        val pageFocus = FocusRequester()

        composeRule.setContent {
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(Unit) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                railFocus.requestFocus()
            }
            MaterialTheme {
                Box(Modifier.fillMaxSize().dpadOverlayEscape(tvNavigation = true)) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail {
                            NavigationRailItem(
                                selected = true,
                                onClick = {},
                                icon = { Text("H") },
                                label = { Text("Home") },
                                modifier = Modifier.focusRequester(railFocus).testTag("rail-home"),
                            )
                            NavigationRailItem(
                                selected = false,
                                onClick = {},
                                icon = { Text("S") },
                                label = { Text("Search") },
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            Button(
                                onClick = {},
                                modifier = Modifier.focusRequester(pageFocus).testTag("page-first"),
                            ) {
                                Text("First content item")
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rail-home").assertIsFocused()
        composeRule.onNodeWithTag("rail-home").performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }
        composeRule.onNodeWithTag("page-first").assertIsFocused()
    }

    @Test
    fun downFromSearchMovesFocusToFirstPageItemAfterOverlayCloses() {
        val searchFocus = FocusRequester()
        val pageFocus = FocusRequester()
        val searchActive = mutableStateOf(true)

        composeRule.setContent {
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(Unit) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                searchFocus.requestFocus()
            }
            MaterialTheme {
                Box(
                    Modifier.fillMaxSize().dpadOverlayEscape(
                        tvNavigation = true,
                        onSearchDown = {
                            if (searchActive.value) {
                                searchActive.value = false
                                true
                            } else {
                                false
                            }
                        },
                    )
                ) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .then(if (searchActive.value) Modifier.dpadOverlay() else Modifier)
                                .focusRequester(searchFocus)
                                .focusable()
                                .testTag("search-field"),
                        )
                        Button(
                            onClick = {},
                            modifier = Modifier.focusRequester(pageFocus).testTag("page-first"),
                        ) {
                            Text("First page item")
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search-field").assertIsFocused()
        composeRule.onNodeWithTag("search-field").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("page-first").assertIsFocused()
            }.isSuccess
        }
        composeRule.runOnIdle { assert(!searchActive.value) }
    }

    @Test
    fun downFromEditableSettingsSearchEscapesItsFocusGroup() {
        val searchFocus = FocusRequester()
        val firstSettingFocus = FocusRequester()

        composeRule.setContent {
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(Unit) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                searchFocus.requestFocus()
            }
            MaterialTheme {
                Box(Modifier.fillMaxSize().dpadOverlayEscape(tvNavigation = true)) {
                    Column(Modifier.fillMaxSize().focusGroup()) {
                        Row(Modifier.fillMaxWidth().dpadOverlay()) {
                            BasicTextField(
                                value = "",
                                onValueChange = {},
                                modifier = Modifier
                                    .focusRequester(searchFocus)
                                    .testTag("settings-search-editable"),
                            )
                        }
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .focusRequester(firstSettingFocus)
                                .testTag("settings-first-result"),
                        ) {
                            Text("First setting")
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-search-editable").assertIsFocused()
        composeRule.onNodeWithTag("settings-search-editable").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("settings-first-result").assertIsFocused()
            }.isSuccess
        }
    }

    @Test
    fun dpadMovesAcrossGridRowsAndKeepsMenuItemsReachable() {
        val firstFocus = FocusRequester()
        val fourthFocus = FocusRequester()

        composeRule.setContent {
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(Unit) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                firstFocus.requestFocus()
            }
            MaterialTheme {
                GridMenu(Modifier.width(400.dp).height(260.dp)) {
                    repeat(6) { index ->
                        GridMenuItem(
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                .then(if (index == 3) Modifier.focusRequester(fourthFocus) else Modifier)
                                .testTag("grid-item-$index"),
                            icon = Icons.Rounded.Bedtime,
                            title = R.string.sleep_timer,
                            onClick = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("grid-item-0").assertIsFocused()
        composeRule.onNodeWithTag("grid-item-0").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("grid-item-3").assertIsFocused() }.isSuccess
        }
    }

    @Test
    fun disabledSleepTimerMenuItemCannotBeActivated() {
        var activated = false

        composeRule.setContent {
            MaterialTheme {
                GridMenu(Modifier.fillMaxSize()) {
                    SleepTimerGridMenu(
                        modifier = Modifier.testTag("disabled-sleep-timer"),
                        sleepTimerTimeLeft = 0L,
                        enabled = false,
                        onClick = { activated = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("disabled-sleep-timer").assertIsNotEnabled()
        composeRule.onNodeWithTag("disabled-sleep-timer").performClick()
        composeRule.runOnIdle { assert(!activated) }
    }

    @Test
    fun fullScreenOverlayDoesNotLeakFocusToCoveredContent() {
        val overlayFocus = FocusRequester()
        val coveredFocus = FocusRequester()

        composeRule.setContent {
            val inputModeManager = LocalInputModeManager.current
            LaunchedEffect(Unit) {
                inputModeManager.requestInputMode(InputMode.Keyboard)
                overlayFocus.requestFocus()
            }
            MaterialTheme {
                Box(Modifier.fillMaxSize().dpadOverlayEscape(tvNavigation = true)) {
                    Button(
                        onClick = {},
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .focusRequester(coveredFocus).testTag("covered-content"),
                    ) { Text("Covered page action") }
                    Column(Modifier.fillMaxSize().dpadOverlay()) {
                        Button(
                            onClick = {},
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                .focusRequester(overlayFocus).testTag("overlay-action"),
                        ) { Text("Overlay action") }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay-action").assertIsFocused()
        for (key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
            composeRule.onNodeWithTag("overlay-action").performKeyInput {
                keyDown(key)
                keyUp(key)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("overlay-action").assertIsFocused()
            composeRule.onNodeWithTag("covered-content").assertIsNotFocused()
        }
    }
}
