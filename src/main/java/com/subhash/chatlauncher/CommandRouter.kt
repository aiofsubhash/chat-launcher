package com.subhash.chatlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.provider.AlarmClock
import androidx.activity.ComponentActivity

/**
 * Turns a chat line into an action. Everything that leaves the app is a DRAFT
 * the user confirms in the target app - this launcher never sends messages
 * on the user's behalf by itself. Profiles are ALLOW-lists: while a profile is
 * on, only its apps (plus the Focus timer / Work's managed apps) can be opened.
 */
class CommandRouter(
    private val activity: ComponentActivity,
    private val prefs: SharedPreferences,
) {
    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    /** Explicit model override; blank = provider default (see LlmClient.defaultModel). */
    var model: String
        get() = prefs.getString("llm_model", "") ?: ""
        set(v) = prefs.edit().putString("llm_model", v).apply()

    /** OpenRouter ids verified zero-priced against the live catalog; empty until the first successful check. */
    var freeModelIds: Set<String>
        get() = (prefs.getString("llm_free_ids", "") ?: "").split(',').filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString("llm_free_ids", v.joinToString(",")).apply()

    /**
     * Optional chat endpoint (full chat-completions URL, e.g. a Cloudflare Worker) for the
     * Connect AI path: the server holds the upstream key; apiKey then carries the pairing
     * credential. Blank = talk to the key's own provider directly.
     */
    var chatEndpoint: String
        get() = prefs.getString("chat_endpoint", "") ?: ""
        set(v) = prefs.edit().putString("chat_endpoint", v).apply()

    var activeProfile: String?
        get() = prefs.getString("active_profile", null)?.takeIf { it.isNotBlank() }
        set(v) = prefs.edit().putString("active_profile", v ?: "").apply()

    var gridWobbly: Boolean
        get() = prefs.getBoolean("grid_wobbly", true)
        set(v) = prefs.edit().putBoolean("grid_wobbly", v).apply()

    // Home dock apps (up to 4), in display order. Defaults per owner request.
    var dockApps: List<String>
        get() = prefs.getString("dock_apps", null)
            ?.split(",")?.filter { it.isNotBlank() }?.take(4)
            ?: DEFAULT_DOCK_APPS
        set(v) = prefs.edit().putString("dock_apps", v.take(4).joinToString(",")).apply()

    /** Normal-mode dock keys: stored choices, with stale stored keys falling back to defaults so
     *  the dock is never blank by accident (device bug 22:53); an intentionally emptied dock stays empty. */
    fun normalDockKeys(installedKeys: Set<String>): List<String> {
        val stored = prefs.getString("dock_apps", null)
        if (stored == null) return DEFAULT_DOCK_APPS.filter { it in installedKeys }
        val keys = stored.split(",").filter { it.isNotBlank() }
        if (keys.isEmpty()) return emptyList()
        val resolved = keys.filter { it == TIMER_KEY || it in installedKeys }
        return resolved.ifEmpty { DEFAULT_DOCK_APPS.filter { it in installedKeys } }
    }

    // ---- Profiles: allow-lists. Built-ins: Focus (Timer only by default), Work (managed
    // work apps + additions). Built-ins always exist and cannot be deleted.

    fun customProfileNames(): Set<String> = prefs.getStringSet("profile_names", emptySet()) ?: emptySet()

    fun profileNames(): Set<String> = BUILTIN_PROFILES + customProfileNames()

    fun isBuiltin(name: String): Boolean = name in BUILTIN_PROFILES

    /** Stored allow-list; an untouched Focus profile allows only the Timer note. */
    fun profileAllowed(name: String): Set<String> {
        val stored = prefs.getStringSet("profile_allow_$name", null)
        if (stored != null) return stored
        return if (name == FOCUS) setOf(TIMER_KEY) else emptySet()
    }

    fun saveProfile(name: String, allowed: Set<String>) {
        val editor = prefs.edit().putStringSet("profile_allow_$name", allowed)
        if (!isBuiltin(name)) editor.putStringSet("profile_names", customProfileNames() + name)
        editor.apply()
    }

    fun deleteProfile(name: String) {
        if (isBuiltin(name)) return // built-ins stay
        if (activeProfile == name) activeProfile = null
        prefs.edit()
            .putStringSet("profile_names", customProfileNames() - name)
            .remove("profile_allow_$name")
            .apply()
    }

    /** Work's effective list is its additions plus whatever the managed profile currently has. */
    fun effectiveAllowed(name: String, workKeys: Set<String>): Set<String> =
        if (name == WORK) profileAllowed(name) + workKeys else profileAllowed(name)

    /** Keys openable right now; null = no profile active (everything openable). */
    fun allowedNow(workKeys: Set<String>): Set<String>? =
        activeProfile?.let { effectiveAllowed(it, workKeys) }

    /**
     * One-time migration from the old hidden-list format ("profile_<name>").
     * Meaning-preserving: allowed = currently installed minus previously hidden. Deferred
     * (nothing written) when the installed list is empty, so a profile never silently turns
     * permissive or empty. Old sets are kept under profile_hidden_backup_<name>. A custom
     * profile colliding with a new built-in migrates as "<name> (migrated)".
     */
    fun migrateHiddenProfiles(installedKeys: Set<String>): Boolean {
        if (prefs.getBoolean("profiles_allow_migrated", false)) return true
        if (installedKeys.isEmpty()) return false // defer, keep old data untouched
        val oldKeys = prefs.all.keys.filter { it.startsWith("profile_") && !it.startsWith("profile_allow_") && !it.startsWith("profile_hidden_backup_") && it != "profile_names" }
        val editor = prefs.edit()
        var active = activeProfile
        for (k in oldKeys) {
            val oldName = k.removePrefix("profile_")
            val hidden = prefs.getStringSet(k, emptySet()) ?: emptySet()
            val allowed = installedKeys - hidden
            val newName = if (isBuiltin(oldName)) "$oldName (migrated)" else oldName
            editor.putStringSet("profile_allow_$newName", allowed)
            editor.putStringSet("profile_hidden_backup_$oldName", hidden)
            editor.remove(k)
            if (newName != oldName) {
                editor.putStringSet("profile_names", customProfileNames() + newName)
                if (active == oldName) active = newName
            }
        }
        if (active != activeProfile) editor.putString("active_profile", active ?: "")
        editor.putBoolean("profiles_allow_migrated", true)
        editor.apply()
        return true
    }

    /** Theme preference: "system", "light" or "dark". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(v) = prefs.edit().putString("theme_mode", v).apply()

    // ---- Focus profile's Timer note: opens the phone's own clock/alarm app.

    /** Best clock-app intent available on this device, or null when no clock app resolves. */
    fun timerIntent(): Intent? {
        val pm = activity.packageManager
        val candidates = listOf(
            Intent(AlarmClock.ACTION_SHOW_TIMERS),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, 600),
        )
        return candidates.firstOrNull { it.resolveActivity(pm) != null }
    }

    /**
     * Launch through LauncherApps with the entry's own UserHandle - the sanctioned API for
     * launcher apps, reliable across personal and work profiles. Raw startActivity only when
     * the entry carries no handle (PackageManager fallback path).
     */
    fun launchAppEntry(app: AppEntry): Boolean = try {
        val handle = app.userHandle
        val la = activity.getSystemService(android.content.pm.LauncherApps::class.java)
        if (handle != null && app.launch.component != null) {
            la.startMainActivity(app.launch.component, handle, null, null)
        } else {
            // From a singleTask HOME activity a flag-less intent can stack the target inside
            // the launcher's own task (suspected in the old build's "Opening" but never
            // foreground report). NEW_TASK keeps the fallback on launcher semantics.
            activity.startActivity(Intent(app.launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (e: Exception) {
        false
    }

    fun launchTimer(): Boolean {
        val intent = timerIntent() ?: return false
        return try {
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val FOCUS = "Focus"
        const val WORK = "Work"
        const val REASONING = "Reasoning"
        val BUILTIN_PROFILES = linkedSetOf(FOCUS, WORK, REASONING)

        /** Synthetic dock identity for the Focus profile's clock-app shortcut. */
        const val TIMER_KEY = "special:timer"

        /** Work mode (a focus profile named "Work") sorts work-profile app entries first. */
        fun prioritizeWork(apps: List<AppEntry>, workMode: Boolean): List<AppEntry> =
            if (workMode) apps.sortedByDescending { it.isWork } else apps

        /**
         * Owner 12:44: the Work-mode sticky dock is exactly Slack, Claude, Gmail, in the
         * owner's stated order. The work-profile instance wins when both profiles carry the
         * app (work context grounds the pick; never an arbitrary package guess); a
         * personal-only install still shows so the named note exists. Dock display only:
         * the Work access policy (openable apps, suggestions, share targets) is unchanged.
         */
        val WORK_DOCK_PACKAGES: List<String> =
            listOf("com.Slack", "com.anthropic.claude", "com.google.android.gm")

        /** Resolve the Work dock stickies from installed apps; missing apps drop out. */
        fun workDockApps(apps: List<AppEntry>): List<AppEntry> =
            WORK_DOCK_PACKAGES.mapNotNull { pkg ->
                apps.firstOrNull { it.packageName == pkg && it.isWork }
                    ?: apps.firstOrNull { it.packageName == pkg }
            }

        /** 30-minute idle reset: fresh chat only when nothing unsent and no question is pending. */
        fun shouldResetChat(idleMillis: Long, hasMessages: Boolean, hasDraft: Boolean, hasPendingQuestion: Boolean): Boolean =
            idleMillis > 30L * 60 * 1000 && hasMessages && !hasDraft && !hasPendingQuestion

        /** Installed-only share targets: drop this launcher; a profile trims to its allow-list. */
        fun shareTargetsOf(resolved: List<Pair<String, String>>, selfPackage: String, allowed: Set<String>?): List<Pair<String, String>> =
            resolved.filter { it.second != selfPackage && (allowed == null || it.second in allowed) }.sortedBy { it.first }

        /** Truthful launch failure; a failed work-profile launch never falls back to the personal app. */
        fun launchFailureMessage(app: AppEntry): String =
            if (app.isWork) "Couldn't open ${app.displayLabel} - the work profile may be paused or locked. Unpause it in system settings and try again; I won't open the personal app instead."
            else "Couldn't open ${app.label}"

        val DEFAULT_DOCK_APPS = listOf(
            "com.google.android.apps.maps",
            "com.android.chrome",
            "com.spotify.music",
            "com.phonepe.app",
        )
    }

    sealed interface Result {
        data class Reply(val text: String) : Result
        data class Launch(val app: AppEntry) : Result
        /** text null = ask what to say first; pkg null = ask which app first. Always a draft. */
        data class DraftMessage(val to: String, val text: String?, val pkg: String? = null) : Result
        data class UberRide(val dropoff: String) : Result
        /** Duplicate personal/work matches: never silently pick - the user chooses. */
        data class ChooseApp(val apps: List<AppEntry>, val label: String) : Result
    }

    fun route(raw: String, suggestions: List<AppEntry>): Result {
        val input = raw.trim()
        if (input.isEmpty()) return Result.Reply("Say something - an app name, 'focus', or a message to send.")

        // Profiles
        if (input.equals("focus", true)) {
            val names = profileNames()
            val current = activeProfile
            return Result.Reply(
                "Profiles: ${names.joinToString(", ")}. Active: ${current ?: "Normal"}. Say 'focus <name>' or 'focus off'."
            )
        }
        if (input.equals("focus off", true)) {
            activeProfile = null
            return Result.Reply("Focus mode off.")
        }
        if (input.startsWith("focus ", true)) {
            val name = input.removePrefix("focus ").trim()
            val match = profileNames().firstOrNull { it.equals(name, true) }
            return if (match != null) {
                activeProfile = match
                Result.Reply("Profile '$match' on - only its allowed apps can be opened.")
            } else {
                Result.Reply("No profile called '$name'. Profiles: ${profileNames().joinToString(", ")}")
            }
        }

        // Exact app-name match wins before message parsing (device bug 22:48): typing exactly
        // an installed app's name opens it - 'WhatsApp' alone is never a message attempt.
        // Explicit 'send a message...' commands still parse below (they are not exact app names).
        run {
            val distinct = suggestions.distinctBy { it.identityKey }
            val exact = distinct.filter { it.label.equals(input, true) || it.packageName.equals(input, true) }
            if (exact.size > 1) return Result.ChooseApp(exact, input)
            if (exact.size == 1) return Result.Launch(exact.first())
        }

        // Explicit note intent stays local (owner 22:52); other open text becomes conversation.
        MessageIntents.parseNote(input)?.let { note ->
            return Result.Reply("Noted - saved on this phone: $note")
        }

        // Message drafts: deterministic, no AI. Missing text/channel is asked inline.
        MessageIntents.parse(input)?.let { parsed ->
            return Result.DraftMessage(parsed.to, parsed.text, parsed.pkg)
        }
        // A command-shaped text that didn't parse stays local - never a silent cloud call.
        if (MessageIntents.looksPrivateAction(input)) {
            return Result.Reply("I couldn't tell who that's for or what app to use. Try 'send a message to <name> on <app>'.")
        }

        // "uber to X" / "ride to X" - opens Uber with the dropoff filled in; the user confirms there.
        val rideMatch = Regex("""(?i)^(?:uber|ride|cab)\s+(?:to\s+)?(.+)$""").find(input)
        if (rideMatch != null) {
            return Result.UberRide(rideMatch.groupValues[1].trim().trim('"'))
        }

        // App launch: a single exact match wins; duplicate personal/work entries always ask.
        // Dedupe by identity first - the same package registered twice is one app, not a choice.
        val distinct = suggestions.distinctBy { it.identityKey }
        val exactMatches = distinct.filter { it.label.equals(input, true) || it.packageName.equals(input, true) }
        if (exactMatches.size > 1) return Result.ChooseApp(exactMatches, input)
        if (exactMatches.size == 1) return Result.Launch(exactMatches.first())
        if (distinct.size == 1) return Result.Launch(distinct.first())
        // Multiple matches for a partial name (e.g. personal + work Instagram): never a
        // silent pick, never a note - an explicit local chooser.
        if (distinct.size > 1) return Result.ChooseApp(distinct, input)

        return Result.Reply("")
    }

    fun execute(result: Result): String = when (result) {
        is Result.ChooseApp -> "" // resolved inline by the conversation UI
        is Result.Launch -> {
            if (result.app.packageName == TIMER_KEY) {
                if (launchTimer()) "Opening the clock app" else "No clock app on this phone handles timers."
            } else {
                // Dispatch truth, stated as dispatch: the OS accepted the open request, but
                // whether the target actually foregrounds is not observable from here (the
                // old build's stuck "Opening Instagram" report). Terminal + offers retry.
                if (launchAppEntry(result.app))
                    "Asked Android to open ${result.app.displayLabel} - if it doesn't appear, tell me and I'll try again."
                else launchFailureMessage(result.app)
            }
        }
        is Result.DraftMessage -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, result.text ?: "")
                if (result.pkg != null) setPackage(result.pkg)
            }
            val where = when (result.pkg) {
                "com.whatsapp" -> "WhatsApp"
                "org.telegram.messenger" -> "Telegram"
                "org.thoughtcrime.securesms" -> "Signal"
                "com.google.android.gm" -> "Gmail"
                "com.instagram.android" -> "Instagram"
                null -> "share"
                else -> "the app"
            }
            try {
                activity.startActivity(intent)
                "Draft ready in $where for ${result.to} - pick the chat and tap send."
            } catch (e: ActivityNotFoundException) {
                try {
                    activity.startActivity(Intent.createChooser(Intent(intent).setPackage(null), "Send to ${result.to}"))
                    "Draft ready for ${result.to} - pick the chat to drop it in."
                } catch (e2: ActivityNotFoundException) {
                    "No app can take that draft."
                }
            }
        }
        is Result.UberRide -> {
            val q = android.net.Uri.encode(result.dropoff)
            val appUri = android.net.Uri.parse("uber://?action=setPickup&pickup=my_location&dropoff[formatted_address]=$q")
            val webUri = android.net.Uri.parse("https://m.uber.com/")
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, appUri))
                "Opening Uber with '${result.dropoff}' as destination text - check it there and confirm the ride yourself."
            } catch (e: ActivityNotFoundException) {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                    "Uber app not found - opened Uber on the web. Enter '${result.dropoff}' as the destination there."
                } catch (e2: ActivityNotFoundException) {
                    "Uber isn't available - no ride opened."
                }
            }
        }
        is Result.Reply -> result.text
    }
}
