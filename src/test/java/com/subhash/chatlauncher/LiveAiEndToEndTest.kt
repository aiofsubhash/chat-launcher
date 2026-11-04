package com.subhash.chatlauncher

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit4.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LIVE end-to-end check against the deployed proxy. Skipped unless LIVE_AI_TICKET is set.
 * The ticket is a one-use, short-lived enrollment code; it is redeemed through the real
 * LlmClient.redeemPairingCode path, exactly as the installed app does, so this uses a
 * fresh install credential, NOT the credential on any real phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class LiveAiEndToEndTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val prompt = "What is the capital of France? Reply with one word."

    @Test
    fun liveClientAndUiAnswer() {
        val ticket = System.getenv("LIVE_AI_TICKET").orEmpty()
        assumeTrue("LIVE_AI_TICKET not set", ticket.isNotBlank())

        // 1) Real redeem, then the real streaming client against the live worker.
        val redeemed = runBlocking { LlmClient.redeemPairingCode(ticket) }
        assertTrue("redeem failed: ${redeemed::class.simpleName}", redeemed is LlmClient.RedeemResult.Connected)
        redeemed as LlmClient.RedeemResult.Connected
        val deltas = mutableListOf<String>()
        val reply = runBlocking {
            LlmClient.chatStream(
                redeemed.credential, LlmClient.defaultModel(LlmClient.Provider.OPENROUTER),
                listOf("user" to prompt), redeemed.endpoint,
            ) { deltas.add(it) }
        }
        println("LIVE_CLIENT deltas=${deltas.size} reply_len=${reply.length} reply=${reply.take(200)}")
        assertTrue("no streamed deltas, reply=$reply", deltas.isNotEmpty())
        assertTrue("client error instead of answer: $reply", !reply.startsWith("The AI couldn't answer") && !reply.startsWith("Couldn't reach"))
        assertTrue("answer missing Paris: $reply", reply.contains("Paris", ignoreCase = true))

        // 2) Real app: store the credential exactly as pairing does, then use the real UI.
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("api_key", redeemed.credential).putString("chat_endpoint", redeemed.endpoint).commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do").performTextInput(prompt)
        composeRule.onNodeWithText("Send").performClick()
        var shown = false
        var failed = false
        val deadline = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < deadline && !shown && !failed) {
            Thread.sleep(500)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.waitForIdle()
            shown = composeRule.onAllNodesWithText("Paris", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
            failed = composeRule.onAllNodesWithText("couldn't answer", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Couldn't reach", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        captureScreenRoboImage("build/outputs/roborazzi/live_ai_e2e.png")
        assertTrue("UI showed an AI error instead of an answer", !failed)
        assertTrue("UI never showed the answer within 90s", shown)
        println("LIVE_UI answer_visible=$shown error_visible=$failed")
    }
}
