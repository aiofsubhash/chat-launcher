package com.subhash.chatlauncher

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PairingTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs get() = context.getSharedPreferences("chat_launcher", 0)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        LlmClient.redeemTransport = LlmClient::defaultRedeemTransport
        prefs.edit().clear().commit()
    }

    // ---- response mapping ----

    @Test
    fun redeem200TrustedEndpointConnects() {
        val r = LlmClient.parseRedeemResponse(200, """{"credential":"cred-1","endpoint":"${LlmClient.DEFAULT_CHAT_ENDPOINT}"}""")
        assertEquals(LlmClient.RedeemResult.Connected("cred-1", LlmClient.DEFAULT_CHAT_ENDPOINT), r)
    }

    @Test
    fun redeem200UntrustedEndpointRejected() {
        assertEquals(LlmClient.RedeemResult.InvalidShape,
            LlmClient.parseRedeemResponse(200, """{"credential":"cred-1","endpoint":"https://evil.example.com/v1/chat/completions"}"""))
        assertEquals(LlmClient.RedeemResult.InvalidShape,
            LlmClient.parseRedeemResponse(200, """{"credential":"cred-1","endpoint":"http://chat-launcher-proxy.aiofsubhash.workers.dev/v1/chat/completions"}"""))
    }

    @Test
    fun trustedEndpointRequiresExactPinnedUrl() {
        assertTrue(LlmClient.isTrustedProxyEndpoint(LlmClient.DEFAULT_CHAT_ENDPOINT))
        assertFalse(LlmClient.isTrustedProxyEndpoint("https://chat-launcher-proxy.aiofsubhash.workers.dev/v1/chat/completions?x=1"))
        assertFalse(LlmClient.isTrustedProxyEndpoint("https://chat-launcher-proxy.aiofsubhash.workers.dev/v1/chat/completions#f"))
        assertFalse(LlmClient.isTrustedProxyEndpoint("https://chat-launcher-proxy.aiofsubhash.workers.dev:8443/v1/chat/completions"))
        assertFalse(LlmClient.isTrustedProxyEndpoint("https://chat-launcher-proxy.aiofsubhash.workers.dev/v1/other"))
        assertFalse(LlmClient.isTrustedProxyEndpoint("https://user@chat-launcher-proxy.aiofsubhash.workers.dev/v1/chat/completions"))
    }

    @Test
    fun redeem200MissingFieldsRejected() {
        assertEquals(LlmClient.RedeemResult.InvalidShape, LlmClient.parseRedeemResponse(200, """{"endpoint":"${LlmClient.DEFAULT_CHAT_ENDPOINT}"}"""))
        assertEquals(LlmClient.RedeemResult.InvalidShape, LlmClient.parseRedeemResponse(200, "not json"))
    }

    @Test
    fun redeemStatusMapping() {
        val body = "{}"
        assertEquals(LlmClient.RedeemResult.InvalidShape, LlmClient.parseRedeemResponse(400, body))
        assertEquals(LlmClient.RedeemResult.InvalidOrExpired, LlmClient.parseRedeemResponse(401, body))
        assertEquals(LlmClient.RedeemResult.RateLimited, LlmClient.parseRedeemResponse(429, body))
        assertEquals(LlmClient.RedeemResult.Unavailable, LlmClient.parseRedeemResponse(503, body))
        assertEquals(LlmClient.RedeemResult.NetworkError, LlmClient.parseRedeemResponse(500, body))
    }

    @Test
    fun redeemPostsCodeToPinnedUrl() = runBlocking {
        var seenUrl = ""
        var seenBody = ""
        LlmClient.redeemTransport = { url, body -> seenUrl = url; seenBody = body; 200 to """{"credential":"c","endpoint":"${LlmClient.DEFAULT_CHAT_ENDPOINT}"}""" }
        val r = LlmClient.redeemPairingCode("code xyz")
        assertEquals(LlmClient.PAIR_REDEEM_URL, seenUrl)
        assertEquals("""{"code":"code xyz"}""", seenBody)
        assertTrue(r is LlmClient.RedeemResult.Connected)
    }

    @Test
    fun redeemTransportFailureIsNetworkError() = runBlocking {
        LlmClient.redeemTransport = { _, _ -> throw java.io.IOException("offline") }
        assertEquals(LlmClient.RedeemResult.NetworkError, LlmClient.redeemPairingCode("abc"))
        assertEquals(LlmClient.RedeemResult.InvalidShape, LlmClient.redeemPairingCode(""))
    }

    // ---- activity intent handling ----

    private fun flushUntil(cond: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!cond() && System.currentTimeMillis() - start < 8000) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
        ShadowLooper.idleMainLooper()
    }

    private fun pairIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)

    @Test
    fun pairIntentSavesCredentialAndIsConsumed() {
        LlmClient.redeemTransport = { _, _ -> 200 to """{"credential":"per-install-cred","endpoint":"${LlmClient.DEFAULT_CHAT_ENDPOINT}"}""" }
        ActivityScenario.launch<MainActivity>(pairIntent("chatlauncher://pair?code=one-use-code")).use { scenario ->
            flushUntil { HistoryStore(prefs).all().any { e -> e.text.contains("This installation is paired") } }
            assertEquals("per-install-cred", prefs.getString("api_key", ""))
            assertEquals(LlmClient.DEFAULT_CHAT_ENDPOINT, prefs.getString("chat_endpoint", ""))
            scenario.onActivity { assertNull("pairing intent data is cleared after handling", it.intent?.data) }
            val texts = HistoryStore(prefs).all().map { it.text }
            assertTrue(texts.any { it.contains("This installation is paired") })
            assertFalse(texts.any { it.contains("per-install-cred") || it.contains("one-use-code") })
        }
    }

    @Test
    fun pairIntentFragmentCodeSavesCredential() {
        LlmClient.redeemTransport = { _, body ->
            assertEquals("""{"code":"frag-code"}""", body)
            200 to """{"credential":"frag-cred","endpoint":"${LlmClient.DEFAULT_CHAT_ENDPOINT}"}"""
        }
        ActivityScenario.launch<MainActivity>(pairIntent("chatlauncher://pair#code=frag-code")).use {
            flushUntil { prefs.getString("api_key", "") == "frag-cred" }
            assertEquals("frag-cred", prefs.getString("api_key", ""))
            assertEquals(LlmClient.DEFAULT_CHAT_ENDPOINT, prefs.getString("chat_endpoint", ""))
        }
    }

    @Test
    fun pairIntentExpiredCodeIsHonestAndSavesNothing() {
        LlmClient.redeemTransport = { _, _ -> 401 to """{"error":"expired"}""" }
        ActivityScenario.launch<MainActivity>(pairIntent("chatlauncher://pair?code=used-code")).use {
            flushUntil { HistoryStore(prefs).all().any { e -> e.text.contains("expired or already used") } }
            assertEquals("", prefs.getString("api_key", ""))
            assertEquals("", prefs.getString("chat_endpoint", ""))
        }
    }

    @Test
    fun pairIntentMissingCodeIsHonest() {
        ActivityScenario.launch<MainActivity>(pairIntent("chatlauncher://pair")).use {
            flushUntil { HistoryStore(prefs).all().any { e -> e.text.contains("didn't look right") } }
            assertEquals("", prefs.getString("api_key", ""))
        }
    }

    @Test
    fun nonPairViewIntentIgnored() {
        ActivityScenario.launch<MainActivity>(pairIntent("https://example.com/foo?code=x")).use { scenario ->
            flushUntil { true }
            scenario.onActivity { assertEquals("https://example.com/foo?code=x", it.intent?.data.toString()) }
            assertEquals("", prefs.getString("api_key", ""))
        }
    }
}
