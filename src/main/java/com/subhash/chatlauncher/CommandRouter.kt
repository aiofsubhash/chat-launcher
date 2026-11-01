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

        // App launch: exact match wins, single suggestion launches, otherwise chat
        val exact = suggestions.firstOrNull { it.label.equals(input, true) || it.packageName.equals(input, true) }
        if (exact != null) return Result.Launch(exact)
        if (suggestions.size == 1) return Result.Launch(suggestions.first())

        return Result.Reply("")
    }

    fun execute(result: Result): String = when (result) {
        is Result.Launch -> try {
            activity.startActivity(result.app.launch)
            "Opening ${result.app.label}"
        } catch (e: ActivityNotFoundException) {
            "Couldn't open ${result.app.label}"
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
            val webUri = android.net.Uri.parse("https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[formatted_address]=$q")
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, appUri))
                "Opening Uber with dropoff '${result.dropoff}' - confirm the ride there."
            } catch (e: ActivityNotFoundException) {
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                    "Opening Uber with dropoff '${result.dropoff}' - confirm the ride there."
                } catch (e2: ActivityNotFoundException) {
                    "Uber isn't installed - no ride opened."
                }
            }
        }
        is Result.Reply -> result.text
    }
}
