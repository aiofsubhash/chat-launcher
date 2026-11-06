package com.subhash.chatlauncher

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Owner Nov 7 00:56: "once I hit send get the keyboard down". Tapping Send must hide the IME
 *  whatever the message routes to; Return still inserts a newline and never sends. Earlier
 *  keyboard-dismiss coverage asserted hide with the IME never shown (IMEbefore=false), which
 *  proved nothing. This test fails unless the IME is actually visible BEFORE the Send tap and
 *  hidden after. A physical keyboard's exact animation remains a device caveat. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class KeyboardDismissOnSendTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun imm() = Shadows.shadowOf(
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(InputMethodManager::class.java)
    )

    @Test
    fun sendHidesVisibleKeyboard() {
        // Focus the composer and type; focusing a text field requests the IME.
        composeRule.onNodeWithText("write what you want to do").performTextInput("hello there")
        composeRule.waitForIdle()
        // If the test environment did not surface the IME on focus, show it against the window
        // so the precondition below is a REAL visible keyboard, not a vacuous one.
        if (!imm().isSoftInputVisible) {
            val view = composeRule.activity.window.decorView
            composeRule.runOnUiThread {
                ApplicationProvider.getApplicationContext<Context>()
                    .getSystemService(InputMethodManager::class.java)
                    .showSoftInput(view, 0)
            }
            composeRule.waitForIdle()
        }
        assertTrue("IME must be visible before Send for this test to prove anything", imm().isSoftInputVisible)

        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) { !imm().isSoftInputVisible }
        assertFalse("IME must be hidden after Send", imm().isSoftInputVisible)
    }
}
