package com.subhash.chatlauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast checks for both palettes - the a11y evidence for the theme work. */
class ContrastTest {

    private fun luminance(c: Color): Double {
        val argb = c.toArgb()
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    private fun blend(fg: Color, alpha: Float, bg: Color): Color {
        val f = fg.toArgb(); val b = bg.toArgb()
        fun mix(fc: Int, bc: Int) = (fc * alpha + bc * (1 - alpha)).toInt()
        return Color(0xFF000000.toInt() or (mix((f shr 16) and 0xFF, (b shr 16) and 0xFF) shl 16) or (mix((f shr 8) and 0xFF, (b shr 8) and 0xFF) shl 8) or mix(f and 0xFF, b and 0xFF))
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a); val lb = luminance(b)
        val (hi, lo) = if (la >= lb) la to lb else lb to la
        return (hi + 0.05) / (lo + 0.05)
    }

    private val schemes = listOf(calmColors, darkCalmColors)

    @Test
    fun bodyTextMeetsAaInBothThemes() {
        for (scheme in schemes) {
            assertTrue("onBackground vs background", ratio(scheme.onBackground, scheme.background) >= 4.5)
            assertTrue("onSurface vs surface", ratio(scheme.onSurface, scheme.surface) >= 4.5)
            assertTrue("onSurfaceVariant vs background", ratio(scheme.onSurfaceVariant, scheme.background) >= 4.5)
            assertTrue("onSecondaryContainer vs secondaryContainer", ratio(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5)
            assertTrue("onPrimaryContainer vs primaryContainer", ratio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5)
        }
    }

    @Test
    fun sendButtonTextMeetsAaInBothThemes() {
        for (scheme in schemes) {
            val buttonBg = blend(accentBrown, 0.8f, scheme.background)
            assertTrue("send text on button (${ratio(sendButtonTextColor, buttonBg)})", ratio(sendButtonTextColor, buttonBg) >= 4.5)
        }
    }

    @Test
    fun boldAccentMeetsLargeTextAaInBothThemes() {
        // Selected states are bold text on the accent brown: 3:1 minimum for large/bold text.
        for (scheme in schemes) {
            assertTrue("accent vs background", ratio(accentBrown, scheme.background) >= 3.0)
        }
    }
}
