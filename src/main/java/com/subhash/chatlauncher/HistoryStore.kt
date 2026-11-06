package com.subhash.chatlauncher

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val ts: Long,
    /** "user" or "assistant". */
    val role: String,
    /** "local" or "cloud" - where the reply came from. */
    val source: String,
    val text: String,
)

/**
 * On-device chat history. Every exchange lands here: user lines, local replies, handoff
 * notes, errors and cloud replies (tagged). Never leaves the phone. Capped; oldest drop off.
 */
class HistoryStore(
    private val prefs: SharedPreferences,
    private val key: String = "chat_history",
    private val cap: Int = 300,
) {
    @Synchronized
    fun append(role: String, source: String, text: String, now: Long = System.currentTimeMillis()) {
        if (text.isBlank()) return
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        arr.put(JSONObject().put("ts", now).put("role", role).put("source", source).put("text", text))
        while (arr.length() > cap) arr.remove(0)
        prefs.edit().putString(key, arr.toString()).apply()
    }

    /** Newest first. */
    fun all(): List<HistoryEntry> {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        val out = ArrayList<HistoryEntry>(arr.length())
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            out.add(HistoryEntry(o.getLong("ts"), o.getString("role"), o.optString("source", "local"), o.getString("text")))
        }
        return out
    }

    fun clear() = prefs.edit().remove(key).apply()
}


/** One collapsed row in History: the user's query plus the replies it produced. */
data class HistoryTurn(
    /** Timestamp of the turn's first entry - stable id for expand/collapse state. */
    val id: Long,
    val query: HistoryEntry?,
    val responses: List<HistoryEntry>,
)

/**
 * Groups a newest-first entry list into newest-first turns. A user line starts a turn;
 * following assistant lines are its responses. Assistant-only runs (launch notes, status)
 * become query-less turns so nothing disappears from History.
 */
fun groupHistoryTurns(entriesNewestFirst: List<HistoryEntry>): List<HistoryTurn> {
    val turns = ArrayList<HistoryTurn>()
    var query: HistoryEntry? = null
    var responses = ArrayList<HistoryEntry>()
    var turnStart = 0L
    fun flush() {
        if (query != null || responses.isNotEmpty()) turns.add(HistoryTurn(turnStart, query, responses.toList()))
        query = null
        responses = ArrayList()
        turnStart = 0L
    }
    for (e in entriesNewestFirst.asReversed()) {
        if (e.role == "user") {
            flush()
            query = e
            turnStart = e.ts
        } else {
            if (query == null && responses.isEmpty()) turnStart = e.ts
            responses.add(e)
        }
    }
    flush()
    return turns.asReversed()
}
