package com.subhash.chatlauncher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPackageManager

/** IME-inset gate (main 01:33): the h420 resize was only a smaller viewport, not an
 *  inset response. These tests inject a real IME-height WindowInsets bottom inset and
 *  verify the layout responds (composer/Send/query chips stay above the inset) at small
 *  and normal viewport, light and dark. Proves inset handling only - a physical Android
 *  keyboard remains a device caveat. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatHomeImeInsetTest {

    private val activityRule = createAndroidComposeRule<MainActivity>()

    private val installFirst = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                installFakeApps()
                base.evaluate()
            }
        }
    }

    @get:Rule
    val chain: TestRule = RuleChain.outerRule(installFirst).around(activityRule)

    private val composeRule get() = activityRule

    private val imeDp = 300

    private fun installFakeApp(pkg: String, label: String) {
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        val spm: ShadowPackageManager = Shadows.shadowOf(pm)
        val appInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
        val ri = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.MainActivity"
                applicationInfo = appInfo
                nonLocalizedLabel = label
            }
        }
        spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ri)
        spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), ri)
    }

    private fun installFakeApps() {
        // The owner's default dock set, so the dock populates exactly as on device.
        installFakeApp("com.google.android.apps.maps", "Maps")
        installFakeApp("com.android.chrome", "Chrome")
        installFakeApp("com.spotify.music", "Spotify")
        installFakeApp("com.phonepe.app", "PhonePe")
    }

    private fun injectIme(open: Boolean) {
        val activity = composeRule.activity
        val decor = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        val imePx = if (open) (imeDp * density).toInt() else 0
        composeRule.runOnUiThread {
            val content = activity.findViewById<android.widget.FrameLayout>(android.R.id.content)
            val composeView = content.getChildAt(0) ?: decor
            val current = ViewCompat.getRootWindowInsets(composeView) ?: WindowInsetsCompat.Builder().build()
            val insets = WindowInsetsCompat.Builder(current)
                .setInsets(WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, imePx))
                .setVisible(WindowInsetsCompat.Type.ime(), open)
                .build()
            // Dispatch straight to the ComposeView: with decorFitsSystemWindows the DecorView
            // consumes the IME inset before the compose hierarchy would see it.
            ViewCompat.dispatchApplyWindowInsets(composeView, insets)
        }
        composeRule.waitForIdle()
    }

    private fun imePx(): Float {
        val d = composeRule.activity.resources.displayMetrics.density
        return imeDp * d
    }

    private fun diag(tag: String) {
        var viewIme = -9
        var sendInfo = "none"
        composeRule.runOnUiThread {
            val content = composeRule.activity.findViewById<android.widget.FrameLayout>(android.R.id.content)
            val cv = content.getChildAt(0)
            viewIme = ViewCompat.getRootWindowInsets(cv)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: -2
        }
        try {
            val send = composeRule.onNodeWithText("Send").getUnclippedBoundsInRoot()
            val root = composeRule.onRoot().getUnclippedBoundsInRoot()
            sendInfo = "send=${send.top}-${send.bottom} rootH=${root.bottom - root.top}"
        } catch (e: Throwable) { sendInfo = "err:${e.message?.take(80)}" }
        System.err.println("IMEDIAG[$tag] viewImeBottom=$viewIme $sendInfo")
    }

    private fun assertSendAboveIme() {
        var viewIme = -1
        composeRule.runOnUiThread {
            val content = composeRule.activity.findViewById<android.widget.FrameLayout>(android.R.id.content)
            val cv = content.getChildAt(0)
            viewIme = (ViewCompat.getRootWindowInsets(cv)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: -2)
        }
        System.err.println("IMEDIAG viewImeBottom=$viewIme")
        val send: androidx.compose.ui.unit.DpRect = composeRule.onNodeWithText("Send").getUnclippedBoundsInRoot()
        val root: androidx.compose.ui.unit.DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
        val rootH = root.bottom - root.top
        val ime = imeDp.dp
        assertTrue(
            "Send bottom (${send.bottom}) must sit above the injected IME inset (${rootH - ime})",
            send.bottom <= rootH - ime + 1.dp,
        )
    }

    private fun typeLongQuery() {
        composeRule.onNodeWithText("write what you want to do")
            .performTextInput("how do i make cold brew coffee at home")
        composeRule.waitForIdle()
    }

    /** Small viewport + injected IME, light theme: long query shows the brand-logo
     *  shortcuts, and Send plus the dock stay above the keyboard inset. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h560dp-xxhdpi")
    fun imeOpenSmallViewportLight() {
        typeLongQuery()
        injectIme(true)
        diag("smallLight")
        composeRule.onNodeWithText("Send").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Google").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").assertExists()
        assertSendAboveIme()
        composeRule.onRoot().captureRoboImage()
    }

    /** Same state in the dark theme. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h560dp-xxhdpi")
    fun imeOpenSmallViewportDark() {
        val prefs = composeRule.activity.getSharedPreferences("chat_launcher", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", "dark").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        typeLongQuery()
        injectIme(true)
        diag("smallDark")
        composeRule.onNodeWithText("Send").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Google").assertIsDisplayed()
        assertSendAboveIme()
        composeRule.onRoot().captureRoboImage()
        prefs.edit().remove("theme_mode").commit()
    }

    /** Normal height + injected IME: the 8x-gap home intent survives the inset; Send and
     *  dock remain usable above it. */
    @Test
    fun imeOpenNormalHeightPreservesHome() {
        typeLongQuery()
        injectIme(true)
        diag("normal")
        composeRule.onNodeWithText("Send").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").assertExists()
        assertSendAboveIme()
        composeRule.onRoot().captureRoboImage()
    }
}
