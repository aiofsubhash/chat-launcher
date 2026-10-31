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
    var focusMode: Boolean
        get() = prefs.getBoolean("focus_mode", false)
        set(v) = prefs.edit().putBoolean("focus_mode", v).apply()

    var focusHidden: Set<String>
        get() = prefs.getStringSet("focus_hidden", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("focus_hidden", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    sealed class Result {
        data class Reply(val text: String) : Result()
        /** Launch an app directly. */
        data class Launch(val app: AppEntry) : Result()
        /** Open a share sheet carrying a draft message; user picks the chat. */
        data class DraftMessage(val to: String, val text: String) : Result()
    }

    fun route(raw: String, suggestions: List<AppEntry>): Result {
        val input = raw.trim()
        if (input.isEmpty()) return Result.Reply("Say something - an app name, 'focus', or a message to send.")

        // "focus" / "focus off" toggles focus mode
        if (input.equals("focus", true) || input.equals("focus on", true)) {
            focusMode = true
            return Result.Reply("Focus mode on. Hidden apps: ${focusHidden.joinToString(", ").ifBlank { "none set - long-press a suggestion to hide it" }}")
        }
        if (input.equals("focus off", true)) {
            focusMode = false
            return Result.Reply("Focus mode off.")
        }

        // "send this message to X: text" / "send text to X" / "send X text via whatsapp"
        val toMatch = Regex("""(?i)^send\s+(?:this message to\s+)?([A-Za-z][\w .]*?)(?::|\s+via\s+\w+)\s+(.+)$""").find(input)
        if (toMatch != null) {
            val to = toMatch.groupValues[1].trim()
            val text = toMatch.groupValues[2].trim().trim('"')
            return Result.DraftMessage(to, text)
        }

        // App launch: exact-ish match wins, otherwise ask via suggestions
        val exact = suggestions.firstOrNull { it.label.equals(input, true) || it.packageName.equals(input, true) }
        if (exact != null) return Result.Launch(exact)
        if (suggestions.size == 1) return Result.Launch(suggestions.first())

        // Default: plain chat (LLM if a key is configured)
        return Result.Reply("")
    }

    fun execute(result: Result): String = when (result) {
        is Result.Launch -> {
            try {
                activity.startActivity(result.app.launch)
                "Opening ${result.app.label}"
            } catch (e: ActivityNotFoundException) {
                "Couldn't open ${result.app.label}"
            }
        }
        is Result.DraftMessage -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, result.text)
            }
            try {
                activity.startActivity(Intent.createChooser(intent, "Send to ${result.to}"))
                "Draft ready for ${result.to} - pick the chat to drop it in."
            } catch (e: ActivityNotFoundException) {
                "No app can take that draft."
            }
        }
        is Result.Reply -> result.text
    }
}
