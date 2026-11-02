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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
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
        installFakeApp("com.google.android.deskclock", "Clock")
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

    private fun settingsPageContent(switcherOpen: Boolean = false, section: String? = null, pickerOpen: Boolean = false, editing: String? = null, editorOpen: Boolean = false, dark: Boolean = false, historyEntries: List<HistoryEntry> = emptyList()) {
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
        composeRule.onNodeWithText("Ask AI").assertExists()
        composeRule.onNodeWithText("Ask AI").performClick()
        composeRule.waitForIdle()
        // No connection configured: local guidance, nothing sent.
        composeRule.onNodeWithText("Ask AI needs a connection first - add a key or pairing code in Settings > Account. Nothing was sent.").assertExists()
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
        composeRule.onRoot().captureRoboImage()
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

    /** Reasoning onboarding: grid-background popup dialog with the first question. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun reasoningOnboarding() {
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                ReasoningOnboardingContent(roundedFont = null, wobbly = true, onDone = {}, onSkip = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What do you want more time for?").assertExists()
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
        composeRule.onRoot().captureRoboImage()
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
