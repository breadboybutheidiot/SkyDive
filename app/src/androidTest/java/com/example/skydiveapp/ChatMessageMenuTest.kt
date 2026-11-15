package com.example.skydiveapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMessageMenuTest {
    @get:Rule val rule = createComposeRule()
    private val edited = ChatMessage("message", "author", "Текст", "2026-01-01", "2026-01-02")

    @Test fun authorSeesEditDeleteAndEditedLabel() {
        rule.setContent { MaterialTheme { ChatMessageRow(edited, null, true, true, {}, {}, {}) } }
        rule.onNodeWithText("изменено").assertExists()
        rule.onNodeWithContentDescription("Действия с сообщением").performClick()
        rule.onNodeWithText("Изменить").assertExists()
        rule.onNodeWithText("Удалить").assertExists()
    }

    @Test fun adminCanDeleteButNotEditAnotherUsersMessage() {
        rule.setContent { MaterialTheme { ChatMessageRow(edited, null, false, true, {}, {}, {}) } }
        rule.onNodeWithContentDescription("Действия с сообщением").performClick()
        rule.onNodeWithText("Удалить").assertExists()
        rule.onNodeWithText("Изменить").assertDoesNotExist()
    }

    @Test fun otherUsersDoNotSeeActions() {
        rule.setContent { MaterialTheme { ChatMessageRow(edited, null, false, false, {}, {}, {}) } }
        rule.onNodeWithContentDescription("Действия с сообщением").assertDoesNotExist()
    }
}
