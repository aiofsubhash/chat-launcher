package com.subhash.chatlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.ComponentActivity

/**
 * Turns a chat line into an action. Everything that leaves the app is a DRAFT
 * the user confirms in the target app - this launcher never sends messages
 * on the user's behalf by itself.
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

    fun profileNames(): Set<String> = prefs.getStringSet("profile_names", emptySet()) ?: emptySet()

    fun profileHidden(name: String): Set<String> =
        prefs.getStringSet("profile_$name", emptySet()) ?: emptySet()

    fun saveProfile(name: String, hidden: Set<String>) {
        prefs.edit()
            .putStringSet("profile_names", profileNames() + name)
            .putStringSet("profile_$name", hidden)
            .apply()
    }

    fun deleteProfile(name: String) {
        if (activeProfile == name) activeProfile = null
        prefs.edit()
            .putStringSet("profile_names", profileNames() - name)
            .remove("profile_$name")
            .apply()
    }

    /** Packages hidden by the currently active focus profile (empty when off). */
    fun hiddenNow(): Set<String> = activeProfile?.let { profileHidden(it) } ?: emptySet()

    companion object {
        /** Work mode (a focus profile named "Work") sorts work-profile app entries first. */
        fun prioritizeWork(apps: List<AppEntry>, workMode: Boolean): List<AppEntry> =
            if (workMode) apps.sortedByDescending { it.isWork } else apps

        /** 30-minute idle reset: fresh chat only when nothing unsent and no question is pending. */
        fun shouldResetChat(idleMillis: Long, hasMessages: Boolean, hasDraft: Boolean, hasPendingQuestion: Boolean): Boolean =
            idleMillis > 30L * 60 * 1000 && hasMessages && !hasDraft && !hasPendingQuestion

        /** Installed-only share targets: drop this launcher and focus-hidden entries, sort by label. */
        fun shareTargetsOf(resolved: List<Pair<String, String>>, selfPackage: String, hidden: Set<String>): List<Pair<String, String>> =
            resolved.filter { it.second != selfPackage && it.second !in hidden }.sortedBy { it.first }

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
        data class DraftMessage(val to: String, val text: String, val pkg: String? = null) : Result
        data class UberRide(val dropoff: String) : Result
        /** Duplicate personal/work matches: never silently pick - the user chooses. */
        data class ChooseApp(val apps: List<AppEntry>, val label: String) : Result
    }

    fun route(raw: String, suggestions: List<AppEntry>): Result {
        val input = raw.trim()
        if (input.isEmpty()) return Result.Reply("Say something - an app name, 'focus', or a message to send.")

        // Focus profiles
        if (input.equals("focus", true)) {
            val names = profileNames()
            val current = activeProfile
            return Result.Reply(
                if (names.isEmpty()) "No focus profiles yet - create one in Settings."
                else "Profiles: ${names.joinToString(", ")}. Active: ${current ?: "none"}. Say 'focus <name>' or 'focus off'."
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
                val hidden = profileHidden(match)
                Result.Reply("Focus profile '$match' on. Hidden: ${hidden.joinToString(", ").ifBlank { "nothing" }}")
            } else {
                Result.Reply("No profile called '$name'. Profiles: ${profileNames().joinToString(", ").ifBlank { "none yet - create one in Settings" }}")
            }
        }

        // "send this message to X: text" / "send X text via whatsapp"
        val toMatch = Regex("""(?i)^send\s+(?:this message to\s+)?([A-Za-z][\w .]*?)(?::|\s+via\s+(\w+))\s+(.+)$""").find(input)
        if (toMatch != null) {
            val to = toMatch.groupValues[1].trim()
            val via = toMatch.groupValues[2]
            val text = toMatch.groupValues[3].trim().trim('"')
            val pkg = if (via.contains("whats", true)) "com.whatsapp" else null
            return Result.DraftMessage(to, text, pkg)
        }

        // "uber to X" / "ride to X" - opens Uber with the dropoff filled in; the user confirms there.
        val rideMatch = Regex("""(?i)^(?:uber|ride|cab)\s+(?:to\s+)?(.+)$""").find(input)
        if (rideMatch != null) {
            return Result.UberRide(rideMatch.groupValues[1].trim().trim('"'))
        }

        // App launch: a single exact match wins; duplicate personal/work entries always ask.
        val exactMatches = suggestions.filter { it.label.equals(input, true) || it.packageName.equals(input, true) }
        if (exactMatches.size > 1) return Result.ChooseApp(exactMatches, input)
        if (exactMatches.size == 1) return Result.Launch(exactMatches.first())
        if (suggestions.size == 1) return Result.Launch(suggestions.first())

        return Result.Reply("")
    }

    fun execute(result: Result): String = when (result) {
        is Result.ChooseApp -> "" // resolved inline by the conversation UI
        is Result.Launch -> try {
            val handle = result.app.userHandle
            if (result.app.isWork && handle != null) {
                // Work-profile entries must start through LauncherApps; a raw startActivity fails.
                activity.getSystemService(android.content.pm.LauncherApps::class.java)
                    .startMainActivity(result.app.launch.component, handle, null, null)
            } else {
                activity.startActivity(result.app.launch)
            }
            "Opening ${result.app.displayLabel}"
        } catch (e: Exception) {
            launchFailureMessage(result.app)
        }
        is Result.DraftMessage -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, result.text)
                if (result.pkg != null) setPackage(result.pkg)
            }
            try {
                activity.startActivity(intent)
                "Draft ready in ${if (result.pkg != null) "WhatsApp" else "share"} for ${result.to} - pick the chat and tap send."
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
