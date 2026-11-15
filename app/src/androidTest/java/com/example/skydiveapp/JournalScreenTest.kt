package com.example.skydiveapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalScreenTest {
    @get:Rule val rule = createComposeRule()
    private val jumps = listOf(
        Jump("a", "01.11.2026", "Якутск", "3000", "Тандем", "", isFavorite = true),
        Jump("b", "01.11.2026", "Москва", "4000", "FS", "")
    )

    @Test fun searchAndFavoriteFiltersCanBeReset() {
        rule.setContent { MaterialTheme { JournalScreen(jumps, emptySet(), {}, {}, {}, {}) } }
        rule.onNodeWithText("Поиск по записям").performTextInput("Москва")
        rule.onAllNodesWithText("Москва").assertCountEquals(2)
        rule.onNodeWithText("★ Избранное").performClick()
        rule.onNodeWithText("Ничего не нашлось").performScrollTo().assertExists()
        rule.onNodeWithText("Сбросить фильтры").performScrollTo().performClick()
        rule.onNodeWithText("Якутск").performScrollTo().assertExists()
    }

    @Test fun starRequestsFavoriteForCorrectJump() {
        var selected: Jump? = null
        rule.setContent { MaterialTheme { JournalScreen(jumps, emptySet(), {}, {}, {}, { selected = it }) } }
        rule.onNodeWithContentDescription("Убрать из избранного").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("a", selected?.id) }
    }
}
