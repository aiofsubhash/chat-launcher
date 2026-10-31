package com.subhash.chatlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Anthropic Messages API client. The API key comes from the in-app
 * settings dialog and lives in SharedPreferences - it is never committed.
 */
object LlmClient {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-sonnet-4-5-20250929"

    suspend fun chat(apiKey: String, history: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val messages = JSONArray()
            for ((role, text) in history.takeLast(10)) {
                messages.put(JSONObject().put("role", role).put("content", text))
            }
            val body = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 1024)
                .put("system", "You are the voice of a chat-first Android launcher. Be brief and plain. You cannot perform device actions yourself; the launcher app handles app launches and message drafts.")
                .put("messages", messages)
                .toString()

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().readText()
            if (code !in 200..299) return@withContext "API error $code: ${text.take(200)}"
            val content = JSONObject(text).getJSONArray("content")
            (0 until content.length())
                .map { content.getJSONObject(it) }
                .filter { it.optString("type") == "text" }
                .joinToString("\n") { it.getString("text") }
        }
}
