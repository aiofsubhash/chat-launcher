package com.subhash.chatlauncher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPackageManager
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatHomeScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun installFakeApp(pkg: String, label: String, canSend: Boolean = false) {
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        val spm: ShadowPackageManager = Shadows.shadowOf(pm)
        val appInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
        val ri = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.MainActivity"
                applicationInfo = appInfo
                nonLocalizedLabel = label
            }
        }
        spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ri)
        spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), ri)
        if (canSend) spm.addResolveInfoForIntent(Intent(Intent.ACTION_SEND).setType("text/plain"), ri)
    }

    @Before
    fun installApps() {
        installFakeApp("com.whatsapp", "WhatsApp", canSend = true)
        installFakeApp("com.whatsapp.w4b", "WhatsApp Business", canSend = true)
        installFakeApp("com.android.chrome", "Chrome")
        installFakeApp("com.google.android.apps.maps", "Maps")
        installFakeApp("com.spotify.music", "Spotify")
        installFakeApp("com.phonepe.app", "PhonePe")
        installFakeApp("com.instagram.android", "Instagram")
        installFakeApp("com.android.camera2", "Camera")
        installFakeApp("com.google.android.gm", "Gmail", canSend = true)
    }

    @Test
    fun initialHome() {
        composeRule.waitForIdle()
        // Fake apps install in @Before; poke the composer once so the dock recomputes with them.
        composeRule.onNodeWithText("write what you want to do").performTextInput("a")
        composeRule.onNodeWithText("a").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun appSuggestions() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("whats")
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }


    /** Settings landing page: profile row above nav, sections as destinations. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageInitial() {
        settingsPageContent()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Settings landing page with the current-profile switcher open. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageSwitcher() {
        settingsPageContent(switcherOpen = true)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Home & customisation subpage: grid toggle + compact home-app summary with Change. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageHome() {
        settingsPageContent(section = "home")
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Focus subpage: profiles, new profile, hidden apps. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageFocus() {
        settingsPageContent(section = "focus")
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    private fun settingsPageContent(switcherOpen: Boolean = false, section: String? = null) {
        val apps = listOf(
            AppEntry("WhatsApp", "com.whatsapp", Intent()),
            AppEntry("Chrome", "com.android.chrome", Intent()),
            AppEntry("Maps", "com.google.android.apps.maps", Intent()),
            AppEntry("Spotify", "com.spotify.music", Intent()),
            AppEntry("PhonePe", "com.phonepe.app", Intent()),
            AppEntry("Gmail", "com.google.android.gm", Intent()),
        )
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                SettingsPage(
                    key = "", onKey = {},
                    profileName = "", onProfileName = {},
                    selected = setOf("com.spotify.music"), onSelected = {},
                    dockSelected = setOf("com.google.android.apps.maps", "com.android.chrome", "com.spotify.music", "com.phonepe.app"),
                    onDockSelected = {},
                    profiles = setOf("Study", "Sleep"),
                    onToggleProfile = {}, onDeleteProfile = {},
                    gridWobbly = true, onGridWobbly = {},
                    activeProfile = "Study",
                    apps = apps,
                    onCancel = {}, onSave = {},
                    switcherInitiallyOpen = switcherOpen,
                    initialSection = section,
                )
            }
        }
    }

    @Test
    fun draftPicker() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("send this message to Avinash: hello, running late")
        composeRule.onNodeWithText("send this message to Avinash: hello, running late").performImeAction()
        composeRule.waitForIdle()
        composeRule.onAllNodes(isRoot())[1].captureRoboImage()
    }
}
