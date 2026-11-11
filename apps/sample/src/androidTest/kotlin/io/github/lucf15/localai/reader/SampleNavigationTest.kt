package io.github.lucf15.localai.reader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class SampleNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<SampleActivity>()

    @Test fun editorSurvivesRecreationAndOtherExamplesHaveIndependentState() {
        compose.onNodeWithText("Polish an email").performClick()
        val draft = "Please keep this edited email after recreation."
        compose.onNodeWithText("English").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(draft)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Email assistant").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains(draft)
        compose.onNodeWithText("English").assertIsSelected()
        compose.onNodeWithContentDescription("Examples").performClick()
        compose.onNodeWithText("Sample").assertIsDisplayed()
        compose.onNodeWithText("Check a note").performClick()
        compose.onNodeWithText("Writing check").assertIsDisplayed()
        compose.onNodeWithText("Italian").assertIsSelected()
        compose.onNode(hasSetTextAction()).assertTextContains("Dommani", substring = true)
        compose.onNodeWithText(draft).assertDoesNotExist()
    }
}
