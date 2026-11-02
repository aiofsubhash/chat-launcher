package com.subhash.chatlauncher

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
        // Allow-list semantics: these are the apps each profile CAN open.
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
    }

    @Test
    fun cancelDiscardsProfileToggle() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Use")[2].performClick() // stage Sleep as active
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals("Study", router.activeProfile)
    }

    @Test
    fun saveAppliesProfileToggle() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Use")[3].performClick() // Sleep (Focus, Work, Reasoning, Sleep, Study)
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("Sleep", router.activeProfile)
    }

    @Test
    fun deleteNeedsSecondTap() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Delete")[0].performClick() // Sleep's Delete -> Confirm delete (built-ins have no Delete)
        composeRule.onNodeWithText("Confirm delete").assertExists()
        composeRule.onNodeWithText("Save").performClick()
        assertTrue(router.profileNames().contains("Sleep")) // single tap + Save does not delete
    }

    @Test
    fun confirmedDeleteAppliesOnSave() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Delete")[0].performClick() // Sleep
        composeRule.onNodeWithText("Confirm delete").performClick()
        composeRule.onNodeWithText("Save").performClick()
        assertFalse(router.profileNames().contains("Sleep")) // confirmed delete applies on Save
    }

    @Test
    fun editProfileAllowedAppsSaves() {
        openFocusSettings()
        composeRule.onAllNodesWithText("Edit")[4].performClick() // Study (Focus, Work, Reasoning, Sleep, Study)
        composeRule.onNodeWithText("Apps \"Study\" can open:").assertExists()
        // Reachability proof, not just handler wiring: scroll the row into the visible
        // viewport, require real on-screen pixels, then tap it with a real coordinate click.
        composeRule.onNodeWithTag("profile_app_com.whatsapp").performScrollTo()
        composeRule.waitForIdle()
        val row = composeRule.onNodeWithTag("profile_app_com.whatsapp").fetchSemanticsNode()
        assertTrue("editor row must have on-screen pixels (bounds were " + row.boundsInRoot + ")",
            row.boundsInRoot.width > 0 && row.boundsInRoot.height > 0)
        composeRule.onNodeWithTag("profile_app_com.whatsapp").performClick() // add WhatsApp to Study's allowed apps
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(setOf("com.spotify.music", "com.whatsapp"), router.profileAllowed("Study"))
    }

    @Test
    fun cancelDiscardsNewProfile() {
        openFocusSettings()
        composeRule.onNodeWithText("+ New profile").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Trip")
        composeRule.onNodeWithText("Cancel").performClick()
        assertFalse(router.profileNames().contains("Trip"))
    }

    @Test
    fun newProfileSavesWithChosenApps() {
        openFocusSettings()
        composeRule.onNodeWithText("+ New profile").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Trip")
        composeRule.onNodeWithText("Gmail").performScrollTo() // 6th checkbox: below the laid-out viewport
        composeRule.onNodeWithText("Gmail").performClick()
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(setOf("com.google.android.gm"), router.profileAllowed("Trip"))
    }

    @Test
    fun builtInsListedWithoutDelete() {
        openFocusSettings()
        composeRule.onNodeWithText("Focus").assertExists()
        composeRule.onNodeWithText("Work").assertExists()
        composeRule.onNodeWithText("Reasoning").assertExists()
        // Only the two custom profiles get a Delete action.
        assertEquals(2, composeRule.onAllNodesWithText("Delete").fetchSemanticsNodes().size)
    }
}
