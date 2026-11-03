package com.subhash.chatlauncher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Device evidence 23:02: Normal -> Focus -> Normal consistently loses the Normal stickies.
 * Real path: persisted active_profile + the real MainActivity app resolution + the real
 * typed profile commands. No injected dock fixture.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DockProfileSwitchTest {

    private val setup = object : ExternalResource() {
        override fun before() {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val spm = Shadows.shadowOf(ctx.packageManager)
            fun reg(pkg: String, label: String) {
                val appInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
                val ri = ResolveInfo().apply {
                    activityInfo = ActivityInfo().apply {
                        packageName = pkg; name = "$pkg.MainActivity"; applicationInfo = appInfo; nonLocalizedLabel = label
                    }
                }
                spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ri)
                spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), ri)
            }
            reg("com.google.android.apps.maps", "Maps")
            reg("com.spotify.music", "Spotify")
            ctx.getSharedPreferences("chat_launcher", 0).edit()
                .putString("dock_apps", "com.google.android.apps.maps,com.spotify.music")
                .putString("active_profile", "").commit()
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(setup).around(composeRule)

    private fun say(text: String) {
        composeRule.onNodeWithText("write what you want to do").performTextInput(text)
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
    }

    private fun dockShows(label: String) =
        composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun normalFocusNormalKeepsNormalStickies() {
        composeRule.waitUntil(5_000) { dockShows("Maps") }
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
        say("focus Focus")
        say("focus off")
        composeRule.waitUntil(5_000) { dockShows("Maps") }
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
        assertTrue(dockShows("Spotify"))
    }

    /** The real Settings path: Normal -> Focus -> Normal through the Settings dialog and Save, twice. */
    @Test
    fun settingsNormalFocusNormalKeepsSavedDockAndPolicy() {
        val prefs = ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("chat_launcher", 0)
        val before = prefs.getString("dock_apps", null)
        composeRule.waitUntil(5_000) { dockShows("Maps") }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onAllNodesWithText("Use")[0].performClick() // Focus
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()
        assertEquals("Focus", prefs.getString("active_profile", null))
        // Focus policy intact: the Normal dock apps are not shown or searchable here.
        assertTrue("Maps must not be in the Focus dock", !dockShows("Maps"))
        composeRule.onNodeWithText("write what you want to do").performTextInput("Spot")
        composeRule.waitForIdle()
        assertTrue("Spotify must not be searchable in Focus", !dockShows("Spotify"))
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onAllNodesWithText("Off")[0].performClick()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()
        assertEquals(before, prefs.getString("dock_apps", null))
        composeRule.waitUntil(5_000) { dockShows("Maps") }
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
    }

    /** Device 23:36: typing an app the active profile forbids must be a local answer, not an AI call. */
    @Test
    fun typingForbiddenAppNameInFocusIsLocalPolicyAnswer() {
        composeRule.waitUntil(5_000) { dockShows("Maps") }
        say("focus Focus")
        say("Spotify")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Spotify isn't allowed in the Focus profile", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The guard must not swallow valid launches: an allowed exact app in Normal opens without a policy message. */
    @Test
    fun typingAllowedAppNameInNormalIsNotBlocked() {
        composeRule.waitUntil(5_000) { dockShows("Maps") }
        say("Spotify")
        assertTrue(composeRule.onAllNodesWithText("isn't allowed", substring = true).fetchSemanticsNodes().isEmpty())
    }
}

/** Same real path, but the activity is (re)created while Focus is the persisted profile. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DockStartInFocusTest {

    private val setup = object : ExternalResource() {
        override fun before() {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val spm = Shadows.shadowOf(ctx.packageManager)
            fun reg(pkg: String, label: String) {
                val appInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
                val ri = ResolveInfo().apply {
                    activityInfo = ActivityInfo().apply {
                        packageName = pkg; name = "$pkg.MainActivity"; applicationInfo = appInfo; nonLocalizedLabel = label
                    }
                }
                spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ri)
                spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), ri)
            }
            reg("com.google.android.apps.maps", "Maps")
            reg("com.spotify.music", "Spotify")
            ctx.getSharedPreferences("chat_launcher", 0).edit().remove("dock_apps").putString("active_profile", "Focus").commit()
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(setup).around(composeRule)

    @Test
    fun launchInFocusThenNormalShowsNormalStickies() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("focus off")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Maps").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
    }
}
