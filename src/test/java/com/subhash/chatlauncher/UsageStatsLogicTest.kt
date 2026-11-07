package com.subhash.chatlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic checks for today's per-app usage: merge, filter, order, formatting. */
class UsageStatsLogicTest {

    private val labels = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.instagram.android" to "Instagram",
        "com.android.chrome" to "Chrome",
    )

    @Test
    fun mergeSumsDuplicatePackageBuckets() {
        val raw = listOf(
            UsageStatsLogic.RawUsage("com.whatsapp", 30 * 60_000L, 1000L),
            UsageStatsLogic.RawUsage("com.whatsapp", 15 * 60_000L, 2000L),
        )
        val out = UsageStatsLogic.merge(raw, labels)
        assertEquals(1, out.size)
        assertEquals(45 * 60_000L, out[0].foregroundMillis)
        assertEquals(2000L, out[0].lastUsedMillis)
        assertEquals("WhatsApp", out[0].label)
    }

    @Test
    fun mergeDropsPackagesWithoutALaunchableLabel() {
        val raw = listOf(
            UsageStatsLogic.RawUsage("com.android.systemui", 500 * 60_000L, 3000L),
            UsageStatsLogic.RawUsage("com.android.chrome", 10 * 60_000L, 1000L),
        )
        val out = UsageStatsLogic.merge(raw, labels)
        assertEquals(listOf("com.android.chrome"), out.map { it.packageName })
    }

    @Test
    fun mergeIncludesShortVisits() {
        val raw = listOf(
            UsageStatsLogic.RawUsage("com.whatsapp", 59_000L, 1000L),
            UsageStatsLogic.RawUsage("com.instagram.android", 60_000L, 1000L),
        )
        val out = UsageStatsLogic.merge(raw, labels)
        assertEquals(listOf("com.instagram.android", "com.whatsapp"), out.map { it.packageName })
    }

    @Test
    fun mergeSortsByForegroundTimeDescending() {
        val raw = listOf(
            UsageStatsLogic.RawUsage("com.android.chrome", 5 * 60_000L, 1000L),
            UsageStatsLogic.RawUsage("com.whatsapp", 90 * 60_000L, 2000L),
            UsageStatsLogic.RawUsage("com.instagram.android", 40 * 60_000L, 1500L),
        )
        val out = UsageStatsLogic.merge(raw, labels)
        assertEquals(
            listOf("com.whatsapp", "com.instagram.android", "com.android.chrome"),
            out.map { it.packageName },
        )
    }

    @Test
    fun mergeEmptyInputStaysEmpty() {
        assertTrue(UsageStatsLogic.merge(emptyList(), labels).isEmpty())
    }

    @Test
    fun formatDurationShapes() {
        assertEquals("<1m", UsageStatsLogic.formatDuration(30_000L))
        assertEquals("1m", UsageStatsLogic.formatDuration(60_000L))
        assertEquals("43m", UsageStatsLogic.formatDuration(43 * 60_000L))
        assertEquals("2h", UsageStatsLogic.formatDuration(120 * 60_000L))
        assertEquals("2h 5m", UsageStatsLogic.formatDuration(125 * 60_000L))
    }
    @Test fun eventsClipOvernightSessionsAndKeepShortVisits() {
        val out = UsageStatsLogic.fromEvents(listOf(
            UsageStatsLogic.ActivityEvent("a", "1", 900, 1),
            UsageStatsLogic.ActivityEvent("a", "1", 1100, 2),
            UsageStatsLogic.ActivityEvent("a", "2", 1200, 1),
            UsageStatsLogic.ActivityEvent("a", "2", 1250, 2),
        ), 1000, 2000)
        assertEquals(150L, out.single().foregroundMillis)
    }
    @Test fun eventsDoNotDoubleCountMultiActivityOrRepeatedResume() {
        val out = UsageStatsLogic.fromEvents(listOf(
            UsageStatsLogic.ActivityEvent("a", "1", 1100, 1),
            UsageStatsLogic.ActivityEvent("a", "1", 1150, 1),
            UsageStatsLogic.ActivityEvent("a", "2", 1200, 1),
            UsageStatsLogic.ActivityEvent("a", "1", 1300, 2),
            UsageStatsLogic.ActivityEvent("a", "2", 1400, 2),
        ), 1000, 2000)
        assertEquals(300L, out.single().foregroundMillis)
    }
    @Test fun eventsStopOnScreenOffAndClipStillOpenAtNow() {
        val out = UsageStatsLogic.fromEvents(listOf(
            UsageStatsLogic.ActivityEvent("a", "1", 1100, 1),
            UsageStatsLogic.ActivityEvent("", "", 1300, 16),
            UsageStatsLogic.ActivityEvent("b", "1", 1800, 1),
        ), 1000, 2000).associateBy { it.packageName }
        assertEquals(200L, out["a"]!!.foregroundMillis)
        assertEquals(200L, out["b"]!!.foregroundMillis)
    }

}
