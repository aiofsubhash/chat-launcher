package com.subhash.chatlauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner Nov 7 15:33: typing an app name with spaces must still match the clickable app chip -
 *  surrounding/trailing whitespace ignored, brand words split with spaces ("whats app") match. */
class AppMatchTest {

    @Test
    fun exactAndCaseInsensitiveMatch() {
        assertTrue(appNameMatches("WhatsApp", "com.whatsapp", "WhatsApp"))
        assertTrue(appNameMatches("WhatsApp", "com.whatsapp", "whatsapp"))
    }

    @Test
    fun trailingAndLeadingSpacesStillMatch() {
        assertTrue(appNameMatches("WhatsApp", "com.whatsapp", "whatsapp "))
        assertTrue(appNameMatches("WhatsApp", "com.whatsapp", "  whatsapp"))
    }

    @Test
    fun spaceInsideBrandWordStillMatches() {
        assertTrue(appNameMatches("WhatsApp", "com.whatsapp", "whats app"))
        assertTrue(appNameMatches("PhonePe", "com.phonepe.app", "phone pe"))
    }

    @Test
    fun collapsedMultipleSpacesStillMatch() {
        assertTrue(appNameMatches("Google Maps", "com.google.maps", "google   maps"))
    }

    @Test
    fun packageNameStillMatches() {
        assertTrue(appNameMatches("WhatsApp", "com.whatsapp", "com.whats"))
    }

    @Test
    fun unrelatedTextDoesNotMatch() {
        assertFalse(appNameMatches("WhatsApp", "com.whatsapp", "telegram"))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(appNameMatches("WhatsApp", "com.whatsapp", "   "))
    }
}
