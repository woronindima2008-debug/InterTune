/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@OptIn(ExperimentalMaterial3Api::class)
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
}
