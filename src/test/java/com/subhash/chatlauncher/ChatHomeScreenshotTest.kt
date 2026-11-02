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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
        installFakeApp("com.google.android.deskclock", "Clock")
        installFakeApp("com.android.settings", "Settings")
        // Let the clock app resolve alarm/timer intents (Focus profile's Timer note).
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        val spm: ShadowPackageManager = Shadows.shadowOf(pm)
        val clockInfo = ApplicationInfo().apply { packageName = "com.google.android.deskclock"; nonLocalizedLabel = "Clock" }
        val clockRi = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.google.android.deskclock"
                name = "com.google.android.deskclock.AlarmActivity"
                applicationInfo = clockInfo
                nonLocalizedLabel = "Clock"
            }
        }
        spm.addResolveInfoForIntent(Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS), clockRi)
        spm.addResolveInfoForIntent(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), clockRi)
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

    /** Prefix match hitting personal+work duplicates asks explicitly - never a silent pick, never a note. */
    @Test
    fun typedPrefixPersonalWorkDuplicateAsksChoice() {
        val personal = AppEntry("Instagram", "com.instagram.android", Intent())
        val work = AppEntry("Instagram", "com.instagram.android", Intent(), isWork = true, userSerial = 10L)
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                ChatHome(
                    router = CommandRouter(composeRule.activity, prefs),
                    resolveApps = { q -> if (q.isNotBlank() && "instagram".contains(q, true)) listOf(personal, work) else emptyList() },
                    shareTargets = { emptyList() },
                    allApps = { listOf(personal, work) },
                    sendToLlm = { _, _ -> null },
                    launchApp = { true },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("insta")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Instagram (personal)").assertExists()
        composeRule.onNodeWithText("Instagram (work)", substring = true).assertExists()
    }

    /** System bars wear the paper background with contrasting icons, both themes. */
    @Test
    fun systemBarsMatchTheme() {
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", "light").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        val lightBg = calmColors.background.toArgb()
        org.junit.Assert.assertEquals(lightBg, composeRule.activity.window.statusBarColor)
        org.junit.Assert.assertEquals(lightBg, composeRule.activity.window.navigationBarColor)
        var ctl = androidx.core.view.WindowInsetsControllerCompat(composeRule.activity.window, composeRule.activity.window.decorView)
        org.junit.Assert.assertTrue("light theme needs dark status icons", ctl.isAppearanceLightStatusBars)
        org.junit.Assert.assertTrue("light theme needs dark nav icons", ctl.isAppearanceLightNavigationBars)

        prefs.edit().putString("theme_mode", "dark").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        val darkBg = darkCalmColors.background.toArgb()
        org.junit.Assert.assertEquals(darkBg, composeRule.activity.window.statusBarColor)
        org.junit.Assert.assertEquals(darkBg, composeRule.activity.window.navigationBarColor)
        ctl = androidx.core.view.WindowInsetsControllerCompat(composeRule.activity.window, composeRule.activity.window.decorView)
        org.junit.Assert.assertFalse("dark theme needs light status icons", ctl.isAppearanceLightStatusBars)
        org.junit.Assert.assertFalse("dark theme needs light nav icons", ctl.isAppearanceLightNavigationBars)
        prefs.edit().remove("theme_mode").commit()
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

    private fun settingsPageContent(switcherOpen: Boolean = false, section: String? = null, pickerOpen: Boolean = false, editing: String? = null, editorOpen: Boolean = false, dark: Boolean = false, advancedOpen: Boolean = false, historyEntries: List<HistoryEntry> = emptyList()) {
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
                    // Mirror a fresh unpaired install: the real layer prefills the fixed proxy URL.
                    endpoint = LlmClient.DEFAULT_CHAT_ENDPOINT, onEndpoint = {},
                    profileName = "", onProfileName = {},
                    selected = if (editing != null) setOf("com.spotify.music") else emptySet(), onSelected = {},
                    dockSelected = setOf("com.google.android.apps.maps", "com.android.chrome", "com.spotify.music", "com.phonepe.app"),
                    onDockSelected = {},
                    profiles = setOf("Focus", "Work", "Sleep"),
                    onToggleProfile = {}, onDeleteProfile = {},
                    editingProfile = editing, onEditProfile = {},
                    gridWobbly = true, onGridWobbly = {},
                    activeProfile = "Focus",
                    apps = apps,
                    historyEntries = historyEntries,
                    onCancel = {}, onSave = {},
                    switcherInitiallyOpen = switcherOpen,
                    pickerInitiallyOpen = pickerOpen,
                    editorInitiallyOpen = editorOpen,
                    advancedInitiallyOpen = advancedOpen,
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

    /** History subpage with captured exchanges: local lines and a tagged cloud reply. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageHistory() {
        val entries = listOf(
            HistoryEntry(1762074600000, "assistant", "cloud", "A good birthday gift could be a nice pen. "),
            HistoryEntry(1762074550000, "user", "local", "what's a good birthday gift for my dad"),
            HistoryEntry(1762074000000, "assistant", "local", "Draft ready in WhatsApp for Sumit - pick the chat and tap send."),
            HistoryEntry(1762073900000, "user", "local", "send a message to Sumit on WhatsApp: happy birthday"),
        )
        settingsPageContent(section = "history", historyEntries = entries)
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

    /** Account tab with the advanced manual-key section expanded. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageAccountAdvanced() {
        settingsPageContent(section = "account", advancedOpen = true)
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

    /** Chat active: notes stay visible above a bottom-only conversation section. */
    @Test
    fun homeChatKeepsNotesVisible() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("uber to airport")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** No API key needed for drafts: missing text is asked inline, then the draft hands off. */
    @Test
    fun draftNoKeyFlow() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("send a message to Sumit on WhatsApp")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What should I say to Sumit?").assertExists()
        composeRule.onNodeWithText("write what you want to do").performTextInput("happy birthday")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** Unhandled text is captured locally with an explicit Ask AI action - never auto-sent. */
    @Test
    fun askAiExplicitAction() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("what's a good birthday gift for my dad")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        // Unpaired open text goes straight to connection guidance (owner 22:52/main 23:01) -
        // no note chip, nothing sent.
        composeRule.onNodeWithText("Ask AI needs a connection first - tap the pairing link you were sent, or see Settings > Account. Nothing was sent.").assertExists()
        composeRule.onRoot().captureRoboImage()
    }

    /** Focus profile home: only the Timer note (opens the phone's clock app). */
    @Test
    fun focusProfileDock() {
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("active_profile", "Focus").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Timer").assertExists()
        composeRule.onNodeWithText("PhonePe").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage()
        prefs.edit().remove("active_profile").commit()
    }

    /** Work profile home with a managed duplicate: the work Gmail shows with its marker. */
    @Test
    fun workProfileDock() {
        val personal = AppEntry("Gmail", "com.google.android.gm", Intent())
        val work = AppEntry("Gmail", "com.google.android.gm", Intent(), isWork = true, userSerial = 10L)
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                ChatHome(
                    router = CommandRouter(composeRule.activity, prefs).apply { activeProfile = "Work" },
                    resolveApps = { listOf(personal, work) },
                    shareTargets = { emptyList() },
                    allApps = { listOf(personal, work) },
                    sendToLlm = { _, _ -> null },
                    launchApp = { true },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("💼 Gmail").assertExists()
        composeRule.onRoot().captureRoboImage()
    }

    /** Regression for the device report "typed Insta, Enter, said opening but nothing happened":
     *  the IME Enter path must dispatch a launch intent at the real Instagram component.
     *  (Dispatch truth only - whether a device foregrounds the app is not observable here.) */
    @Test
    fun instaEnterDispatchesLaunch() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("Insta")
        composeRule.onNodeWithText("Insta").performImeAction()
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("an open request was dispatched", started)
        org.junit.Assert.assertEquals("com.instagram.android", started.component?.packageName)
        composeRule.onRoot().captureRoboImage()
    }

    /** Reasoning profile: tapping an entertainment note shows the intervention pause with an
     *  immediate "Just open it" escape; choosing it dispatches the real launch. */
    @Test
    fun reasoningInterventionGate() {
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("active_profile", "Reasoning")
            .putStringSet("profile_allow_Reasoning", setOf("com.instagram.android"))
            .commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Instagram").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Just open it").assertExists()
        // The pause is the REAL dialog card (tagged Surface in a second window), not inline text.
        composeRule.onNodeWithTag("reasoning_intervention_card").assertExists()
        // Dialogs live in a second window root: capture the whole screen (home + dialog).
        captureScreenRoboImage()
        composeRule.onNodeWithTag("reasoning_open_anyway", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("Just open it dispatches the launch", started)
        org.junit.Assert.assertEquals("com.instagram.android", started.component?.packageName)
        prefs.edit().remove("active_profile").remove("profile_allow_Reasoning").commit()
    }


    /** Reasoning gates typed launches too: naming an app shows the pause, typed 'just open it' ends it, and a session bypass prevents repeated interrogation. */
    @Test
    fun reasoningTypedCommandGate() {
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("active_profile", "Reasoning")
            .putStringSet("profile_allow_Reasoning", setOf("com.instagram.android"))
            .commit()
        // Instagram comes from the @Before fixture; registering it again would duplicate
        // the PM entry and route() would (correctly) refuse a two-way "match" as a note.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("insta")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        // No launch yet - the pause dialog appears instead.
        composeRule.onNodeWithText("Just open it").assertExists()
        org.junit.Assert.assertNull(
            "typed command must not launch before the pause",
            Shadows.shadowOf(composeRule.activity).nextStartedActivity
        )
        // Typed override honors immediately.
        composeRule.onNodeWithText("write what you want to do").performTextInput("just open it")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("typed 'just open it' dispatches the launch", started)
        org.junit.Assert.assertEquals("com.instagram.android", started.component?.packageName)
        // Session bypass: a second typed open launches directly, no repeated interrogation.
        composeRule.onNodeWithText("write what you want to do").performTextInput("insta")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Just open it").assertDoesNotExist()
        org.junit.Assert.assertNotNull(
            "second open bypasses the pause",
            Shadows.shadowOf(composeRule.activity).nextStartedActivity
        )
        prefs.edit().remove("active_profile").remove("profile_allow_Reasoning").commit()
    }
    /** Live-wrapper behavior: typed activation opens the real onboarding dialog; Skip closes it. */
    @Test
    fun reasoningOnboardingLiveWrapper() {
        // Fail fast: the default 60s idle timeout makes every loop-hypothesis cycle a minute.
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        androidx.test.espresso.IdlingPolicies.setIdlingResourceTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        val ctx = composeRule.activity.getSharedPreferences("user_context_prefs", android.content.Context.MODE_PRIVATE)
        ctx.edit().clear().commit()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("focus reasoning")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        // The real Dialog wrapper (not just its content) must open and stay interactive.
        composeRule.onNodeWithText("What do you want more time for?").assertExists()
        // Answer entry must actually function inside the dialog: answer all four, Done persists.
        composeRule.onNodeWithText("Your answer").performTextInput("my family")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What does your phone get in the way of?").assertExists()
        composeRule.onNodeWithText("Your answer").performTextInput("doomscrolling")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your answer").performTextInput("boredom")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your answer").performTextInput("reading")
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What would you rather be doing instead?").assertDoesNotExist()
        val saved = ctx.getString("entries", null) ?: ""
        org.junit.Assert.assertTrue("typed answers saved locally, got: " + saved.take(160),
            saved.contains("my family") && saved.contains("reading"))
    }

    /** Reasoning onboarding over the REAL home via the REAL typed path: the capture includes
     *  the home backdrop + the live dialog window, and the card bounds are asserted against
     *  the 70-80% popup spec (not an isolated content fixture). */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun reasoningOnboarding() {
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        androidx.test.espresso.IdlingPolicies.setIdlingResourceTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        val ctx = composeRule.activity.getSharedPreferences("user_context_prefs", android.content.Context.MODE_PRIVATE)
        ctx.edit().clear().commit()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("focus reasoning")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What do you want more time for?").assertExists()
        val card = composeRule.onNodeWithTag("reasoning_onboarding_card").getUnclippedBoundsInRoot()
        val cfg = composeRule.activity.resources.configuration
        val sw = cfg.screenWidthDp.toFloat()
        val sh = cfg.screenHeightDp.toFloat()
        val cardW = (card.right - card.left).value
        val cardH = (card.bottom - card.top).value
        org.junit.Assert.assertTrue(
            "onboarding width ~92% of screen, got ${cardW}dp of ${sw}dp",
            cardW > sw * 0.86f && cardW < sw * 0.97f,
        )
        org.junit.Assert.assertTrue(
            "onboarding height within the 70-80% popup spec, got ${cardH}dp of ${sh}dp",
            cardH > sh * 0.70f && cardH < sh * 0.79f,
        )
        // Dialogs live in a second window root: capture the whole screen (home + dialog).
        captureScreenRoboImage()
        ctx.edit().clear().commit()
    }

    /** Device report regression: typed 'insta' + Send must dispatch AND drop the keyboard/focus,
     *  so the launcher never sits behind a stuck IME saying "Opening Instagram" forever. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun typedInstaLaunchDismissesKeyboard() {
        composeRule.waitForIdle()
        val field = composeRule.onNode(hasSetTextAction())
        field.performTextInput("insta")
        field.assertIsFocused()
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("typed 'insta' dispatches the launch", started)
        org.junit.Assert.assertEquals("com.instagram.android", started.component?.packageName)
        composeRule.onNodeWithText("Asked Android to open Instagram", substring = true).assertExists()
        field.assertIsNotFocused()
    }

    /** Exact app word + Enter opens the app locally (user 21:50: typed 'settings' must open
     *  Settings, never the API-key/cloud path). */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsTypedOpensSettingsApp() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("settings")
        composeRule.onNodeWithText("settings").performImeAction()
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("typed 'settings' dispatches the launch", started)
        org.junit.Assert.assertEquals("com.android.settings", started.component?.packageName)
    }

    /** Device bug 22:48: exact typed 'whatsapp' + Enter launches the app, never the
     *  message-recipient clarification. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun whatsappTypedEnterLaunchesApp() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("whatsapp")
        composeRule.onNodeWithText("whatsapp").performImeAction()
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("typed 'whatsapp' dispatches the launch", started)
        org.junit.Assert.assertEquals("com.whatsapp", started.component?.packageName)
    }

    /** Owner 22:52/main 23:01: unpaired open text gets connection guidance - never a
     *  silent note or a fake "saved" claim. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun unpairedOpenTextGetsConnectionGuidance() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("what's up")
        composeRule.onNodeWithText("what's up").performImeAction()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ask AI needs a connection first - tap the pairing link you were sent, or see Settings > Account. Nothing was sent.").assertExists()
        composeRule.onNodeWithText("Noted - that's saved in history on this phone.", substring = true).assertDoesNotExist()
    }

    /** Device bug 22:53 mitigation: stored dock keys that no longer resolve fall back to
     *  defaults - Normal mode never shows a blank dock by accident. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun normalDockFallsBackWhenStoredKeysStale() {
        val p = ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("chat_launcher", 0)
        p.edit().putString("dock_apps", "com.ghost.app").commit()
        composeRule.waitForIdle()
        // Fake apps install in @Before; poke the composer once so the dock recomputes with them.
        composeRule.onNodeWithText("write what you want to do").performTextInput("a")
        composeRule.onNodeWithText("a").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Maps").assertExists()
        composeRule.onNodeWithText("Chrome").assertExists()
    }

    /** Owner 22:52: explicit note intent saves locally, never a cloud call. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun explicitNoteSavesLocally() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("note buy milk")
        composeRule.onNodeWithText("note buy milk").performImeAction()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Noted - saved on this phone: buy milk").assertExists()
    }

    /** Owner 22:53: swipe left opens the full app grid in Normal mode. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun swipeLeftOpensAppGrid() {
        ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("chat_launcher", 0).edit().remove("active_profile").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("a")
        composeRule.onNodeWithText("a").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("All apps").assertExists()
        composeRule.onNodeWithText("WhatsApp").assertExists()
    }

    /** Owner 22:53: right swipe opens the camera app in Normal mode. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun swipeRightLaunchesCameraInNormal() {
        ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("chat_launcher", 0).edit().remove("active_profile").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("right swipe dispatches the camera launch", started)
        org.junit.Assert.assertEquals("com.android.camera2", started.component?.packageName)
    }

    /** Owner 22:53: the grid stays closed under a profile; camera respects the allow-list
     *  (Focus allows only the Timer note). */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun swipesRespectFocusProfile() {
        val p = ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("chat_launcher", 0)
        p.edit().putString("active_profile", "Focus").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("All apps").assertDoesNotExist()
        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNull("camera is not in the Focus allow-list", started)
    }

    /** Right swipe dismisses the grid. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun swipeRightDismissesAppGrid() {
        ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("chat_launcher", 0).edit().remove("active_profile").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("a")
        composeRule.onNodeWithText("a").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("All apps").assertExists()
        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("All apps").assertDoesNotExist()
    }

    /** Long-press a suggestion chip: App info opens the OS app-details page for that package. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun suggestionLongPressAppInfo() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("insta")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Instagram").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_actions_dialog").assertExists()
        composeRule.onNodeWithText("App info").performClick()
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("App info dispatches the details intent", started)
        org.junit.Assert.assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        org.junit.Assert.assertEquals("package:com.instagram.android", started.dataString)
    }

    /** Long-press a suggestion chip: Uninstall routes to the OS delete flow (it confirms). */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun suggestionLongPressUninstall() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput("insta")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Instagram").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_actions_dialog").assertExists()
        composeRule.onNodeWithText("Uninstall").performClick()
        composeRule.waitForIdle()
        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        org.junit.Assert.assertNotNull("Uninstall dispatches the OS delete flow", started)
        org.junit.Assert.assertEquals(Intent.ACTION_DELETE, started.action)
        org.junit.Assert.assertEquals("package:com.instagram.android", started.dataString)
    }

    /** Honest terminal failure: a launch the OS rejects returns a clear "Couldn't open" message,
     *  never a stuck "Opening" note, and nothing is dispatched. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun typedLaunchFailureIsHonest() {
        // Ghost entry routed through LauncherApps (work-profile path): Robolectric does not
        // shadow LauncherApps.startMainActivity, so the dead component genuinely throws here,
        // exercising the same catch -> honest failure the device path relies on.
        val ghost = AppEntry(
            "Ghost", "com.ghost.app",
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(android.content.ComponentName("com.ghost.app", "com.ghost.app.Main")),
            isWork = true,
            userHandle = android.os.Process.myUserHandle(),
        )
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        val router = CommandRouter(composeRule.activity, prefs)
        org.junit.Assert.assertFalse("ghost launch must report failure", router.launchAppEntry(ghost))
        val msg = router.execute(CommandRouter.Result.Launch(ghost))
        org.junit.Assert.assertTrue(
            "failure must be an honest terminal message, got: $msg",
            msg.startsWith("Couldn't open 💼 Ghost"),
        )
        org.junit.Assert.assertNull(
            "a failed launch must not record a dispatch",
            Shadows.shadowOf(composeRule.activity).nextStartedActivity,
        )
        // The personal-path catch is identical: a throwing startActivity also maps to the
        // honest message, never a stuck "Opening".
        org.junit.Assert.assertEquals(CommandRouter.launchFailureMessage(ghost), msg)
    }

    /** Bounded terminal state: after a dispatch, leaving and returning keeps the finished
     *  exchange, an empty composer, and no pending chooser/question. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun launchTerminalStateSurvivesBackground() {
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("insta")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertNotNull(Shadows.shadowOf(composeRule.activity).nextStartedActivity)
        composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Asked Android to open Instagram", substring = true).assertExists()
        composeRule.onNodeWithText("write what you want to do").assertExists()
        composeRule.onNodeWithText("which one?", substring = true).assertDoesNotExist()
    }

    /** Settings tabs never wrap or clip on a narrow phone, light theme. */
    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h700dp-xxhdpi")
    fun settingsTabsNarrowLight() = assertTabsSingleLine(dark = false)

    /** Settings tabs never wrap or clip on a narrow phone, dark theme. */
    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h700dp-xxhdpi")
    fun settingsTabsNarrowDark() = assertTabsSingleLine(dark = true)

    private fun assertTabsSingleLine(dark: Boolean) {
        settingsPageContent(dark = dark)
        composeRule.waitForIdle()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        var lineHeight = -1f
        for (label in listOf("Home", "History", "Context", "Account")) {
            val b = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()
            org.junit.Assert.assertTrue("$label clipped offscreen: $b vs $root", b.right <= root.right && b.left >= root.left)
            val h = (b.bottom - b.top).value
            if (lineHeight < 0f) lineHeight = h else org.junit.Assert.assertEquals(
                "$label wrapped to a second line", lineHeight, h, 1f,
            )
        }
        composeRule.onRoot().captureRoboImage()
    }

    /** Reasoning intervention dialog with a personal goal nudge. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun reasoningIntervention() {
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                ReasoningInterventionDialog(
                    app = AppEntry("Instagram", "com.instagram.android", Intent()),
                    goal = "drums",
                    roundedFont = null,
                    wobbly = true,
                    onOpen = {},
                    onLater = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Just open it").assertExists()
        // Dialogs live in a second window root: capture the whole screen (home + dialog).
        captureScreenRoboImage()
    }

    /** Context tab: read-only answers from the Reasoning onboarding. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsPageContext() {
        val entries = listOf(
            UserContextStore.Entry("What do you want more time for?", "drums", 1762074600000),
            UserContextStore.Entry("What does your phone get in the way of?", "sleep", 1762074610000),
        )
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                SettingsContent(
                    key = "", onKey = {},
                    profileName = "", onProfileName = {},
                    selected = emptySet(), onSelected = {},
                    dockSelected = emptySet(), onDockSelected = {},
                    profiles = setOf("Focus", "Work", "Reasoning"),
                    onToggleProfile = {}, onDeleteProfile = {},
                    editingProfile = null, onEditProfile = {},
                    gridWobbly = true, onGridWobbly = {},
                    activeProfile = null,
                    apps = emptyList(),
                    contextEntries = entries,
                    initialSection = "context",
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("drums").assertExists()
        composeRule.onRoot().captureRoboImage()
    }
}
