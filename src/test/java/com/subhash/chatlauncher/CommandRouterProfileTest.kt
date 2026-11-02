package com.subhash.chatlauncher

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Allow-list profiles, built-ins, migration and deterministic routing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandRouterProfileTest {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var router: CommandRouter

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("router_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        router = CommandRouter(activity, prefs)
    }

    @Test
    fun builtInProfilesAlwaysExist() {
        assertTrue(router.profileNames().containsAll(listOf("Focus", "Work")))
    }

    @Test
    fun focusDefaultsToTimerOnly() {
        assertEquals(setOf(CommandRouter.TIMER_KEY), router.profileAllowed("Focus"))
    }

    @Test
    fun workEffectiveAddsManagedApps() {
        val eff = router.effectiveAllowed("Work", setOf("com.google.android.gm@work:10"))
        assertTrue("com.google.android.gm@work:10" in eff)
    }

    @Test
    fun workAdditionsEditable() {
        router.saveProfile("Work", setOf("com.spotify.music"))
        val eff = router.effectiveAllowed("Work", setOf("com.google.android.gm@work:10"))
        assertEquals(setOf("com.spotify.music", "com.google.android.gm@work:10"), eff)
    }

    @Test
    fun builtInsCannotBeDeleted() {
        router.deleteProfile("Focus")
        router.deleteProfile("Work")
        assertTrue(router.profileNames().containsAll(listOf("Focus", "Work")))
    }

    @Test
    fun noProfileMeansEverythingOpen() {
        assertNull(router.allowedNow(setOf("x@work:1")))
    }

    @Test
    fun activeProfileTrimsToAllowList() {
        router.activeProfile = "Focus"
        assertEquals(setOf(CommandRouter.TIMER_KEY), router.allowedNow(emptySet()))
    }

    // ---- Migration from hidden lists: meaning preserved, data kept, collisions renamed.

    @Test
    fun migrationInvertsHiddenToAllowed() {
        // Old format: "Study" hid Spotify.
        prefs.edit().putStringSet("profile_names", setOf("Study")).putStringSet("profile_Study", setOf("com.spotify.music")).commit()
        val installed = setOf("com.spotify.music", "com.android.chrome", "com.whatsapp")
        assertTrue(router.migrateHiddenProfiles(installed))
        assertEquals(setOf("com.android.chrome", "com.whatsapp"), router.profileAllowed("Study"))
        assertNull(prefs.getStringSet("profile_Study", null))
        // Backup of the old set survives.
        assertEquals(setOf("com.spotify.music"), prefs.getStringSet("profile_hidden_backup_Study", null))
    }

    @Test
    fun migrationDeferredWithoutAppList() {
        prefs.edit().putStringSet("profile_Study", setOf("com.spotify.music")).commit()
        assertFalse(router.migrateHiddenProfiles(emptySet()))
        // Old data untouched, migration not marked done.
        assertEquals(setOf("com.spotify.music"), prefs.getStringSet("profile_Study", null))
        assertFalse(prefs.getBoolean("profiles_allow_migrated", false))
    }

    @Test
    fun migrationCollisionRenames() {
        prefs.edit().putStringSet("profile_names", setOf("Focus")).putStringSet("profile_Focus", setOf("com.whatsapp")).commit()
        router.activeProfile = "Focus"
        val installed = setOf("com.whatsapp", "com.android.chrome")
        assertTrue(router.migrateHiddenProfiles(installed))
        assertEquals(setOf("com.android.chrome"), router.profileAllowed("Focus (migrated)"))
        // Built-in Focus keeps its default; the user's old profile keeps its meaning and stays active.
        assertEquals(setOf(CommandRouter.TIMER_KEY), router.profileAllowed("Focus"))
        assertEquals("Focus (migrated)", router.activeProfile)
    }

    // ---- Deterministic routing: message drafts never need an API key.

    @Test
    fun messageWithoutTextAsks() {
        val r = router.route("send a message to Sumit on WhatsApp", emptyList())
        assertTrue(r is CommandRouter.Result.DraftMessage)
        val d = r as CommandRouter.Result.DraftMessage
        assertEquals("Sumit", d.to)
        assertNull(d.text)
        assertEquals("com.whatsapp", d.pkg)
    }

    @Test
    fun messageWithChannelAndTextRoutes() {
        val d = router.route("send Sumit hello on whatsapp", emptyList()) as CommandRouter.Result.DraftMessage
        assertEquals("hello", d.text)
        assertEquals("com.whatsapp", d.pkg)
    }

    @Test
    fun failedPrivateCommandStaysLocal() {
        val r = router.route("send something somewhere somehow", emptyList())
        assertTrue(r is CommandRouter.Result.Reply)
        assertTrue((r as CommandRouter.Result.Reply).text.contains("couldn't tell"))
    }

    @Test
    fun openEndedTextIsEmptyReply() {
        // The chat layer turns this into a local note + explicit Ask AI action.
        val r = router.route("what's a good birthday gift for my dad", emptyList())
        assertTrue(r is CommandRouter.Result.Reply)
        assertTrue((r as CommandRouter.Result.Reply).text.isEmpty())
    }

    @Test
    fun appLaunchStillWorks() {
        val chrome = AppEntry("Chrome", "com.android.chrome", Intent())
        val r = router.route("chrome", listOf(chrome))
        assertTrue(r is CommandRouter.Result.Launch)
    }

    @Test
    fun focusOnOffRoundTrip() {
        val on = router.route("focus work", emptyList()) as CommandRouter.Result.Reply
        assertTrue(on.text.startsWith("Profile 'Work'"))
        assertEquals("Work", router.activeProfile)
        router.route("focus off", emptyList())
        assertNull(router.activeProfile)
    }
}
