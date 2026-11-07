package com.subhash.chatlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudCaptionTest {
    @Test
    fun streamingCaptionReadsAiIng() {
        assertEquals("ai-ing", cloudCaption(streaming = true))
    }

    @Test
    fun settledCaptionReadsAi() {
        assertEquals("ai", cloudCaption(streaming = false))
    }
}
