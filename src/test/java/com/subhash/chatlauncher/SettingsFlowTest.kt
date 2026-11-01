package com.subhash.chatlauncher

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Executable settings behavior against a real CommandRouter: Save applies staged edits, Cancel discards them. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var router: CommandRouter

    private val apps = listOf(
        AppEntry("WhatsApp", "com.whatsapp", Intent()),
        AppEntry("Chrome", "com.android.chrome", Intent()),
        AppEntry("Maps", "com.google.android.apps.maps", Intent()),
        AppEntry("Spotify", "com.spotify.music", Intent()),
        AppEntry("PhonePe", "com.phonepe.app", Intent()),
        AppEntry("Gmail", "com.google.android.gm", Intent()),
    )

    @Before
    fun setUp() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("flow_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        router = CommandRouter(composeRule.activity, prefs)
        router.saveProfile("Study", setOf("com.spotify.music"))
        router.saveProfile("Sleep", setOf("com.android.chrome"))
        router.activeProfile = "Study"
    }

    private fun openFocusSettings() {
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                SettingsDialog(
                    router = router,
                    allApps = { apps },
                    dockApps = router.dockApps,
                    onDockApps = { router.dockApps = it },
                    gridWobbly = true, onGridWobbly = {},
                    activeProfile = router.activeProfile, onActiveProfile = { router.activeProfile = it },
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Focus").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun cancelDiscardsProfileToggle() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Use")[0].performClick() // stage Sleep as active
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals("Study", router.activeProfile)
    }

    @Test
    fun saveAppliesProfileToggle() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Use")[0].performClick()
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("Sleep", router.activeProfile)
    }

    @Test
    fun deleteNeedsSecondTap() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Delete")[1].performClick() // Sleep's Delete -> Confirm delete
        composeRule.onNodeWithText("Confirm delete").assertExists()
        composeRule.onNodeWithText("Save").performClick()
        assertTrue(router.profileNames().contains("Sleep")) // single tap + Save does not delete
    }

    @Test
    fun confirmedDeleteAppliesOnSave() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Delete")[1].performClick()
        composeRule.onNodeWithText("Confirm delete").performClick()
        composeRule.onNodeWithText("Save").performClick()
        assertFalse(router.profileNames().contains("Sleep")) // confirmed delete applies on Save
    }

    @Test
    fun editProfileHiddenAppsSaves() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Edit")[0].performClick() // Study
        composeRule.onNodeWithText("Apps hidden in Study:").assertExists()
        composeRule.onNodeWithText("WhatsApp").performClick() // add WhatsApp to Study's hidden apps
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(setOf("com.spotify.music", "com.whatsapp"), router.profileHidden("Study"))
    }

    @Test
    fun cancelDiscardsNewProfile() {
        openFocusSettings()
        composeRule.onNode(hasSetTextAction()).performTextInput("Work")
        composeRule.onNodeWithText("Cancel").performClick()
        assertFalse(router.profileNames().contains("Work"))
    }

    @Test
    fun newProfileSavesWithChosenApps() {
        openFocusSettings()
        composeRule.onNode(hasSetTextAction()).performTextInput("Work")
        composeRule.onNodeWithText("Gmail").performClick()
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(setOf("com.google.android.gm"), router.profileHidden("Work"))
    }
}
