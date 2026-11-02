package com.subhash.chatlauncher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Reasoning profile's local user context: the short onboarding conversation's
 * questions and answers. Stored only on this phone (SharedPreferences); never sent
 * anywhere - the cloud path never sees these. Read-only in the UI for now.
 */
class UserContextStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_context_prefs", Context.MODE_PRIVATE)

    data class Entry(val question: String, val answer: String, val ts: Long)

    fun entries(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("q"), o.getString("a"), o.getLong("ts"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isEmpty(): Boolean = entries().isEmpty()

    fun add(question: String, answer: String, ts: Long = System.currentTimeMillis()) {
        val arr = JSONArray()
        entries().takeLast(MAX - 1).forEach {
            arr.put(JSONObject().put("q", it.question).put("a", it.answer).put("ts", it.ts))
        }
        arr.put(JSONObject().put("q", question).put("a", answer).put("ts", ts))
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** The user's own words about what they want more time for, if they answered - used
     *  to keep the Reasoning nudge personal without anything leaving the phone. */
    fun goal(): String? = entries().firstOrNull { it.question == QUESTIONS.first() }
        ?.answer?.takeIf { it.isNotBlank() }

    companion object {
        private const val KEY = "entries"
        private const val MAX = 24
        val QUESTIONS = listOf(
            "What do you want more time for?",
            "What does your phone get in the way of?",
            "When you reach for an entertainment app, what are you usually looking for?",
            "What would you rather be doing instead?",
        )
    }
}
