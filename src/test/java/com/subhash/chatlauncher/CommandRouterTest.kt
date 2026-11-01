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
    fun briefcaseMarksWorkEntries() {
        val work = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = true)
        val personal = AppEntry("Gmail", "com.google.android.gm", android.content.Intent(), isWork = false)
        org.junit.Assert.assertEquals("\uD83D\uDCBC Gmail", work.displayLabel)
        org.junit.Assert.assertEquals("Gmail", personal.displayLabel)
    }
}
