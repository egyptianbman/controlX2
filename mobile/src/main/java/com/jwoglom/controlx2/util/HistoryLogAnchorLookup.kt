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

    private fun pumpTimeSecToDate(pumpTimeSec: Double): LocalDateTime =
        LocalDateTime.ofEpochSecond(
            (pumpTimeSec + Dates.JANUARY_1_2008_UNIX_EPOCH).roundToLong(), 0, ZoneOffset.UTC
        )
}
