package com.subhash.chatlauncher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real MainActivity UI + real LlmClient.chatStream over real HTTP, against a LOCAL server that
 * replays the exact bytes the deployed Worker returned (captured live 2026-11-04 22:47 IST,
 * HTTP 200 text/event-stream). The server side is a REPLAY, not the live Worker; the live
 * Worker is proven separately by calling LlmClient directly. No credential is used here.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class LiveReplayUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val sse = """data: {"id":"f86a18e415434950b85f509721024979","object":"chat.completion.chunk","created":1793812667,"model":"@cf/google/gemma-4-26b-a4b-it-external","choices":[{"index":0,"delta":{"reasoning_content":null,"role":"assistant","content":""},"logprobs":null,"finish_reason":null,"matched_stop":null}],"usage":{"prompt_tokens":25,"completion_tokens":0,"total_tokens":25,"prompt_tokens_details":{"cached_tokens":0},"neurons":0.22727271914482117},"p":"j2gpa07dvy4kf5o5tiple3rio1fha"}

data: {"id":"f86a18e415434950b85f509721024979","object":"chat.completion.chunk","created":1793812667,"model":"@cf/google/gemma-4-26b-a4b-it-external","choices":[{"index":0,"delta":{"reasoning_content":null,"content":"Paris"},"logprobs":null,"finish_reason":null,"matched_stop":null}],"usage":{"prompt_tokens":0,"completion_tokens":1,"total_tokens":1,"prompt_tokens_details":{"cached_tokens":0},"neurons":0.027272727340459824},"p":"6z5ngkk1dvblya0sif667k4z3he9gf5otu7vj"}

data: {"id":"f86a18e415434950b85f509721024979","object":"chat.completion.chunk","created":1793812667,"model":"@cf/google/gemma-4-26b-a4b-it-external","choices":[{"index":0,"delta":{"reasoning_content":null},"logprobs":null,"finish_reason":"stop","matched_stop":106}],"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0,"prompt_tokens_details":{"cached_tokens":0},"neurons":0},"p":"snd4l"}

data: {"id":"f86a18e415434950b85f509721024979","object":"chat.completion.chunk","created":1793812667,"model":"@cf/google/gemma-4-26b-a4b-it-external","choices":[],"usage":{"prompt_tokens":0,"completion_tokens":1,"total_tokens":1,"prompt_tokens_details":{"cached_tokens":0},"neurons":0.027272727340459824},"p":"yjvkf1e0g5ey7i2phb6b2uib12lp5daeiz7pv"}

data: {"response":"","usage":{"prompt_tokens":25,"completion_tokens":2,"total_tokens":27,"prompt_tokens_details":{"cached_tokens":0},"neurons":0.2818181738257408}}

data: [DONE]

"""
    private lateinit var server: ServerSocket
    @Volatile private var seenAuth: String? = null
    @Volatile private var seenBody: String = ""

    @Before
    fun up() {
        server = ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            try {
                while (!server.isClosed) {
                    val s = server.accept()
                    s.use { sock ->
                        val inp = sock.getInputStream()
                        val head = StringBuilder()
                        while (!head.endsWith("\r\n\r\n")) {
                            val c = inp.read()
                            if (c < 0) break
                            head.append(c.toChar())
                        }
                        val lines = head.toString().split("\r\n")
                        seenAuth = lines.firstOrNull { it.startsWith("Authorization:", true) }?.substringAfter(":")?.trim()
                        val len = lines.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(":")?.trim()?.toInt() ?: 0
                        val buf = ByteArray(len)
                        var off = 0
                        while (off < len) { val n = inp.read(buf, off, len - off); if (n < 0) break; off += n }
                        seenBody = String(buf, Charsets.UTF_8)
                        val bytes = sse.toByteArray()
                        val out = sock.getOutputStream()
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: " + bytes.size + "\r\nConnection: close\r\n\r\n").toByteArray())
                        out.write(bytes)
                        out.flush()
                    }
                }
            } catch (e: Exception) { /* server closed */ }
        }.apply { isDaemon = true; start() }
    }

    @After
    fun down() { server.close() }

    @Test
    fun realUiShowsStreamedAnswer() {
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("api_key", "replay-test-credential")
            .putString("chat_endpoint", "http://127.0.0.1:${server.localPort}/v1/chat/completions").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("write what you want to do")
            .performTextInput("What is the capital of France? Reply with one word.")
        composeRule.onNodeWithText("Send").performClick()
        val deadline = System.currentTimeMillis() + 20_000
        var shown = false
        while (System.currentTimeMillis() < deadline && !shown) {
            Thread.sleep(200)
            composeRule.waitForIdle()
            shown = composeRule.onAllNodesWithText("Paris", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        captureScreenRoboImage("build/outputs/roborazzi/replay_ui_answer.png")
        assertEquals("Bearer replay-test-credential", seenAuth)
        assertTrue("request must be one user message: $seenBody", seenBody.contains("France") && seenBody.contains("\"stream\":true"))
        assertTrue("UI never showed the streamed answer", shown)
        assertTrue("no error bubble", composeRule.onAllNodesWithText("couldn't answer", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty() &&
            composeRule.onAllNodesWithText("Couldn't reach", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    }
}
