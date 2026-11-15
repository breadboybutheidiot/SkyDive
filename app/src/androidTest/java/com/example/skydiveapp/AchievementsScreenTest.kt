package com.example.skydiveapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AchievementsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun earnedAchievementStaysVisibleWithEmptyJournal() {
        rule.setContent { MaterialTheme { AchievementsSection(emptyList(), true, setOf("first_jump"), null) {} } }
        rule.onNodeWithText("Получено 1 из 6").assertExists()
        rule.onNodeWithText("Получено").assertExists()
        rule.onNodeWithText("0 / 10 прыжков").assertExists()
    }

    @Test fun errorOffersRetry() {
        var retried = false
        rule.setContent { MaterialTheme { AchievementsSection(emptyList(), false, null, "Нет связи") { retried = true } } }
        rule.onNodeWithText("Попробовать снова").performClick()
        rule.runOnIdle { assertTrue(retried) }
    }
}
