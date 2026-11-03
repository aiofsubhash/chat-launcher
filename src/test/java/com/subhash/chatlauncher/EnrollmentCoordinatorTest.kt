package com.subhash.chatlauncher

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enrollment lifecycle (owner 14:43): no parallel redemption of the same single-use ticket
 * across an activity recreate, a late Connected never overwrites a credential saved
 * meanwhile, and a transient failure is neither consumed nor retried within the launch.
 * Everything is synthetic: redeem is a test double, no network.
 */
class EnrollmentCoordinatorTest {

    private class FakeStorage : EnrollmentCoordinator.AttemptStorage {
        override var attemptedTicketHash: String? = null
    }

    private fun newCoordinator(
        storage: FakeStorage,
        hasCredential: () -> Boolean = { false },
        saveCredential: (String, String) -> Unit = { _, _ -> },
        redeem: suspend (String) -> LlmClient.RedeemResult = { LlmClient.RedeemResult.Connected("cred", "https://chat-launcher-proxy.aiofsubhash.workers.dev/v1/chat") },
        messages: MutableList<String> = mutableListOf(),
        scope: CoroutineScope,
    ) = EnrollmentCoordinator(storage, hasCredential, saveCredential, redeem, { messages += it }, scope)

    @Test
    fun connectedStoresCredentialAndConsumesAttempt() = runBlocking {
        val storage = FakeStorage()
        var stored: Pair<String, String>? = null
        val messages = mutableListOf<String>()
        val c = newCoordinator(storage, saveCredential = { cred, ep -> stored = cred to ep }, messages = messages, scope = this)
        c.maybeAutoEnroll("ticket-a")!!.join()
        assertEquals("cred", stored?.first)
        assertEquals(EnrollmentGate.ticketHash("ticket-a"), storage.attemptedTicketHash)
        assertEquals(EnrollmentCoordinator.MSG_CONNECTED, messages.last())
    }

    @Test
    fun recreateDuringRedeemNeverStartsParallelRedemption() = runBlocking {
        val storage = FakeStorage()
        var redeemCalls = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val c = newCoordinator(storage, redeem = {
            redeemCalls++
            started.complete(Unit)
            release.await()
            LlmClient.RedeemResult.Connected("cred", "e")
        }, scope = scope)
        val first = c.maybeAutoEnroll("ticket-a")
        started.await()
        val second = c.maybeAutoEnroll("ticket-a") // activity recreate while the redeem is live
        assertNull(second)
        release.complete(Unit)
        first!!.join()
        assertEquals(1, redeemCalls)
        assertEquals(EnrollmentGate.ticketHash("ticket-a"), storage.attemptedTicketHash)
    }

    @Test
    fun lateConnectedNeverOverwritesCredentialSavedMeanwhile() = runBlocking {
        val storage = FakeStorage()
        var credential: String? = null
        var hasCredential = false
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val messages = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val c = newCoordinator(
            storage,
            hasCredential = { hasCredential },
            saveCredential = { cred, _ -> credential = cred },
            redeem = {
                started.complete(Unit)
                release.await()
                LlmClient.RedeemResult.Connected("ticket-cred", "e")
            },
            messages = messages,
            scope = scope,
        )
        val job = c.maybeAutoEnroll("ticket-a")
        started.await()
        // the user finishes manual pairing while the ticket redeem is still in flight
        hasCredential = true
        credential = "manual-cred"
        release.complete(Unit)
        job!!.join()
        assertEquals("manual-cred", credential) // untouched
        assertEquals(EnrollmentGate.ticketHash("ticket-a"), storage.attemptedTicketHash) // ticket spent
        assertEquals(EnrollmentCoordinator.MSG_ALREADY_CONNECTED, messages.last())
    }

    @Test
    fun transientFailureIsNotConsumedAndRetriesOnlyOnNextLaunch() = runBlocking {
        val storage = FakeStorage()
        var redeemCalls = 0
        val messages = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val redeem: suspend (String) -> LlmClient.RedeemResult = { redeemCalls++; LlmClient.RedeemResult.NetworkError }
        newCoordinator(storage, redeem = redeem, messages = messages, scope = scope).maybeAutoEnroll("ticket-a")!!.join()
        assertEquals(1, redeemCalls) // no in-launch retry loop
        assertNull(storage.attemptedTicketHash) // not consumed
        assertEquals(EnrollmentCoordinator.MSG_RETRY_NEXT_LAUNCH, messages.last())
        newCoordinator(storage, redeem = redeem, scope = scope).maybeAutoEnroll("ticket-a")!!.join() // next launch
        assertEquals(2, redeemCalls)
    }

    @Test
    fun definitiveFailureConsumesAttemptAndNeverRetries() = runBlocking {
        val storage = FakeStorage()
        var redeemCalls = 0
        val messages = mutableListOf<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val redeem: suspend (String) -> LlmClient.RedeemResult = { redeemCalls++; LlmClient.RedeemResult.InvalidOrExpired }
        newCoordinator(storage, redeem = redeem, messages = messages, scope = scope).maybeAutoEnroll("ticket-a")!!.join()
        assertNotNull(storage.attemptedTicketHash)
        assertEquals(EnrollmentCoordinator.MSG_NEEDS_RENEWAL, messages.last())
        assertNull(newCoordinator(storage, redeem = redeem, scope = scope).maybeAutoEnroll("ticket-a"))
        assertEquals(1, redeemCalls)
    }

    @Test
    fun freshTicketInRenewedBuildAttemptsAgainAfterConsumption() = runBlocking {
        val storage = FakeStorage()
        storage.attemptedTicketHash = EnrollmentGate.ticketHash("ticket-a") // old ticket spent
        var redeemCalls = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val redeem: suspend (String) -> LlmClient.RedeemResult = { redeemCalls++; LlmClient.RedeemResult.Connected("cred", "e") }
        newCoordinator(storage, redeem = redeem, scope = scope).maybeAutoEnroll("ticket-b")!!.join()
        assertEquals(1, redeemCalls)
        assertEquals(EnrollmentGate.ticketHash("ticket-b"), storage.attemptedTicketHash)
    }
}
