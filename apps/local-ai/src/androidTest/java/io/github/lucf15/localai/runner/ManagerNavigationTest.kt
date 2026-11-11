package io.github.lucf15.localai.runner

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ManagerNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun permissionsDestinationSurvivesRecreationAndReturnsToModels() {
        compose.onNodeWithContentDescription("App permissions").performClick()
        compose.onNodeWithText("App permissions").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Allowed apps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Models, shared across apps.").assertIsDisplayed()
        compose.onNodeWithText("Evaluate").assertDoesNotExist()
        compose.onNodeWithText("Model catalog").assertIsDisplayed()
        compose.onNodeWithText("Qwen2.5 1.5B Instruct INT8").assertExists()
    }
}
