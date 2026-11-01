package com.subhash.chatlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal chat client with two providers. Anthropic keys talk to the Messages API;
 * OpenRouter keys (sk-or-...) talk to OpenRouter's OpenAI-compatible endpoint, where
 * the default model is a free one. The API key comes from the app's Account settings
 * and lives in SharedPreferences - it is never committed.
 */
object LlmClient {

    enum class Provider { ANTHROPIC, OPENROUTER }

    /** OpenRouter keys carry the sk-or- prefix; anything else is treated as Anthropic. */
    fun providerForKey(apiKey: String): Provider =
        if (apiKey.startsWith("sk-or-")) Provider.OPENROUTER else Provider.ANTHROPIC

    fun defaultModel(provider: Provider): String = when (provider) {
        Provider.ANTHROPIC -> "claude-sonnet-4-5-20250929"
        // Free OpenRouter model by default - no paid fallback. Editable in Account settings.
        Provider.OPENROUTER -> "nvidia/nemotron-3-ultra-550b-a55b:free"
    }

    /** Zero-priced ids from OpenRouter's public catalog payload (GET /api/v1/models). */
    fun freeOpenRouterModels(catalogJson: String): Set<String> {
        val data = JSONObject(catalogJson).getJSONArray("data")
        val out = mutableSetOf<String>()
        for (i in 0 until data.length()) {
            val m = data.getJSONObject(i)
            val pricing = m.optJSONObject("pricing") ?: continue
            if (pricing.optString("prompt") == "0" && pricing.optString("completion") == "0") {
                out.add(m.getString("id"))
            }
        }
        return out
    }

    suspend fun fetchOpenRouterCatalog(): String = withContext(Dispatchers.IO) {
        val conn = (URL("https://openrouter.ai/api/v1/models").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        conn.inputStream.bufferedReader().readText()
    }

    /**
     * Free-only gate, fail-closed. Anthropic keys use the account's own models. An OpenRouter
     * id is allowed only when the live catalog (or a set cached from a successful fetch)
     * verifies it as zero-priced; if pricing can't be verified the id is rejected, and a
     * rejected saved model is never silently swapped for another.
     */
    fun isAllowedModel(provider: Provider, model: String, freeIds: Set<String>?): Boolean =
        when (provider) {
            Provider.ANTHROPIC -> true
            Provider.OPENROUTER -> freeIds?.let { model in it } ?: false
        }

    /**
     * Model to send, or null when a saved OpenRouter choice fails the free-only gate and the
     * caller must surface that instead of falling back silently. Blank saved = the provider
     * default, which is always a verified :free id for OpenRouter.
     */
    fun gatedModel(provider: Provider, saved: String, freeIds: Set<String>?): String? =
        when {
            saved.isBlank() -> defaultModel(provider)
            isAllowedModel(provider, saved, freeIds) -> saved
            else -> null
        }

    private const val SYSTEM_PROMPT = "You are the voice of a chat-first Android launcher. Be brief and plain. You cannot perform device actions yourself; the launcher app handles app launches and message drafts. When visual formatting genuinely helps, you may answer in simple HTML (no scripts)."

    suspend fun chat(apiKey: String, model: String, history: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            when (providerForKey(apiKey)) {
                Provider.OPENROUTER -> chatOpenRouter(apiKey, model, history)
                Provider.ANTHROPIC -> chatAnthropic(apiKey, model, history)
            }
        }

    /** Request body for OpenRouter. max_price 0/0 makes OpenRouter itself refuse non-free routing - provider-side fail-closed, independent of the local gate. */
    internal fun openRouterBody(model: String, history: List<Pair<String, String>>): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        for ((role, text) in history.takeLast(10)) {
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        return JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("messages", messages)
            .put("provider", JSONObject().put("max_price", JSONObject().put("prompt", "0").put("completion", "0")))
            .toString()
    }

    private fun chatOpenRouter(apiKey: String, model: String, history: List<Pair<String, String>>): String {
        val body = openRouterBody(model, history)
        val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if (code !in 200..299) return "API error $code: ${text.take(200)}"
        return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun chatAnthropic(apiKey: String, model: String, history: List<Pair<String, String>>): String {
        val messages = JSONArray()
        for ((role, text) in history.takeLast(10)) {
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("system", SYSTEM_PROMPT)
            .put("messages", messages)
            .toString()
        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
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
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if (code !in 200..299) return "API error $code: ${text.take(200)}"
        val content = JSONObject(text).getJSONArray("content")
        return (0 until content.length())
            .map { content.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("\n") { it.getString("text") }
    }
}
