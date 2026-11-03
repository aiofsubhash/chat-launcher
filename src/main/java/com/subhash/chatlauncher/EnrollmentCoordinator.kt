package com.subhash.chatlauncher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrates the single automatic enrollment attempt per launch (owner 14:43: no manual
 * pairing step). Decisions live in EnrollmentGate; this class owns the lifecycle:
 * - an activity recreate while a redeem is still running must never start a parallel
 *   redemption of the same ticket (single-use server-side);
 * - a credential saved meanwhile (manual pairing) always wins: a late Connected result
 *   must never overwrite it, though the ticket attempt is still consumed;
 * - transient failures are not consumed and never retried within the same launch.
 * Everything is injected, so the full lifecycle is plain-JUnit testable with no network.
 */
class EnrollmentCoordinator(
    private val storage: AttemptStorage,
    private val hasCredential: () -> Boolean,
    private val saveCredential: (credential: String, endpoint: String) -> Unit,
    private val redeem: suspend (String) -> LlmClient.RedeemResult,
    private val announce: (String) -> Unit,
    private val scope: CoroutineScope,
) {
    interface AttemptStorage {
        var attemptedTicketHash: String?
    }

    /** Returns the attempt job (tests join it), or null when no attempt is due. */
    fun maybeAutoEnroll(ticket: String): Job? {
        if (!EnrollmentGate.shouldAttempt(hasCredential(), ticket, storage.attemptedTicketHash)) return null
        val hash = EnrollmentGate.ticketHash(ticket)
        if (!inFlight.add(hash)) return null // recreate during a live redeem: never a parallel redemption
        announce(MSG_SETTING_UP)
        return scope.launch {
            val result = try {
                redeem(ticket)
            } finally {
                inFlight.remove(hash)
            }
            when {
                result is LlmClient.RedeemResult.Connected -> {
                    storage.attemptedTicketHash = hash
                    if (hasCredential()) {
                        // Manual pairing landed first; its credential stays, the ticket is spent.
                        announce(MSG_ALREADY_CONNECTED)
                    } else {
                        saveCredential(result.credential, result.endpoint)
                        announce(MSG_CONNECTED)
                    }
                }
                EnrollmentGate.consumesAttempt(result) -> {
                    storage.attemptedTicketHash = hash
                    announce(MSG_NEEDS_RENEWAL)
                }
                else -> announce(MSG_RETRY_NEXT_LAUNCH) // transient: next launch retries, no loop
            }
        }
    }

    companion object {
        private val inFlight = ConcurrentHashMap.newKeySet<String>()

        const val MSG_SETTING_UP = "Setting up AI automatically..."
        const val MSG_CONNECTED = "AI connected automatically - this installation is set up."
        const val MSG_ALREADY_CONNECTED = "AI connected - this installation is set up."
        const val MSG_NEEDS_RENEWAL = "Automatic AI setup needs renewal - this build's enrollment has expired. A renewed build connects it automatically, or use the pairing link in Settings."
        const val MSG_RETRY_NEXT_LAUNCH = "Couldn't reach AI setup - it will try again next time you open the app."
    }
}
