package com.subhash.chatlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deterministic, no-AI phrasings the owner actually uses. */
class MessageIntentsTest {

    @Test
    fun sendMessageToNameOnApp() {
        val p = MessageIntents.parse("send a message to Sumit on WhatsApp")!!
        assertEquals("Sumit", p.to)
        assertEquals("whatsapp", p.channel)
        assertEquals("com.whatsapp", p.pkg)
        assertNull(p.text)
    }

    @Test
    fun letsPrefixStripped() {
        val p = MessageIntents.parse("let's send a message to Sumit on WhatsApp")!!
        assertEquals("Sumit", p.to)
        assertEquals("com.whatsapp", p.pkg)
    }

    @Test
    fun colonText() {
        val p = MessageIntents.parse("send a message to Sumit on WhatsApp: happy birthday")!!
        assertEquals("Sumit", p.to)
        assertEquals("com.whatsapp", p.pkg)
        assertEquals("happy birthday", p.text)
    }

    @Test
    fun sayingText() {
        val p = MessageIntents.parse("message Sumit on telegram saying running late")!!
        assertEquals("Sumit", p.to)
        assertEquals("org.telegram.messenger", p.pkg)
        assertEquals("running late", p.text)
    }

    @Test
    fun bareTextAfterChannel() {
        val p = MessageIntents.parse("send a message to Sumit on whatsapp happy birthday")!!
        assertEquals("Sumit", p.to)
        assertEquals("happy birthday", p.text)
    }

    @Test
    fun sendNameTextOnChannel() {
        val p = MessageIntents.parse("send Sumit hello on whatsapp")!!
        assertEquals("Sumit", p.to)
        assertEquals("com.whatsapp", p.pkg)
        assertEquals("hello", p.text)
    }

    @Test
    fun sendNameOnChannelThenText() {
        val p = MessageIntents.parse("send Sumit on whatsapp hello there")!!
        assertEquals("Sumit", p.to)
        assertEquals("hello there", p.text)
    }

    @Test
    fun originalColonForm() {
        val p = MessageIntents.parse("send this message to Avinash: hello, running late")!!
        assertEquals("Avinash", p.to)
        assertNull(p.channel)
        assertEquals("hello, running late", p.text)
    }

    @Test
    fun sendNameColonText() {
        val p = MessageIntents.parse("send Sumit: yo")!!
        assertEquals("Sumit", p.to)
        assertEquals("yo", p.text)
    }

    @Test
    fun multiWordNameWithColon() {
        val p = MessageIntents.parse("send Sumit Kumar: yo")!!
        assertEquals("Sumit Kumar", p.to)
    }

    @Test
    fun unknownChannelKeepsParsed() {
        val p = MessageIntents.parse("send a message to Sumit on slack: hi")!!
        assertEquals("Sumit", p.to)
        assertEquals("slack", p.channel)
        assertNull(p.pkg)
        assertEquals("hi", p.text)
    }

    @Test
    fun textVerb() {
        val p = MessageIntents.parse("text Sumit: hi")!!
        assertEquals("Sumit", p.to)
    }

    // Non-messages stay non-messages (app open, rides, profiles, open questions).
    @Test
    fun appNameAloneNotAMessage() = assertNull(MessageIntents.parse("whatsapp"))

    @Test
    fun rideNotAMessage() = assertNull(MessageIntents.parse("uber to airport"))

    @Test
    fun focusNotAMessage() = assertNull(MessageIntents.parse("focus off"))

    @Test
    fun questionNotAMessage() = assertNull(MessageIntents.parse("what's a good birthday gift for my dad"))

    @Test
    fun failedCommandLooksPrivate() {
        assertTrue(MessageIntents.looksPrivateAction("send something somewhere somehow"))
        assertTrue(MessageIntents.looksPrivateAction("message"))
        assertTrue(!MessageIntents.looksPrivateAction("what's the weather"))
    }
}
