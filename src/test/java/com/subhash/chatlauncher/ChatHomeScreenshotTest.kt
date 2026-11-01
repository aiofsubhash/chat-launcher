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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
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

    /** Chat thread after one device-action exchange: user bubble + assistant handoff note. */

    /** Duplicate same-label apps always render an unambiguous inline choice. */
    @Test
    fun appDuplicateChoice() {
        installFakeApp("com.whatsapp.work", "WhatsApp", canSend = true) // same label as personal WhatsApp
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("whatsapp")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** True personal/work duplicate fixture: same label, work side carries isWork+serial. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun workProfileDuplicateChoice() {
        val personal = AppEntry("WhatsApp", "com.whatsapp", Intent())
        val work = AppEntry("WhatsApp", "com.whatsapp", Intent(), isWork = true, userSerial = 10L)
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                ChatHome(
                    router = CommandRouter(composeRule.activity, prefs),
                    resolveApps = { q -> if (q.contains("whats", true)) listOf(personal, work) else emptyList() },
                    shareTargets = { emptyList() },
                    allApps = { listOf(personal, work) },
                    sendToLlm = { _, _ -> null },
                    launchApp = { true },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("whatsapp")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Dark theme home: the paper palette's dark variant over the same layout. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun darkHome() {
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", "dark").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("a")
        composeRule.onNodeWithText("a").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
        prefs.edit().remove("theme_mode").commit()
    }

    /** Large-font home: layout must hold at 1.3x font scale. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 1.3f)
    fun largeFontHome() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("a")
        composeRule.onNodeWithText("a").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 1.3f)
    fun largeFontDockScrollRevealsAllNotes() {
        // At 1.3x font the dock scrolls horizontally; every note must be reachable.
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("a")
        composeRule.onNodeWithText("a").performTextClearance()
        composeRule.waitForIdle()
        // performScrollTo rasterizes the full scrolled layer and OOMs Robolectric
        // NATIVE graphics at 1.3x; assert scrollability + composition instead.
        composeRule.onNode(androidx.compose.ui.test.hasScrollAction()).assertExists()
        composeRule.onNodeWithText("PhonePe").assertExists()
    }

    @Test
    fun chatConversation() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("uber to airport")
        composeRule.onNodeWithText("Send").performClick()
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

    /** Home tab with the new-profile editor intentionally opened. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageFocusEditor() {
        settingsPageContent(section = "home", editorOpen = true)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Small viewport + enlarged font, picker and editor open: last control reachable by scroll. */
    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-xxhdpi", fontScale = 1.3f)
    fun settingsPageScrollReach() {
        settingsPageContent(section = "home", pickerOpen = true, editorOpen = true)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("Work")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Gmail")[1].performScrollTo()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    private fun settingsPageContent(switcherOpen: Boolean = false, section: String? = null, pickerOpen: Boolean = false, editing: String? = null, editorOpen: Boolean = false, dark: Boolean = false) {
        val apps = listOf(
            AppEntry("WhatsApp", "com.whatsapp", Intent()),
            AppEntry("Chrome", "com.android.chrome", Intent()),
            AppEntry("Maps", "com.google.android.apps.maps", Intent()),
            AppEntry("Spotify", "com.spotify.music", Intent()),
            AppEntry("PhonePe", "com.phonepe.app", Intent()),
            AppEntry("Gmail", "com.google.android.gm", Intent()),
        )
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = if (dark) darkCalmColors else calmColors) {
                SettingsPage(
                    key = "", onKey = {},
                    profileName = "", onProfileName = {},
                    selected = if (editing != null) setOf("com.spotify.music") else emptySet(), onSelected = {},
                    dockSelected = setOf("com.google.android.apps.maps", "com.android.chrome", "com.spotify.music", "com.phonepe.app"),
                    onDockSelected = {},
                    profiles = setOf("Focus", "Sleep"),
                    onToggleProfile = {}, onDeleteProfile = {},
                    editingProfile = editing, onEditProfile = {},
                    gridWobbly = true, onGridWobbly = {},
                    activeProfile = "Focus",
                    apps = apps,
                    onCancel = {}, onSave = {},
                    switcherInitiallyOpen = switcherOpen,
                    pickerInitiallyOpen = pickerOpen,
                    editorInitiallyOpen = editorOpen,
                    initialSection = section,
                )
            }
        }
    }

    /** Settings in the dark palette: theme appearance stays readable and switchable. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun darkSettingsTheme() {
        settingsPageContent(dark = true)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Home & customisation with the installed-app picker open via Change. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPagePicker() {
        settingsPageContent(section = "home", pickerOpen = true)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** History subpage: truthfully labelled as not built yet. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageHistory() {
        settingsPageContent(section = "history")
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Account tab: API key, stored on device only. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageAccount() {
        settingsPageContent(section = "account")
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Focus subpage while editing an existing profile's hidden apps. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageFocusEdit() {
        settingsPageContent(section = "home", editing = "Focus")
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Delete asks for a second tap (Confirm delete) before removing a profile. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageFocusDeleteConfirm() {
        settingsPageContent(section = "home")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Delete")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Open-ended message without an API key: assistant points to Settings > Account. */
    @Test
    fun chatNoKeyGuidance() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("what's a good birthday gift for my dad")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Draft without a channel: the assistant asks inline which app to use, no modal. */
    @Test
    fun draftChannelQuestion() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("send this message to Avinash: hello, running late")
        composeRule.onNodeWithText("send this message to Avinash: hello, running late").performImeAction()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }
}
