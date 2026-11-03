package com.subhash.chatlauncher

import java.security.MessageDigest

/**
 * Automatic AI enrollment (owner 14:43: "remove the manual pairing step"). A private build
 * carries a single-use enrollment ticket; first launch redeems it silently through the same
 * pinned redeem path as the manual pairing link. Decisions live here, pure and testable:
 * - a stored credential always wins (never overwrite a valid pairing);
 * - attempts are scoped to the ticket's identity, so a renewed build (new ticket) tries
 *   again while the same consumed ticket is never retried;
 * - only definitive server outcomes consume the attempt - a transient failure (offline,
 *   rate limit, proxy down) may try once more on a later launch, never in a loop.
 * The ticket itself is never logged, hashed for comparison only.
 */
object EnrollmentGate {

    fun shouldAttempt(hasCredential: Boolean, ticket: String, attemptedTicketHash: String?): Boolean =
        !hasCredential && ticket.isNotBlank() && ticketHash(ticket) != attemptedTicketHash

    /** SHA-256 hex of the ticket; the comparison identity, never the ticket. */
    fun ticketHash(ticket: String): String =
        MessageDigest.getInstance("SHA-256").digest(ticket.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Consumed/expired/malformed = this ticket is done; transient = a later launch may retry. */
    fun consumesAttempt(result: LlmClient.RedeemResult): Boolean = when (result) {
        is LlmClient.RedeemResult.Connected,
        LlmClient.RedeemResult.InvalidOrExpired,
        LlmClient.RedeemResult.InvalidShape -> true
        LlmClient.RedeemResult.RateLimited,
        LlmClient.RedeemResult.Unavailable,
        LlmClient.RedeemResult.NetworkError -> false
    }
}
