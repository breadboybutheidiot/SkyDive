package com.example.skydiveapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JumpHelpTest {
    @get:Rule val rule = createComposeRule()

    @Test fun freefallHelpOpensAndReturnsToJumpForm() {
        rule.setContent {
            MaterialTheme { JumpDialog(null, JumpDraft(null), {}, false, null, {}) }
        }
        rule.onNodeWithContentDescription("Подсказка: Свободное падение").performScrollTo().performClick()
        rule.onNodeWithText("Сколько секунд длилось свободное падение до раскрытия парашюта. Можно оставить пустым").assertExists()
        rule.onNodeWithText("Понятно").performClick()
        rule.onNodeWithText("Новый прыжок").assertExists()
    }
}
