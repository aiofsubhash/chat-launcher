package com.subhash.chatlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmClientTest {

    @Test
    fun openRouterKeyDetected() {
        assertEquals(LlmClient.Provider.OPENROUTER, LlmClient.providerForKey("sk-or-v1-abc123"))
    }

    @Test
    fun anthropicKeyDefault() {
        assertEquals(LlmClient.Provider.ANTHROPIC, LlmClient.providerForKey("sk-ant-api03-abc123"))
        assertEquals(LlmClient.Provider.ANTHROPIC, LlmClient.providerForKey("anything-else"))
    }

    @Test
    fun openRouterDefaultIsFreeModel() {
        assertEquals("nvidia/nemotron-3-ultra-550b-a55b:free", LlmClient.defaultModel(LlmClient.Provider.OPENROUTER))
        assert(LlmClient.defaultModel(LlmClient.Provider.OPENROUTER).endsWith(":free"))
    }

    @Test
    fun anthropicDefaultIsClaude() {
        assertEquals("claude-sonnet-4-5-20250929", LlmClient.defaultModel(LlmClient.Provider.ANTHROPIC))
    }
}
