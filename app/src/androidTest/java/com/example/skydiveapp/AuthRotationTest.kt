package com.example.skydiveapp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRotationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun registrationDraftSurvivesRotation() {
        rule.onNodeWithText("Нет аккаунта? Зарегистрироваться").performClick()
        rule.onNodeWithText("Логин").performTextInput("skytest123")
        rule.onNodeWithText("Пароль").performTextInput("abc123")
        rule.onNodeWithText("Повтори пароль").performScrollTo().performTextInput("abc124")

        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        rule.onNodeWithText("Регистрация").assertExists()
        rule.onNodeWithText("skytest123").assertExists()
        rule.onNodeWithText("Зарегистрироваться", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("Пароли не совпадают").performScrollTo().assertExists()
    }

    @Test fun statusBarUsesDarkIcons() {
        rule.activityRule.scenario.onActivity { activity ->
            assertTrue(WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars)
        }
    }
}
