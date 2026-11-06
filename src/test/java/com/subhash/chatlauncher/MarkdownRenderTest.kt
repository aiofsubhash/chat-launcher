package com.subhash.chatlauncher

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual check of the reply renderer on the actual answer shape from the Nov 7 00:30
 * recording: bold spans, a heading, a dropped rule and a fenced code block - rendered as
 * formatting, never as raw ** or ###, at one consistent text size.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MarkdownRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun replyMarkdownRendersAsFormatting() {
        val reply = "To find the implicit state machines in your application, you need to look for places where **logic depends on the history of previous events** or where **the system's behavior changes based on its current \"mode.\"**\n\nAn implicit state machine exists whenever a piece of code says: *\"If X happened, and we are currently in state Y, then do Z.\"*\n\n---\n\n### 1. Search for \"Boolean Combinations\"\nThe most common type:\n```\nif (isLoading && !isError && dataLoaded) { ... }\n```\n* **The Pattern:** flags used in `if` statements."
        composeRule.activity.setContent {
            MaterialTheme(colorScheme = calmColors) {
                // On the paper background: a bare composable captured on the default dark
                // gray canvas misread as a contrast defect (main review Nov 7 01:14).
                androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MarkdownText(reply, fontFamily = null)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/reply_markdown.png")
    }
}
