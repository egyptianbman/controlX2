package com.jwoglom.controlx2.util

import android.content.Context
import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorItem
import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogDao
import com.jwoglom.controlx2.db.historylog.HistoryLogSeqRange
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Coordinates background sampling of the anchor table so the UI can convert sequence IDs to dates.
 *
 * Preloads during app startup (leveraging the long startup/history-fetch window) and on demand when
 * the history dialog opens. Sampling is cheap (~one indexed query per [SampleSpacingIds] IDs) but the
 * DB only has data once history has fetched, so a sample only fires when it is the first run or the
 * range has grown enough to be worth re-sampling. At most one sample runs at a time.
 */
class HistoryLogAnchorManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastHead: Long? = null
    // Oldest ID covered by the last sample. Re-sampling is triggered when the DB grows older
    // (an older ID appears) so dates for newly-fetched older records are never left unsampled.
    private var lastOldest: Long? = null
    private var lastDbCount: Long? = null

    val isRunning: Boolean get() = job?.isActive == true

    private fun daos(): Pair<HistoryLogDao, HistoryLogAnchorRepo> {
        val db = HistoryLogDatabase.getDatabase(context)
        return db.historyLogDao() to HistoryLogAnchorRepo(db.historyLogAnchorDao())
    }

    /**
     * Ensure anchors cover the DB's actual history span. Starts sampling if nothing is running and
     * the range is worth it; otherwise returns a deferred that completes when the current sample
     * finishes. Idempotent within a run, so callers (startup hook + dialog) can both invoke it safely.
     *
     * The span is read from the DB ([HistoryLogDao.getSeqRange]), never from the caller's status
     * response: a stale [firstSequenceNum] would otherwise leave older fetched IDs without anchors,
     * collapsing their dates to the first anchor's date.
     */
    fun ensureLoaded(pumpSid: Int): CompletableDeferred<Unit> {
        if (isRunning) return doneOf(job!!)
        return launchCheck(pumpSid)
    }

    private fun launchCheck(pumpSid: Int): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        job = scope.launch {
            try {
                val (dao, repo) = daos()
                val range = dao.getSeqRange(pumpSid).first()
                val oldest = range?.minSeqId
                val head = range?.maxSeqId
                val count = dao.getCount(pumpSid).first() ?: 0
                if (shouldSample(lastOldest, lastHead, lastDbCount ?: 0, oldest, head ?: 0, count)) {
                    if (oldest != null && head != null) {
                        HistoryLogAnchorSampler(repo, dao, pumpSid).sample(oldest, head)
                        lastOldest = oldest
                        lastHead = head
                        lastDbCount = count
                        Timber.i(
                            "HistoryLogAnchorManager: sampled anchors oldest=$oldest head=$head records=$count"
                        )
                    }
                } else {
                    Timber.d(
                        "HistoryLogAnchorManager: skipping sample (oldest=$oldest head=$head records=$count)"
                    )
                }
            } catch (t: Throwable) {
                Timber.w(t, "HistoryLogAnchorManager: anchor sample failed")
            } finally {
                deferred.complete(Unit)
            }
        }
        return deferred
    }

    private fun doneOf(j: Job): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().apply { j.invokeOnCompletion { complete(Unit) } }

    /** Current anchors for [pumpSid], for building a [HistoryLogAnchorLookup]. Call off the main thread. */
    fun anchors(pumpSid: Int): List<HistoryLogAnchorItem> = daos().second.getAll(pumpSid)

    companion object {
        // Re-sample once the DB has grown this many records since the last sample (history fetching).
        private const val DbCountGrowthThreshold = 5_000L

        /**
         * Whether a sample is warranted given the previous sample's oldest/head/DB-count and the
         * current state. Pure and stateless, so the dedup policy is unit-testable without a DB.
         *
         * Re-samples when the DB grows older (an older ID appears) in addition to head/count growth,
         * so newly-fetched older records always get date anchors.
         */
        fun shouldSample(
            prevOldest: Long?,
            prevHead: Long?,
            prevCount: Long,
            curOldest: Long?,
            curHead: Long,
            count: Long,
        ): Boolean {
            // First run (no prior sample), or the DB lost its oldest data (deletion) -> sample.
            if (prevOldest == null || prevHead == null) return true
            // No data to sample yet.
            if (count <= 0) return false
            // New older data appeared (oldest moved earlier) -> re-sample to cover it.
            val oldestGrew = curOldest != null && curOldest < prevOldest
            val headGrew = curHead - prevHead >= SampleSpacingIds
            val countGrew = count - prevCount >= DbCountGrowthThreshold
            return oldestGrew || headGrew || countGrew
        }
    }
}
