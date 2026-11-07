package com.subhash.chatlauncher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

/**
 * Per-app time today, at activity-monitor level only (owner Nov 7: "Activity monitor level
 * is enough"): which apps were open and for how long - never screen contents, messages or
 * anything typed. Read on demand from Android's own UsageStatsManager and shown on this
 * phone only; nothing is stored by us or uploaded. Android gates the data behind the
 * Usage access setting; the app cannot grant it to itself, so the Usage tab deep-links to
 * the system toggle once.
 */
data class AppUsage(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
    val lastUsedMillis: Long,
)

/** Read side is injectable so the UI and logic are testable without a real UsageStatsManager. */
interface UsageSource {
    fun hasAccess(): Boolean
    fun today(): List<AppUsage>
}

object UsageStatsLogic {
    /** One raw UsageStats row: a package's foreground total and last-used stamp over a range. */
    data class RawUsage(val packageName: String, val foregroundMillis: Long, val lastUsedMillis: Long)

    /**
     * Merge duplicate per-package rows (multiple sessions in the range), keep only
     * launchable apps we can name, include short visits, sort by time desc.
     */
    fun merge(raw: List<RawUsage>, labels: Map<String, String>): List<AppUsage> {
        val totals = mutableMapOf<String, Long>()
        val lastUsed = mutableMapOf<String, Long>()
        for (r in raw) {
            totals[r.packageName] = (totals[r.packageName] ?: 0L) + r.foregroundMillis
            lastUsed[r.packageName] = maxOf(lastUsed[r.packageName] ?: 0L, r.lastUsedMillis)
        }
        return totals.mapNotNull { (pkg, total) ->
            val label = labels[pkg] ?: return@mapNotNull null
            if (total <= 0) null
            else AppUsage(pkg, label, total, lastUsed[pkg] ?: 0L)
        }.sortedByDescending { it.foregroundMillis }
    }

    data class ActivityEvent(val packageName: String, val activityId: String, val at: Long, val type: Int)

    /** Union of resumed activities per package, clipped to the exact local-day range.
     * A lookback seeds activities already open at midnight. Screen-off/shutdown closes
     * running sessions. Multiple resumed activities in one app never double count time. */
    fun fromEvents(events: List<ActivityEvent>, begin: Long, end: Long): List<RawUsage> {
        val active = mutableMapOf<String, MutableSet<String>>()
        val started = mutableMapOf<String, Long>()
        val totals = mutableMapOf<String, Long>()
        val last = mutableMapOf<String, Long>()
        fun close(pkg: String, at: Long) {
            val start = started.remove(pkg) ?: return
            val duration = (minOf(at, end) - maxOf(start, begin)).coerceAtLeast(0L)
            totals[pkg] = (totals[pkg] ?: 0L) + duration
        }
        for (event in events.sortedBy { it.at }) {
            if (event.at > end) break
            val pkg = event.packageName
            when (event.type) {
                1 -> { // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                    val ids = active.getOrPut(pkg) { mutableSetOf() }
                    if (ids.isEmpty()) started[pkg] = event.at
                    ids.add(event.activityId)
                    last[pkg] = maxOf(last[pkg] ?: 0L, event.at)
                }
                2, 23 -> { // ACTIVITY_PAUSED / ACTIVITY_STOPPED
                    val ids = active[pkg] ?: continue
                    ids.remove(event.activityId)
                    if (ids.isEmpty()) close(pkg, event.at)
                }
                16, 26 -> { // SCREEN_NON_INTERACTIVE / DEVICE_SHUTDOWN
                    started.keys.toList().forEach { close(it, event.at) }
                    active.clear()
                }
            }
        }
        started.keys.toList().forEach { close(it, end) }
        return totals.map { (pkg, total) -> RawUsage(pkg, total, last[pkg] ?: 0L) }
    }

    /** "2h 5m", "43m", "<1m" - compact enough for a right-aligned row. */
    fun formatDuration(ms: Long): String {
        val mins = ms / 60_000
        if (mins < 1) return "<1m"
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) {
            if (m > 0) "${h}h ${m}m" else "${h}h"
        } else "${m}m"
    }
}

class AndroidUsageSource(
    private val context: Context,
    private val labels: () -> Map<String, String>,
) : UsageSource {

    override fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun today(): List<AppUsage> {
        val begin = dayStartMillis()
        val end = System.currentTimeMillis()
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: error("Android usage service isn't available.")
        val stream = usm.queryEvents(begin - 24L * 3600_000L, end)
            ?: error("Unlock your phone and try again.")
        val events = mutableListOf<UsageStatsLogic.ActivityEvent>()
        val event = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            val id = event.className.orEmpty()
            events.add(UsageStatsLogic.ActivityEvent(event.packageName.orEmpty(), id, event.timeStamp, event.eventType))
        }
        return UsageStatsLogic.merge(UsageStatsLogic.fromEvents(events, begin, end), labels())
    }

    private fun dayStartMillis(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
