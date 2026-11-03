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

    /**
     * Chat with progressive delivery: onDelta receives each text chunk as it arrives (OpenRouter
     * SSE). Anthropic and any non-streaming path deliver the full reply once via onDelta.
     */
    /**
     * Chat with progressive delivery. endpointUrl (Connect AI): POST the OpenAI-compatible
     * body to that full URL with the credential as bearer - the server holds the upstream
     * key and fixes the model. Blank endpoint = the key's own provider, as before.
     */
    suspend fun chatStream(apiKey: String, model: String, history: List<Pair<String, String>>, endpointUrl: String = "", onDelta: suspend (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            if (endpointUrl.isNotBlank()) {
                return@withContext chatProxyStream(endpointUrl.trim(), apiKey, model, history, onDelta)
            }
            when (providerForKey(apiKey)) {
                Provider.OPENROUTER -> chatOpenRouterStream(apiKey, model, history, onDelta)
                Provider.ANTHROPIC -> chatAnthropic(apiKey, model, history).also { onDelta(it) }
            }
        }

    /** Connect AI proxy: same OpenAI-compatible shape, server-fixed free model upstream. */
    private suspend fun chatProxyStream(endpointUrl: String, credential: String, model: String, history: List<Pair<String, String>>, onDelta: suspend (String) -> Unit): String {
        // Proxy contract: exactly ONE current user message - no system scaffold, no history
        // (device 400 'expected exactly one message' 22:51). The server fixes the model upstream.
        val userText = history.lastOrNull { it.first == "user" }?.second.orEmpty()
        val body = JSONObject(proxyBody(model, userText)).put("stream", true).toString()
        val conn = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("Authorization", "Bearer $credential")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            return proxyErrorMessage(code, err)
        }
        val sb = StringBuilder()
        val reader = conn.inputStream.bufferedReader()
        var line = reader.readLine()
        while (line != null) {
            val delta = parseSseDelta(line)
            if (delta != null) {
                sb.append(delta)
                onDelta(delta)
            }
            line = reader.readLine()
        }
        return sb.toString()
    }

    /** Parse one SSE line into a content delta, or null for keep-alives/[DONE]. */
    fun parseSseDelta(line: String): String? {
        if (!line.startsWith("data: ")) return null
        val payload = line.removePrefix("data: ").trim()
        if (payload == "[DONE]") return null
        return try {
            JSONObject(payload).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("delta").optString("content").takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
    }

    suspend fun chat(apiKey: String, model: String, history: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            when (providerForKey(apiKey)) {
                Provider.OPENROUTER -> chatOpenRouter(apiKey, model, history)
                Provider.ANTHROPIC -> chatAnthropic(apiKey, model, history)
            }
        }

    /** Connect AI proxy body: the current user message only - never history, never a system prompt. */
    internal fun proxyBody(model: String, userText: String): String =
        JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", userText)))
            .toString()

    /** Clean error line for the UI - never a raw server JSON dump. */
    internal fun proxyErrorMessage(code: Int, errBody: String): String {
        val detail = try { JSONObject(errBody).getJSONObject("error").optString("message") } catch (e: Exception) { "" }
        if (code == 429 || detail.contains("rate_limit", true)) {
            return "The free AI is busy right now. Nothing is lost - try again in a bit."
        }
        return if (detail.isNotBlank()) "The AI couldn't answer (error $code) - $detail" else "The AI couldn't answer (error $code)."
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

    private suspend fun chatOpenRouterStream(apiKey: String, model: String, history: List<Pair<String, String>>, onDelta: suspend (String) -> Unit): String {
        val body = JSONObject(openRouterBody(model, history)).put("stream", true).toString()
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
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            return "API error $code: ${err.take(200)}"
        }
        val sb = StringBuilder()
        val reader = conn.inputStream.bufferedReader()
        var line = reader.readLine()
        while (line != null) {
            val delta = parseSseDelta(line)
            if (delta != null) {
                sb.append(delta)
                onDelta(delta)
            }
            line = reader.readLine()
        }
        return sb.toString()
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

    // ---- Connect AI pairing (one-tap redemption) ----
    /** The only proxy host this build redeems pairing codes against or accepts endpoints for. */
    const val TRUSTED_PROXY_HOST = "chat-launcher-proxy.aiofsubhash.workers.dev"
    const val DEFAULT_CHAT_ENDPOINT = "https://chat-launcher-proxy.aiofsubhash.workers.dev/v1/chat/completions"
    const val PAIR_REDEEM_URL = "https://chat-launcher-proxy.aiofsubhash.workers.dev/v1/pair/redeem"

    sealed interface RedeemResult {
        data class Connected(val credential: String, val endpoint: String) : RedeemResult
        /** 400, or a 200 body missing fields or pointing at an untrusted endpoint. */
        object InvalidShape : RedeemResult
        /** 401: code unknown, expired, or already used. */
        object InvalidOrExpired : RedeemResult
        object RateLimited : RedeemResult
        object Unavailable : RedeemResult
        object NetworkError : RedeemResult
    }

    /** True only for https URLs on the pinned proxy host - a pairing response must not redirect credentials elsewhere. */
    fun isTrustedProxyEndpoint(url: String): Boolean = try {
        // Fail closed: only the exact pinned chat-completions URL - no other path, query, fragment, port or userinfo.
        val u = java.net.URI(url.trim())
        u.scheme.equals("https", ignoreCase = true) && u.host != null &&
            u.host.equals(TRUSTED_PROXY_HOST, ignoreCase = true) && u.userInfo == null &&
            u.port == -1 && (u.path ?: "") == "/v1/chat/completions" && u.query == null && u.fragment == null
    } catch (e: Exception) { false }

    /** Maps a redeem HTTP exchange to a result. Never logs; the code is never included in any result. */
    fun parseRedeemResponse(status: Int, body: String): RedeemResult = when (status) {
        200 -> try {
            val json = JSONObject(body)
            val credential = json.optString("credential", "")
            val endpoint = json.optString("endpoint", "")
            if (credential.isBlank() || !isTrustedProxyEndpoint(endpoint)) RedeemResult.InvalidShape
            else RedeemResult.Connected(credential, endpoint)
        } catch (e: Exception) { RedeemResult.InvalidShape }
        400 -> RedeemResult.InvalidShape
        401 -> RedeemResult.InvalidOrExpired
        429 -> RedeemResult.RateLimited
        503 -> RedeemResult.Unavailable
        else -> RedeemResult.NetworkError
    }

    internal fun defaultRedeemTransport(url: String, jsonBody: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            return status to body
        } finally {
            conn.disconnect()
        }
    }

    /** POST(redeemUrl, jsonBody) -> (status, body); swapped by tests. The redeem URL is a pinned constant. */
    internal var redeemTransport: (String, String) -> Pair<Int, String> = ::defaultRedeemTransport

    /** Redeem a single-use pairing code for a per-install credential. The code is never logged. */
    suspend fun redeemPairingCode(code: String): RedeemResult = withContext(Dispatchers.IO) {
        if (code.isBlank() || code.length > 512) return@withContext RedeemResult.InvalidShape
        try {
            val (status, body) = redeemTransport(PAIR_REDEEM_URL, "{\"code\":" + JSONObject.quote(code) + "}")
            parseRedeemResponse(status, body)
        } catch (e: Exception) { RedeemResult.NetworkError }
    }
}
