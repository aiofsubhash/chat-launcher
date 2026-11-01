package com.subhash.chatlauncher

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DebugFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
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
            .getSharedPreferences("dbg3", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        router = CommandRouter(composeRule.activity, prefs)
        router.saveProfile("Study", setOf("com.spotify.music"))
        router.saveProfile("Sleep", setOf("com.android.chrome"))
        router.activeProfile = "Study"
    }

    @Test
    fun newProfileDebug() {
        var captured = setOf<String>()
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                var selected by remember { mutableStateOf(setOf<String>()) }
                var profileName by remember { mutableStateOf("") }
                SettingsPage(
                    key = "", onKey = {},
                    profileName = profileName, onProfileName = { profileName = it },
                    selected = selected, onSelected = { selected = it; captured = it },
                    dockSelected = emptySet(), onDockSelected = {},
                    profiles = emptySet(),
                    onToggleProfile = {}, onDeleteProfile = {},
                    editingProfile = null, onEditProfile = {},
                    gridWobbly = true, onGridWobbly = {},
                    activeProfile = null,
                    apps = apps,
                    onCancel = {}, onSave = {},
                    editorInitiallyOpen = false,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("+ New profile").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Work")
        composeRule.onNodeWithText("Gmail").performClick()
        composeRule.waitForIdle()
        assertEquals(setOf("com.google.android.gm"), captured)
    }
}
