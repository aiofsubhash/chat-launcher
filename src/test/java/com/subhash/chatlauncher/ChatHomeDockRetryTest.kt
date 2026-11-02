package com.subhash.chatlauncher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPackageManager

/** Production retry path (main 00:46): when the FIRST installed-apps query is empty -
 *  a launcher cold start racing package visibility - the bounded retry in MainActivity
 *  must settle the dock once apps become visible, with no typing. This guards the
 *  mitigation only; it does not establish the 22:53 device root cause. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatHomeDockRetryTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dockPopulatesWhenAppsAppearAfterLaunch() {
        // No fake apps registered before launch: the first composition's query is empty.
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("chat_launcher", 0)
            .edit().remove("active_profile").remove("dock_apps").commit()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Maps").assertDoesNotExist()

        // An app becomes visible after the first composition (the race window).
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        val spm: ShadowPackageManager = Shadows.shadowOf(pm)
        val pkg = "com.google.android.apps.maps"
        val appInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = "Maps" }
        val ri = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.MainActivity"
                applicationInfo = appInfo
                nonLocalizedLabel = "Maps"
            }
        }
        spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ri)
        spm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg), ri)

        // The retry loop (100ms ticks, bounded at 10) must settle the dock on its own.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Maps").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
    }
}
