package com.subhash.chatlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders the adaptive launcher icon at real launcher sizes (48dp / 72dp) so the
 *  23:01 approved design (dark paper, faint wobbly grid, plain white bar) is verified
 *  as actual raster, not just resource wiring. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class IconRenderTest {

    private fun render(dp: Int): Bitmap {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val d = ctx.getDrawable(R.mipmap.ic_launcher)
        assertNotNull("ic_launcher resolves", d)
        val px = (dp * ctx.resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d!!.setBounds(0, 0, px, px)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun save(bmp: Bitmap, name: String) {
        val dir = File("build/outputs/iconrender")
        dir.mkdirs()
        FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun iconRendersAtLauncherSizes() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dark = android.graphics.Color.rgb(22, 21, 19)
        for (dp in listOf(48, 72)) {
            val bmp = render(dp)
            save(bmp, "ic_launcher_${dp}dp.png")
            // Center-pixel sanity: the dark paper covers the middle of the icon.
            val c = bmp.getPixel(bmp.width / 2, bmp.height / 3)
            fun dr(a: Int, b: Int) = Math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
                Math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
                Math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))
            assertTrue("center region is the dark paper at ${dp}dp", dr(c, dark) < 90)
            // The white bar is present in the lower band.
            var whiteFound = false
            for (y in (bmp.height * 0.62).toInt() until (bmp.height * 0.78).toInt()) {
                for (x in (bmp.width * 0.28).toInt() until (bmp.width * 0.72).toInt()) {
                    val p = bmp.getPixel(x, y)
                    if (android.graphics.Color.red(p) > 235 && android.graphics.Color.green(p) > 235 && android.graphics.Color.blue(p) > 235) { whiteFound = true; break }
                }
                if (whiteFound) break
            }
            assertTrue("white bar present at ${dp}dp", whiteFound)
        }
    }
}
