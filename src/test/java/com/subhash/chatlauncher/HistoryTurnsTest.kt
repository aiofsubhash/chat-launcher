package com.subhash.chatlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Progressive-disclosure grouping for Settings > History (owner Nov 7 00:31). */
class HistoryTurnsTest {

    private fun user(ts: Long, text: String) = HistoryEntry(ts, "user", "local", text)
    private fun ai(ts: Long, text: String, cloud: Boolean = true) = HistoryEntry(ts, "assistant", if (cloud) "cloud" else "local", text)

    @Test
    fun userLineStartsATurnAndRepliesAttach() {
        // newest first, as HistoryStore.all() returns
        val entries = listOf(ai(30, "answer"), user(20, "question"), ai(10, "older answer"), user(5, "older question"))
        val turns = groupHistoryTurns(entries)
        assertEquals(2, turns.size)
        assertEquals(20L, turns[0].id) // newest turn first
        assertEquals("question", turns[0].query?.text)
        assertEquals(listOf("answer"), turns[0].responses.map { it.text })
        assertEquals(5L, turns[1].id)
        assertEquals(listOf("older answer"), turns[1].responses.map { it.text })
    }

    @Test
    fun assistantOnlyRunBecomesQuerylessTurn() {
        val turns = groupHistoryTurns(listOf(ai(10, "Opened Maps", cloud = false)))
        assertEquals(1, turns.size)
        assertNull(turns[0].query)
        assertEquals("Opened Maps", turns[0].responses[0].text)
    }

    @Test
    fun consecutiveUserLinesAreSeparateTurns() {
        val turns = groupHistoryTurns(listOf(user(20, "second"), user(10, "first")))
        assertEquals(2, turns.size)
        assertEquals("second", turns[0].query?.text)
        assertEquals(emptyList<String>(), turns[0].responses.map { it.text })
    }

    @Test
    fun emptyHistoryGivesNoTurns() {
        assertEquals(0, groupHistoryTurns(emptyList()).size)
    }
}
