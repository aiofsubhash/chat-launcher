package com.subhash.chatlauncher

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Usage tab behavior with a fake UsageSource: grant gate, empty day, and the data list. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class UsageTabTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private class FakeUsageSource(
        private val access: Boolean,
        private val rows: List<AppUsage> = emptyList(),
    ) : UsageSource {
        override fun hasAccess(): Boolean = access
        override fun today(): List<AppUsage> = rows
    }

    private fun showUsageTab(source: UsageSource) {
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                SettingsPage(
                    key = "", onKey = {},
                    profileName = "", onProfileName = {},
                    selected = emptySet(), onSelected = {},
                    dockSelected = emptySet(), onDockSelected = {},
                    profiles = setOf("Focus"),
                    onToggleProfile = {}, onDeleteProfile = {},
                    editingProfile = null, onEditProfile = {},
                    gridWobbly = true, onGridWobbly = {},
                    activeProfile = null,
                    apps = listOf(AppEntry("WhatsApp", "com.whatsapp", Intent())),
                    usageSource = source,
                    onCancel = {}, onSave = {},
                    initialSection = "usage",
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun noAccessShowsExplanationAndEnableButton() {
        showUsageTab(FakeUsageSource(access = false))
        composeRule.onNodeWithText("App usage").assertIsDisplayed()
        composeRule.onNodeWithTag("usage_access_button").assertIsDisplayed()
        composeRule.onNodeWithText("Open usage access settings").assertIsDisplayed()
    }

    @Test
    fun accessShowsTotalAndPerAppRows() {
        val rows = listOf(
            AppUsage("com.whatsapp", "WhatsApp", 95 * 60_000L, 1762505100000L),
            AppUsage("com.instagram.android", "Instagram", 40 * 60_000L, 1762504500000L),
        )
        showUsageTab(FakeUsageSource(access = true, rows = rows))
        composeRule.onNodeWithText("Today so far - 2h 15m across 2 apps").assertIsDisplayed()
        composeRule.onNodeWithText("WhatsApp").assertIsDisplayed()
        composeRule.onNodeWithText("1h 35m").assertIsDisplayed()
        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("40m").assertIsDisplayed()
    }

    @Test
    fun accessWithEmptyDaySaysSo() {
        showUsageTab(FakeUsageSource(access = true))
        composeRule.onNodeWithText("Nothing counted yet today.").assertIsDisplayed()
    }

    @Test
    fun usageTabIsReachableFromTabRow() {
        showUsageTab(FakeUsageSource(access = false))
        composeRule.onNodeWithText("Usage").assertIsDisplayed()
        composeRule.onNodeWithText("History").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Usage").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("usage_access_button").assertIsDisplayed()
    }
}
