package com.subhash.chatlauncher

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > History progressive disclosure (owner Nov 7 00:31: "collapse everything
 * except, show my query I'll click to expand"). Collapsed rows show only the query;
 * tapping expands the exchange and tapping again collapses it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryDisclosureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val query = "find all of the implicit state machines in my app"
    private val answer = "places where logic depends on the history of previous events"
    private val entries = listOf(
        HistoryEntry(2_000L, "assistant", "cloud", "To find them, look for **logic depends** on the history.\n### 1. Search\n$answer"),
        HistoryEntry(1_000L, "user", "local", query),
    )

    private fun openHistory() {
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                SettingsPage(
                    key = "", onKey = {},
                    profileName = "", onProfileName = {},
                    selected = emptySet(), onSelected = {},
                    dockSelected = emptySet(), onDockSelected = {},
                    profiles = emptySet(),
                    onToggleProfile = {}, onDeleteProfile = {},
                    editingProfile = null, onEditProfile = {},
                    gridWobbly = false, onGridWobbly = {},
                    activeProfile = null,
                    apps = emptyList(),
                    historyEntries = entries,
                    contextEntries = emptyList(),
                    onCancel = {}, onSave = {},
                    initialSection = "history",
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun collapsedShowsOnlyQueryTapExpandsTapCollapses() {
        openHistory()
        // Collapsed: the query is visible, the response is not composed at all.
        composeRule.onNodeWithText(query).assertIsDisplayed()
        composeRule.onNodeWithText(answer, substring = true).assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/history_collapsed.png")

        // Tap the query: the exchange expands - response visible, markdown markers gone.
        composeRule.onNodeWithText(query).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(answer, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("**logic depends**", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("### 1. Search", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("1. Search", substring = true).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/history_expanded.png")

        // Tap again: collapsed again.
        composeRule.onNodeWithText(query).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(answer, substring = true).assertDoesNotExist()
    }
}
