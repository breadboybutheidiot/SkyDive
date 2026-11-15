package com.example.skydiveapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSelectorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun selectorChangesColorsAndCanSwitchBack() {
        var dark by mutableStateOf(false)
        var background = Color.Unspecified
        rule.setContent {
            AppTheme(dark) {
                val current = MaterialTheme.colorScheme.background
                SideEffect { background = current }
                ThemeSelector(dark) { dark = it }
            }
        }
        rule.onNodeWithText("Светлая").assertIsSelected()
        rule.runOnIdle { assertEquals(Color(0xFFF9F7FF), background) }
        rule.onNodeWithText("Тёмная").performClick()
        rule.onNodeWithText("Тёмная").assertIsSelected()
        rule.runOnIdle { assertEquals(Color(0xFF17171F), background) }
        rule.onNodeWithText("Светлая").performClick()
        rule.runOnIdle { assertEquals(Color(0xFFF9F7FF), background) }
    }
}
