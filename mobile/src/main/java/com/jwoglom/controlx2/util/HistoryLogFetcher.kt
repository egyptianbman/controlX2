package com.jwoglom.controlx2.util

import android.util.LruCache
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.pump.PumpHistoryLogFetcher
import com.jwoglom.controlx2.pump.PumpSession
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.Long.max
import java.lang.Long.min
import java.time.LocalDateTime
import java.time.ZoneId
/**
 * Progress of the current history-log sync scope. [target] is the number of records the
 * active fetch is trying to bring into the DB (the records still missing when the scope
 * opened); [synced] is how many of those are now populated. When [target] is 0 there is
 * nothing to sync and the UI should hide the progress bar.
 */
data class HistoryLogSyncProgress(val target: Long, val synced: Long)

const val InitialHistoryLogCount = 5000
const val FetchGroupTimeoutMs = 4000

class HistoryLogFetcher(
    private val historyLogRepo: HistoryLogRepo,
    val pumpSid: Int,
    private val commandSender: suspend (startSeqId: Long, count: Int) -> Unit,
    private val autoFetchEnabled: () -> Boolean = { true },
    private val canRequest: () -> Boolean = { true },
    private val broadcastCallback: ((HistoryLogItem) -> Unit)? = null,
    private val progressCallback: ((HistoryLogSyncProgress) -> Unit)? = null,
    private val requestDelayMs: Long = 1000,
    private val pollIntervalMs: Long = 500,
    private val fetchGroupTimeoutMs: Long = FetchGroupTimeoutMs.toLong()
) : PumpHistoryLogFetcher {
    private var recentSeqIds = LruCache<Long, Long>(256)
    private var latestSeqId: Long = 0

    private val triggerRangeMutex = Mutex()
    private val statusResponseLock = Mutex()
    private val streamResponseLock = Mutex()
    @Volatile
    private var cancelled = false

    constructor(
        historyLogRepo: HistoryLogRepo,
        pumpSid: Int,
        pumpSession: PumpSession,
        autoFetchEnabled: () -> Boolean = { true },
        broadcastCallback: ((HistoryLogItem) -> Unit)? = null,
        progressCallback: ((HistoryLogSyncProgress) -> Unit)? = null
    ) : this(
        historyLogRepo = historyLogRepo,
        pumpSid = pumpSid,
        commandSender = { start, count -> pumpSession.sendCommand(HistoryLogRequest(start, count)) },
        autoFetchEnabled = autoFetchEnabled,
        canRequest = { pumpSession.isActive },
        broadcastCallback = broadcastCallback,
        progressCallback = progressCallback
    )

    override fun cancel() {
        cancelled = true
    }

    private fun shouldContinue(): Boolean {
        return !cancelled && autoFetchEnabled() && canRequest()
    }

    private suspend fun request(start: Long, count: Int) {
        if (!shouldContinue()) {
            Timber.i("HistoryLogFetcher.request skipped start=$start count=$count")
            return
        }
        commandSender(start, count)
    }

    private suspend fun triggerRange(startLog: Long, endLog: Long) {
        triggerRangeMutex.withLock {
            Timber.i("HistoryLogFetcher.triggerRange<lock>")
            triggerRangeInternal(startLog, endLog)
            Timber.i("HistoryLogFetcher.triggerRange<unlock>")
        }
    }

    private suspend fun triggerRangeInternal(
        startLog: Long,
        endLog: Long
    )  {
        if (!shouldContinue()) {
            Timber.i("HistoryLogFetcher.triggerRange skipped $startLog - $endLog")
            return
        }
        Timber.i("HistoryLogFetcher.triggerRange($startLog, $endLog)")
        val chunkSize = 32
        var num = 1
        var totalNums = 1
        for (i in startLog..endLog step chunkSize.toLong()) {
            totalNums++
        }

        var i = endLog
        while (i >= startLog) {
            if (!shouldContinue()) {
                Timber.i("HistoryLogFetcher.triggerRange stopping early at $i")
                return
            }
            val count = min(chunkSize.toLong(), i - startLog + 1).toInt()
            val startI = i - count + 1
            Timber.i("HistoryLogFetcher.triggerRangeStart $startI - $i ($count)")

            // HistoryLogRequest returns items backward from the start ID,
            // so we request from the END of the range to get all items in [startI, i]
            request(i, count)

            delay(requestDelayMs)

            var waitTimeMs = 0L
            while (true) {
                if (!shouldContinue()) {
                    Timber.i("HistoryLogFetcher.triggerRange poll stopping early at $startI - $i")
                    return
                }
                delay(pollIntervalMs)
                waitTimeMs += pollIntervalMs
                val dbCount = historyLogRepo.getCount(pumpSid, startI, i).firstOrNull() ?: 0
                Timber.d("HistoryLogFetcher dbCount=${dbCount} / count=$count")
                if (dbCount >= count) {
                    break
                }
                if (waitTimeMs >= fetchGroupTimeoutMs) {
                    Timber.i("HistoryLogFetcher subset $startI - $i timed out: latest=$latestSeqId waitTime=$waitTimeMs")
                    break
                }
            }
            Timber.i("HistoryLogFetcher.triggerRangeDone $startI - $i: latest=$latestSeqId")
            num++
            i = startI - 1
        }
    }
    override suspend fun onStatusResponse(message: HistoryLogStatusResponse, scope: CoroutineScope) {
        if (!shouldContinue()) return
        statusResponseLock.withLock {
            Timber.i("HistoryLogFetcher.onStatusResponse<lock>")
            onStatusResponseInternal(message, scope)
            Timber.i("HistoryLogFetcher.onStatusResponse<unlock>")
        }
    }
    /**
     * Publish progress for the current sync scope. [target] is the number of records the
     * active fetch is trying to bring into the DB (the records still missing when the scope
     * opened); [baseline] is how many of the window were already populated when it opened.
     * [synced] is how many of the target are now populated (dbInWindow - baseline). A target
     * of 0 means nothing to sync, so the UI should hide the progress bar.
     */
    private suspend fun publishSyncProgress(
        target: Long,
        baseline: Long,
        winStart: Long,
        winEnd: Long,
    ) {
        if (target <= 0) {
            progressCallback?.invoke(HistoryLogSyncProgress(0, 0))
            return
        }
        val dbInWindow = try {
            historyLogRepo.getCount(pumpSid, winStart, winEnd).firstOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
        val synced = (dbInWindow - baseline).coerceIn(0, target)
        progressCallback?.invoke(HistoryLogSyncProgress(target, synced))
    }

    private suspend fun onStatusResponseInternal(message: HistoryLogStatusResponse, scope: CoroutineScope) {
        Timber.i("HistoryLogFetcher.onStatusResponse")
        val dbLatest = historyLogRepo.getLatest(pumpSid).firstOrNull()
        val dbLatestId = dbLatest?.seqId
        val dbCount = historyLogRepo.getCount(pumpSid).firstOrNull() ?: 0
        
        val catchupThreshold = message.lastSequenceNum - InitialHistoryLogCount
        var startId = when {
            dbLatestId != null && dbLatestId >= catchupThreshold && dbLatestId <= message.lastSequenceNum -> {
                // Use the catchup threshold if we have far fewer rows than expected,
                // e.g. when a previous fetch was interrupted mid-way. The DB has the
                // latest seq but is missing many older entries within the window.
                val expectedCount = dbLatestId - catchupThreshold
                if (expectedCount > 0 && dbCount < expectedCount / 2) {
                    catchupThreshold
                } else {
                    dbLatestId
                }
            }
            else -> catchupThreshold
        }

        // don't try and fetch earlier than the first available seq number on the pump
        // (this is not always 0; after a pump has been used for a long period old
        // data is retentioned away and deleted while keeping id numbers consistent)
        startId = max(startId, message.firstSequenceNum)
        // ...or the latest sequence num
        startId = min(startId, message.lastSequenceNum)

        // should not be possible
        if (startId < 0) {
            startId = 0
        }

        val diffCount = message.lastSequenceNum - startId

        Timber.i("HistoryLogFetcher.onStatusResponse: db: $dbLatestId pump: ${message.lastSequenceNum} (diff: $diffCount)")

        val allIds = historyLogRepo.getAllIds(pumpSid, startId, message.lastSequenceNum)
        val missingIds = getMissingIds(allIds, startId, message.lastSequenceNum)
        var missingIdsTotal: Long = 0
        missingIds.forEach {
            missingIdsTotal += (it.last - it.first + 1)
        }

        

        // sync scope for the progress bar: target is the count still missing when this scope
        // opened, baseline is how many of the window were already populated then, so synced
        // (= dbInWindow - baseline) tracks only the current fetch, not the cumulative total.
        val syncTarget = missingIdsTotal
        val syncBaseline = (message.lastSequenceNum - startId + 1) - syncTarget
        Timber.i("HistoryLogFetcher.onStatusResponse: missingIds=${missingIdsTotal} $missingIds")

        scope.launch {
            if (!shouldContinue()) {
                Timber.i("HistoryLogFetcher.onStatusResponse: cancelled before launch")
                return@launch
            }
            publishSyncProgress(syncTarget, syncBaseline, startId, message.lastSequenceNum)
            Timber.i("HistoryLogFetcher.onStatusResponse: launching ranges: $missingIds (dbCount=$dbCount)")
            missingIds.reversed().forEach {
                if (!shouldContinue()) {
                    Timber.i("HistoryLogFetcher.onStatusResponse: cancelled during launch")
                    return@launch
                }
                triggerRange(it.first, it.last)
                publishSyncProgress(syncTarget, syncBaseline, startId, message.lastSequenceNum)
            }
            publishSyncProgress(syncTarget, syncBaseline, startId, message.lastSequenceNum)
            Timber.i("HistoryLogFetcher.onStatusResponse: completed, fetched $missingIdsTotal")
            val finalDbCount = historyLogRepo.getCount(pumpSid).firstOrNull() ?: 0
            Timber.i("HistoryLogFetcher.onStatusResponse: completed, ${finalDbCount - dbCount} actual: $dbCount -> $finalDbCount")
        }
    }

    override suspend fun onStreamResponse(log: HistoryLog) {
        streamResponseLock.withLock {
            if (recentSeqIds.get(log.sequenceNum) != null) {
                Timber.d("HistoryLogFetcher onStreamResponse skip duplicate ${log.sequenceNum}")
                return@withLock
            }
            Timber.d("HistoryLogFetcher onStreamResponse ${log.sequenceNum}")
            val item = HistoryLogItem(
                seqId = log.sequenceNum,
                pumpSid = pumpSid,
                typeId = log.typeId(),
                cargo = log.cargo,
                pumpTimeSec = log.pumpTimeSec,
                pumpTime = LocalDateTime.ofInstant(log.pumpTimeSecInstant, ZoneId.systemDefault()),
                addedTime = LocalDateTime.now()
            )
            historyLogRepo.insert(item)
            latestSeqId = max(latestSeqId, log.sequenceNum)
            recentSeqIds.put(log.sequenceNum, log.sequenceNum)
            broadcastCallback?.invoke(item)
        }
    }
}

fun getMissingIds(present: List<Long>, min: Long, max: Long): List<LongRange> {
    if (present.isEmpty()) {
        return listOf(min..max)
    }
    val missing = mutableListOf<LongRange>()
    if (present[0] > min) {
        missing.add(min until present[0])
    }
    var prev: Long = present[0]
    for ((k, i) in present.withIndex()) {
        if (k == 0) continue
        if (i > prev + 1) {
            missing.add(prev+1..i-1)
        }
        prev = i
    }
    if (prev < max) {
        missing.add(prev+1..max)
    }
    return missing
}
