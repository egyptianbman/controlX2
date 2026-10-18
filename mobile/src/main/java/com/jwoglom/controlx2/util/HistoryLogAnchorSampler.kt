package com.jwoglom.controlx2.util

import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorItem
import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogDao
import timber.log.Timber
import java.util.TreeSet

/** Sample anchor points every 10,000 sequence IDs (~3 days of history). */
const val SampleSpacingIds = 10_000L

/**
 * Background sampler that records (seqId -> pump clock) anchor points across the fetched history so
 * the UI can convert any sequence ID to a date.
 *
 * Samples every [SampleSpacingIds] IDs from [headId] down to [oldestId], using the closest real
 * seqId at or below each sample point so retention gaps don't leave holes in coverage. Non-blocking:
 * callers launch [sample] in a background coroutine. [onToast] is invoked on the caller's thread, so
 * wire it to a main-thread Toast if you want to inform the user.
 */
class HistoryLogAnchorSampler(
    private val anchorRepo: HistoryLogAnchorRepo,
    private val historyLogDao: HistoryLogDao,
    private val pumpSid: Int,
    private val sampleSpacing: Long = SampleSpacingIds,
    private val onToast: (String) -> Unit = {},
) {
    /**
     * The sample points across [oldestId, headId], soonest IDs first, deduped, always including both
     * endpoints. Pure, so it is unit-testable without a DB.
     */
    fun anchorTargets(oldestId: Long, headId: Long): List<Long> {
        if (headId < oldestId) return emptyList()
        val targets = TreeSet<Long>()
        var target = headId
        while (target > oldestId) {
            targets.add(target)
            target -= sampleSpacing
        }
        targets.add(oldestId)
        return targets.toList()
    }

    /**
     * Sample and persist anchors across [oldestId, headId]. Returns the anchors recorded.
     */
    suspend fun sample(oldestId: Long, headId: Long): List<HistoryLogAnchorItem> {
        if (headId < oldestId) return emptyList()
        val anchors = ArrayList<HistoryLogAnchorItem>()
        for (target in anchorTargets(oldestId, headId)) {
            val point = historyLogDao.getClosestAtOrBelow(pumpSid, target)
            if (point != null) {
                anchors.add(HistoryLogAnchorItem(pumpSid, point.seqId, point.pumpTimeSec))
            } else {
                Timber.w("HistoryLogAnchorSampler: no data at-or-below target $target")
            }
        }
        if (anchors.isNotEmpty()) {
            anchorRepo.insertAll(anchors)
            onToast("Sampled ${anchors.size} date anchors")
        }
        return anchors
    }
}
