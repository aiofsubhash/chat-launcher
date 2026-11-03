package com.subhash.chatlauncher

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** Pure routing decisions: what a chat line becomes. No external app is launched here. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CommandRouterTest {

    private fun router(): CommandRouter {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val prefs = activity.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return CommandRouter(activity, prefs)
    }

    @Test
    fun whatsappDraftTargetsPackage() {
        val r = router().route("send Nirmal via whatsapp running late", emptyList())
        assertEquals(CommandRouter.Result.DraftMessage("Nirmal", "running late", "com.whatsapp"), r)
    }

    @Test
    fun genericDraftLeavesPickerToUser() {
        val r = router().route("send this message to Avinash: hello, running late", emptyList())
        assertEquals(CommandRouter.Result.DraftMessage("Avinash", "hello, running late", null), r)
    }

    @Test
    fun uberToParsesDropoff() {
        val r = router().route("uber to Kochi airport", emptyList())
        assertEquals(CommandRouter.Result.UberRide("Kochi airport"), r)
    }

    @Test
    fun rideStripsQuotes() {
        val r = router().route("ride to \"MG Road\"", emptyList())
        assertEquals(CommandRouter.Result.UberRide("MG Road"), r)
    }

    @Test
    fun focusOffClearsActiveProfile() {
        val r = router()
        r.saveProfile("Study", setOf("com.spotify.music"))
        r.activeProfile = "Study"
        assertEquals(CommandRouter.Result.Reply("Focus mode off."), r.route("focus off", emptyList()))
        assertNull(r.activeProfile)
    }

    @Test
    fun focusNameActivatesProfile() {
        val r = router()
        r.saveProfile("Study", setOf("com.spotify.music"))
        val res = r.route("focus study", emptyList())
        assertTrue(res is CommandRouter.Result.Reply && (res as CommandRouter.Result.Reply).text.contains("Study"))
        assertEquals("Study", r.activeProfile)
    }

    @Test
    fun exactAppLabelLaunches() {
        val apps = listOf(AppEntry("WhatsApp", "com.whatsapp", Intent()))
        val r = router().route("whatsapp", apps)
        assertTrue(r is CommandRouter.Result.Launch && (r as CommandRouter.Result.Launch).app.packageName == "com.whatsapp")
    }

    @Test
    fun duplicatePersonalAndWorkAppAlwaysAsks() {
        val apps = listOf(
            AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = false),
            AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = true),
        )
        val r = router().route("gmail", apps)
        assert(r is CommandRouter.Result.ChooseApp) { "expected ChooseApp, got $r" }
        org.junit.Assert.assertEquals(2, (r as CommandRouter.Result.ChooseApp).apps.size)
    }

    @Test
    fun workModeSortsWorkAppsFirst() {
        val apps = listOf(
            AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = false),
            AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = true),
        )
        org.junit.Assert.assertEquals(false, CommandRouter.prioritizeWork(apps, false).first().isWork)
        org.junit.Assert.assertEquals(true, CommandRouter.prioritizeWork(apps, true).first().isWork)
    }


    @Test
    fun explicitChannelSkipsQuestion() {
        // "via whatsapp" resolves the channel, so no inline question is needed.
        val r = router().route("send Nirmal via whatsapp running late", emptyList())
        assert(r is CommandRouter.Result.DraftMessage && (r as CommandRouter.Result.DraftMessage).pkg == "com.whatsapp") { "expected explicit whatsapp channel, got $r" }
    }

    @Test
    fun shareTargetsAreInstalledOnlyAndNotSelfOrHidden() {
        val resolved = listOf(
            "WhatsApp" to "com.whatsapp",
            "Gmail" to "com.google.android.gm",
            "Chat Launcher" to "com.subhash.chatlauncher",
            "Slack" to "com.slack",
        )
        // Focus allows Gmail + WhatsApp only: self and not-allowed entries (Slack) never appear.
        val chosen = CommandRouter.shareTargetsOf(resolved, "com.subhash.chatlauncher", setOf("com.whatsapp", "com.google.android.gm"))
        assertEquals(listOf("Gmail" to "com.google.android.gm", "WhatsApp" to "com.whatsapp"), chosen)
    }

    @Test
    fun duplicateChoicesAreUnambiguouslyLabelled() {
        val work = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = true)
        val personal = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = false)
        // Both sides of a personal/work duplicate name their profile - no bare "Gmail" vs "Gmail".
        assertEquals("BRIEFCASE Gmail (work)".replace("BRIEFCASE", "\uD83D\uDCBC"), work.choiceLabel)
        assertEquals("Gmail (personal)", personal.choiceLabel)
        assert(work.choiceLabel != personal.choiceLabel)
    }

    @Test
    fun personalAndWorkIdentityKeysPersistSeparately() {
        val work = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = true)
        val personal = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = false)
        assertEquals("com.google.android.gm", personal.identityKey)
        assertEquals("com.google.android.gm@work:unknown", work.identityKey) // null handle in unit tests
        // Focus profiles store identity keys, so allowing personal Gmail does not allow work Gmail.
        val r = router()
        r.saveProfile("Focus", setOf(personal.identityKey))
        assertEquals(setOf("com.google.android.gm"), r.profileAllowed("Focus"))
        assert(work.identityKey !in r.profileAllowed("Focus"))
    }

    @Test
    fun workLaunchFailureNeverFallsBackToPersonal() {
        val work = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = true)
        val msg = CommandRouter.launchFailureMessage(work)
        assert(msg.contains("work profile")) { msg }
        assert(msg.contains("won't open the personal app")) { msg }
        val personal = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = false)
        assertEquals("Couldn't open Gmail", CommandRouter.launchFailureMessage(personal))
    }

    @Test
    fun uberCopyClaimsDestinationTextOnly() {
        // The handoff guarantees destination text was passed, not that Uber resolved a place.
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val prefs = activity.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val msg = CommandRouter(activity, prefs).execute(CommandRouter.Result.UberRide("Kochi airport"))
        assert(msg.contains("as destination text")) { msg }
        assert(!msg.contains("filled in")) { msg }
    }


    @Test
    fun sessionResetOnlyWhenIdleAndNothingPending() {
        val thirtyOneMin = 31L * 60 * 1000
        assert(CommandRouter.shouldResetChat(thirtyOneMin, hasMessages = true, hasDraft = false, hasPendingQuestion = false))
        // Never discards an unsent draft or an unanswered question.
        assert(!CommandRouter.shouldResetChat(thirtyOneMin, hasMessages = true, hasDraft = true, hasPendingQuestion = false))
        assert(!CommandRouter.shouldResetChat(thirtyOneMin, hasMessages = true, hasDraft = false, hasPendingQuestion = true))
        // Nothing to reset in an empty chat or before 30 minutes.
        assert(!CommandRouter.shouldResetChat(thirtyOneMin, hasMessages = false, hasDraft = false, hasPendingQuestion = false))
        assert(!CommandRouter.shouldResetChat(29L * 60 * 1000, hasMessages = true, hasDraft = false, hasPendingQuestion = false))
    }

    @Test
    fun briefcaseMarksWorkEntries() {
        val work = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = true)
        val personal = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = false)
        org.junit.Assert.assertEquals("\uD83D\uDCBC Gmail", work.displayLabel)
        org.junit.Assert.assertEquals("Gmail", personal.displayLabel)
    }

    // Device bug 22:35: "call shuvo" got the "send a message to <name> on <app>" fallback.
    @Test
    fun callRequestIsNeverTheMessageFallback() {
        val r = router().route("call shuvo", emptyList())
        assertEquals(CommandRouter.Result.Dial("shuvo"), r)
        assertEquals(CommandRouter.Result.Dial("Shuvo Das"), router().route("please call Shuvo Das", emptyList()))
    }

    @Test
    fun callOnlyOpensDialerAndPlacesNoCall() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val prefs = activity.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        val note = CommandRouter(activity, prefs).execute(CommandRouter.Result.Dial("shuvo"))
        val started = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, started.action)
        assertNull(started.data)
        assertTrue(note.contains("shuvo") && note.contains("isn't filled in"))
    }
}
