package com.subhash.chatlauncher

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Real-wire contract tests: the actual chatProxyStream serialization through HttpURLConnection
 *  against a local server - the exact failing shape from the 22:51 device 400. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LlmClientProxyTest {

    private fun serve(code: Int, body: String, onRequest: (String, String?) -> Unit): Pair<ServerSocket, String> {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread {
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                var contentLength = 0
                var auth: String? = null
                input.readLine() // request line
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("content-length:", true)) contentLength = line.substringAfter(':').trim().toInt()
                    if (line.startsWith("authorization:", true)) auth = line.substringAfter(':').trim()
                }
                val chars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(chars, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                onRequest(String(chars, 0, read), auth)
                val bytes = body.toByteArray()
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 $code X\r\ncontent-type: text/event-stream\r\ncontent-length: ${bytes.size}\r\nconnection: close\r\n\r\n".toByteArray())
                out.write(bytes)
                out.flush()
            }
            server.close()
        }
        return server to "http://127.0.0.1:${server.localPort}/v1/chat/completions"
    }

    @Test
    fun proxyWireSendsExactlyOneCurrentUserMessage() {
        var seenBody = ""
        var seenAuth: String? = null
        val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}\n\ndata: [DONE]\n\n"
        val (server, url) = serve(200, sse) { b, a -> seenBody = b; seenAuth = a }
        try {
            val deltas = StringBuilder()
            val reply = runBlocking {
                LlmClient.chatStream(
                    "cred-123", "server-fixed-model",
                    listOf("user" to "old question", "assistant" to "old answer", "user" to "what's up"),
                    url,
                ) { d -> deltas.append(d) }
            }
            val json = JSONObject(seenBody)
            val messages = json.getJSONArray("messages")
            assertEquals("exactly one message leaves the phone", 1, messages.length())
            assertEquals("user", messages.getJSONObject(0).getString("role"))
            assertEquals("what's up", messages.getJSONObject(0).getString("content"))
            assertFalse("no system scaffold on the wire", seenBody.contains("\"system\""))
            assertFalse("no history on the wire", seenBody.contains("old question"))
            assertEquals("Bearer cred-123", seenAuth)
            assertEquals("Hello", reply)
            assertEquals("Hello", deltas.toString())
        } finally {
            server.close()
        }
    }

    @Test
    fun proxyErrorIsMappedNotDumped() {
        val (server, url) = serve(400, "{\"error\":{\"message\":\"expected exactly one message\",\"type\":\"proxy_error\"}}") { _, _ -> }
        try {
            val reply = runBlocking {
                LlmClient.chatStream("cred-123", "m", listOf("user" to "hi"), url) {}
            }
            assertEquals("The AI couldn't answer (error 400) - expected exactly one message", reply)
            assertFalse("no raw JSON in the UI line", reply.contains("{"))
        } finally {
            server.close()
        }
    }
}
