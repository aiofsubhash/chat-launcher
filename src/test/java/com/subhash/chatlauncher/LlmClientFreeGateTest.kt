package com.subhash.chatlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmClientFreeGateTest {
    @Test
    fun openRouterRequestEnforcesZeroPriceProviderSide() {
        val body = LlmClient.openRouterBody("nvidia/nemotron-3-ultra-550b-a55b:free", listOf("user" to "hi"))
        val json = org.json.JSONObject(body)
        assertEquals("nvidia/nemotron-3-ultra-550b-a55b:free", json.getString("model"))
        val maxPrice = json.getJSONObject("provider").getJSONObject("max_price")
        assertEquals("0", maxPrice.getString("prompt"))
        assertEquals("0", maxPrice.getString("completion"))
        // Default model path is covered too: gatedModel(blank) returns the default, which goes through the same body.
        val defaultBody = LlmClient.openRouterBody(LlmClient.gatedModel(LlmClient.Provider.OPENROUTER, "", null)!!, emptyList())
        assertEquals("0", org.json.JSONObject(defaultBody).getJSONObject("provider").getJSONObject("max_price").getString("prompt"))
    }

    @Test
    fun freeOpenRouterModelsParsesZeroPricedOnly() {
        val catalog = """
            {"data":[
              {"id":"nvidia/nemotron-3-ultra-550b-a55b:free","pricing":{"prompt":"0","completion":"0"}},
              {"id":"openai/gpt-5","pricing":{"prompt":"0.00001","completion":"0.00003"}},
              {"id":"vendor/no-pricing"}
            ]}
        """.trimIndent()
        assertEquals(setOf("nvidia/nemotron-3-ultra-550b-a55b:free"), LlmClient.freeOpenRouterModels(catalog))
    }

    @Test
    fun openRouterPaidIdRejectedWithCatalog() {
        val free = setOf("nvidia/nemotron-3-ultra-550b-a55b:free")
        assert(!LlmClient.isAllowedModel(LlmClient.Provider.OPENROUTER, "openai/gpt-5", free))
        assert(LlmClient.isAllowedModel(LlmClient.Provider.OPENROUTER, "nvidia/nemotron-3-ultra-550b-a55b:free", free))
    }

    @Test
    fun openRouterFailsClosedWhenPricingUnverifiable() {
        // No catalog evidence - even :free-suffixed ids are rejected rather than trusted blindly.
        assert(!LlmClient.isAllowedModel(LlmClient.Provider.OPENROUTER, "qwen/qwen3-32b:free", null))
        assert(!LlmClient.isAllowedModel(LlmClient.Provider.OPENROUTER, "openai/gpt-5", null))
        assert(LlmClient.isAllowedModel(LlmClient.Provider.ANTHROPIC, "claude-sonnet-4-5-20250929", null))
    }

    @Test
    fun gatedModelNeverSilentlySwapsRejectedSaved() {
        val free = setOf("nvidia/nemotron-3-ultra-550b-a55b:free", "qwen/qwen3-32b:free")
        // Rejected saved model -> null (caller surfaces an error), never a silent swap.
        assertEquals(null, LlmClient.gatedModel(LlmClient.Provider.OPENROUTER, "openai/gpt-5", free))
        assertEquals(null, LlmClient.gatedModel(LlmClient.Provider.OPENROUTER, "qwen/qwen3-32b:free", null))
        // Verified saved model and blank (default) still work.
        assertEquals("qwen/qwen3-32b:free", LlmClient.gatedModel(LlmClient.Provider.OPENROUTER, "qwen/qwen3-32b:free", free))
        assertEquals("nvidia/nemotron-3-ultra-550b-a55b:free", LlmClient.gatedModel(LlmClient.Provider.OPENROUTER, "", free))
        assertEquals("claude-sonnet-4-5-20250929", LlmClient.gatedModel(LlmClient.Provider.ANTHROPIC, "", null))
    }
}
