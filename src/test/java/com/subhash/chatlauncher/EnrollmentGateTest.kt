package com.subhash.chatlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Automatic AI enrollment decisions (owner 14:43): saved credential wins, attempts are
 *  scoped to the ticket identity, only definitive outcomes consume an attempt. */
class EnrollmentGateTest {

    private val ticketA = "ticket-aaa"
    private val ticketB = "ticket-bbb"

    @Test
    fun savedCredentialAlwaysWins() {
        assertFalse(EnrollmentGate.shouldAttempt(hasCredential = true, ticket = ticketA, attemptedTicketHash = null))
    }

    @Test
    fun blankTicketNeverAttempts() {
        assertFalse(EnrollmentGate.shouldAttempt(hasCredential = false, ticket = "", attemptedTicketHash = null))
    }

    @Test
    fun firstLaunchWithTicketAttempts() {
        assertTrue(EnrollmentGate.shouldAttempt(hasCredential = false, ticket = ticketA, attemptedTicketHash = null))
    }

    @Test
    fun consumedTicketIsNotRetried() {
        val hash = EnrollmentGate.ticketHash(ticketA)
        assertFalse(EnrollmentGate.shouldAttempt(hasCredential = false, ticket = ticketA, attemptedTicketHash = hash))
    }

    @Test
    fun freshTicketInRenewedBuildAttemptsAgain() {
        val oldHash = EnrollmentGate.ticketHash(ticketA)
        assertTrue(EnrollmentGate.shouldAttempt(hasCredential = false, ticket = ticketB, attemptedTicketHash = oldHash))
    }

    @Test
    fun definitiveOutcomesConsumeTheAttempt() {
        assertTrue(EnrollmentGate.consumesAttempt(LlmClient.RedeemResult.Connected("cred", "https://chat-launcher-proxy.aiofsubhash.workers.dev/v1/chat")))
        assertTrue(EnrollmentGate.consumesAttempt(LlmClient.RedeemResult.InvalidOrExpired))
        assertTrue(EnrollmentGate.consumesAttempt(LlmClient.RedeemResult.InvalidShape))
    }

    @Test
    fun transientOutcomesAllowALaterRelaunchRetry() {
        assertFalse(EnrollmentGate.consumesAttempt(LlmClient.RedeemResult.RateLimited))
        assertFalse(EnrollmentGate.consumesAttempt(LlmClient.RedeemResult.Unavailable))
        assertFalse(EnrollmentGate.consumesAttempt(LlmClient.RedeemResult.NetworkError))
    }

    @Test
    fun ticketHashIsStableAndDistinct() {
        assertEquals(EnrollmentGate.ticketHash(ticketA), EnrollmentGate.ticketHash(ticketA))
        assertTrue(EnrollmentGate.ticketHash(ticketA) != EnrollmentGate.ticketHash(ticketB))
        assertEquals(64, EnrollmentGate.ticketHash(ticketA).length)
    }
}
