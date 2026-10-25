package com.jwoglom.controlx2.util

import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorItem
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToLong

/**
 * Converts a history-log sequence ID to a wall-clock date by interpolating between sampled anchors.
 *
 * The sampler records (seqId -> pump clock) reference points every ~10,000 IDs across the fetched
 * history. For an arbitrary ID we linearly interpolate the pump clock between the two surrounding
 * anchors and convert it to a date, so the range slider can show human-readable dates without a DB
 * query per ID (and without requiring the exact ID to be fetched). Pure and stateless, so it is
 * trivially testable and safe to call from the UI thread.
 */
class HistoryLogAnchorLookup(private val anchors: List<HistoryLogAnchorItem>) {

    private val sorted: List<HistoryLogAnchorItem> = anchors.sortedBy { it.anchorId }

    fun dateForId(id: Long): LocalDateTime? {
        if (sorted.isEmpty()) return null
        if (sorted.size == 1) return pumpTimeSecToDate(sorted.first().pumpTimeSec.toDouble())

        val lower = sorted.lastOrNull { it.anchorId <= id }
        val upper = sorted.firstOrNull { it.anchorId >= id }

        return when {
            // Between two distinct anchors: interpolate the pump clock linearly.
            lower != null && upper != null && lower !== upper -> {
                val span = (upper.anchorId - lower.anchorId).toDouble()
                val frac = (id - lower.anchorId).toDouble() / span
                pumpTimeSecToDate(lower.pumpTimeSec.toDouble() + (upper.pumpTimeSec - lower.pumpTimeSec) * frac)
            }
            // Below all anchors: use the earliest anchor.
            upper != null && lower == null -> pumpTimeSecToDate(upper.pumpTimeSec.toDouble())
            // At/above all anchors: use the latest anchor.
            else -> pumpTimeSecToDate(lower!!.pumpTimeSec.toDouble())
        }
    }

    /**
     * Inverse of [dateForId]: the seqId whose anchored date is closest to [date], found by inverting
     * the linear anchor interpolation. Returns [fallback] when anchors are missing or [date]
     * predates all of them, so a caller can degrade to an earlier bound. Assumes the pump clock
     * increases with anchorId (the sampler records anchors chronologically).
     */
    fun idForDate(date: LocalDateTime, fallback: Long): Long {
        if (sorted.isEmpty()) return fallback
        val targetSec = dateToPumpTimeSec(date)
        val exact = sorted.firstOrNull { it.pumpTimeSec.toDouble() == targetSec }
        if (exact != null) return exact.anchorId
        val lower = sorted.lastOrNull { it.pumpTimeSec < targetSec }
        val upper = sorted.firstOrNull { it.pumpTimeSec > targetSec }
        return when {
            // Bracketed by two anchors: invert the interpolation for a sub-anchor-precision id.
            lower != null && upper != null -> {
                val tSpan = upper.pumpTimeSec - lower.pumpTimeSec.toDouble()
                val idSpan = (upper.anchorId - lower.anchorId).toDouble()
                if (tSpan == 0.0) return lower.anchorId
                val frac = (targetSec - lower.pumpTimeSec) / tSpan
                (lower.anchorId + frac * idSpan).roundToLong()
            }
            // [date] is at/after the newest anchor -> nearest is the head anchor.
            upper == null -> sorted.last().anchorId
            // [date] predates every anchor -> hand back the caller's earlier-bound fallback.
            else -> fallback
        }
    }

    /**
     * Default start seqId for a range ending at [head] covering the most recent [hours] hours.
     *
     * seqIds are event-driven (not time-uniform), so the start is the seqId whose anchored date is
     * ~[hours] before [head]'s date, found by inverting [idForDate]. Returns [fallback] when anchors
     * are missing or the target predates all of them, so the window degrades to the full
     * [fallback..head] span.
     */
    fun recentStart(head: Long, fallback: Long, hours: Int = 24): Long {
        val headDate = dateForId(head) ?: return fallback
        return idForDate(headDate.minusHours(hours.toLong()), fallback = fallback)
    }

    private fun pumpTimeSecToDate(pumpTimeSec: Double): LocalDateTime =
        LocalDateTime.ofEpochSecond(
            (pumpTimeSec + Dates.JANUARY_1_2008_UNIX_EPOCH).roundToLong(), 0, ZoneOffset.UTC
        )

    private fun dateToPumpTimeSec(date: LocalDateTime): Double =
        (date.toEpochSecond(ZoneOffset.UTC) - Dates.JANUARY_1_2008_UNIX_EPOCH).toDouble()
 }
