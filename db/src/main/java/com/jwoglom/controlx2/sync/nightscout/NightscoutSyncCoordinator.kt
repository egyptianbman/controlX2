package com.jwoglom.controlx2.sync.nightscout

import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.nightscout.NightscoutProcessorState
import com.jwoglom.controlx2.db.nightscout.NightscoutProcessorStateDao
import com.jwoglom.controlx2.db.nightscout.NightscoutSyncStateDao
import com.jwoglom.controlx2.db.nightscout.NightscoutSyncState
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutProfile
import com.jwoglom.controlx2.sync.nightscout.processors.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Main coordinator for Nightscout sync operations
 *
 * Orchestrates sequential processing of pump data through specialized processors,
 * following the tconnectsync architecture pattern.
 */
class NightscoutSyncCoordinator(
    private val historyLogRepo: HistoryLogRepo,
    private val nightscoutApi: NightscoutApi,
    private val syncStateDao: NightscoutSyncStateDao,
    private val processorStateDao: NightscoutProcessorStateDao,
    private val config: NightscoutSyncConfig,
    private val pumpSid: Int
) {
    // Map processor types to instances
    private val processorMap: Map<ProcessorType, BaseProcessor> = mapOf(
        ProcessorType.CGM_READING to ProcessCGMReading(nightscoutApi, historyLogRepo),
        ProcessorType.BOLUS to ProcessBolus(nightscoutApi, historyLogRepo, processorStateDao),
        ProcessorType.BASAL to ProcessBasal(nightscoutApi, historyLogRepo),
        ProcessorType.BASAL_SUSPENSION to ProcessBasalSuspension(nightscoutApi, historyLogRepo),
        ProcessorType.BASAL_RESUME to ProcessBasalResume(nightscoutApi, historyLogRepo),
        ProcessorType.ALARM to ProcessAlarm(nightscoutApi, historyLogRepo),
        ProcessorType.CGM_ALERT to ProcessCGMAlert(nightscoutApi, historyLogRepo),
        ProcessorType.USER_MODE to ProcessUserMode(nightscoutApi, historyLogRepo),
        ProcessorType.CARTRIDGE to ProcessCartridge(nightscoutApi, historyLogRepo),
        ProcessorType.CARB to ProcessCarb(nightscoutApi, historyLogRepo),
        ProcessorType.DEVICE_STATUS to ProcessDeviceStatus(nightscoutApi, historyLogRepo)
    )

    // Processors in processing order (following tconnectsync)
    private val processorOrder = listOf(
        ProcessorType.CGM_READING,
        ProcessorType.BOLUS,
        ProcessorType.BASAL,
        ProcessorType.BASAL_SUSPENSION,
        ProcessorType.BASAL_RESUME,
        ProcessorType.ALARM,
        ProcessorType.CGM_ALERT,
        ProcessorType.USER_MODE,
        ProcessorType.CARTRIDGE,
        ProcessorType.CARB,
        ProcessorType.DEVICE_STATUS
    )

    /**
     * Sync all enabled processors
     */
    suspend fun syncAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): SyncResult {
        // Reset one-shot timestamp sample logging for this sync run.
        NightscoutTimestampPolicy.resetSampleLoggingForSyncRun()
        if (!config.enabled) {
            Timber.d("Nightscout sync is disabled")
            return SyncResult.Disabled
        }

        if (!config.isValid()) {
            Timber.e("Nightscout configuration is invalid")
            return SyncResult.InvalidConfig
        }

        var syncState = syncStateDao.getState()

        // First-time setup
        if (syncState == null) {
            syncState = NightscoutSyncState(
                firstEnabledTime = LocalDateTime.now(),
                lookbackHours = config.initialLookbackHours
            )
            syncStateDao.upsert(syncState)
            Timber.i("Initialized Nightscout sync state with ${config.initialLookbackHours}h lookback")
        }

        // Determine the sequence ID range to process
        val (startSeqId, endSeqId) = determineSeqIdRange(syncState)

        if (startSeqId >= endSeqId) {
            Timber.d("No new data to sync (startSeqId=$startSeqId, endSeqId=$endSeqId)")
            return SyncResult.NoData
        }

        // Fetch all history logs in range
        val allLogs = historyLogRepo.getRange(pumpSid, startSeqId, endSeqId).first()

        if (allLogs.isEmpty()) {
            Timber.d("No history logs found in range $startSeqId..$endSeqId")
            return SyncResult.NoData
        }

        // Progress counts logs a processor can actually upload, not every log in range: many
        // logs (CGM state, Control-IQ cycles, IDP lists...) carry typeIds no supported
        // processor recognizes and so are never uploaded. Use the syncable count as the bar
        // denominator so it can reach 100% instead of capping at (syncable / total).
        val syncableTypeIds = processorOrder
            .mapNotNull { processorMap[it] }
            .filter { it.isEnabled(config) }
            .flatMap { it.supportedTypeIds() }
            .toSet()
        val syncableCount = allLogs.count { it.typeId in syncableTypeIds }
        // Retroactive re-send: the selected range is a re-upload, not a resume. The per-processor
        // cursors are already past this range, so reset every processor cursor (and the global
        // fallback) below the range start to force re-processing and re-uploading of the range.
        if (syncState.retroactiveStartTime != null && syncState.retroactiveEndTime != null) {
            resetCursorsForRetroactive(startSeqId)
        }

        Timber.i("Syncing ${allLogs.size} history logs (seqId $startSeqId..$endSeqId)")

        var totalUploaded = 0
        // Logs consumed so far, driving the log-based progress bar (done/total).
        var processedLogs = 0
        val latestSeqId = allLogs.maxOf { it.seqId }
        // Progress is in logs, not processors: total is the syncable count. done grows as each
        // chunk is uploaded, driven by the onChunkProgress hook below -- so the bar advances in
        // ~chunk-size steps instead of jumping a whole processor's worth at once. Cleared after.
        nightscoutApi.onChunkProgress = { items ->
            processedLogs = (processedLogs + items).coerceAtMost(syncableCount)
            onProgress?.invoke(processedLogs, syncableCount)
        }

        onProgress?.invoke(0, syncableCount)

        // SEQUENTIAL processing (not parallel!) with per-processor cursors
        for (processorType in processorOrder) {
            val processor = processorMap[processorType] ?: continue

            if (!processor.isEnabled(config)) {
                Timber.d("Processor ${processorType.name} is disabled, skipping")
                continue
            }

            // Get this processor's individual cursor, falling back to global cursor
            val processorState = processorStateDao.getByType(processorType.name)
            val processorLastSeqId = processorState?.lastProcessedSeqId
                ?: syncState.lastProcessedSeqId
            val currentMaxBolusId = processorState?.maxProcessedBolusId ?: 0

            // Filter logs for this processor's supported types AND above its cursor
            val candidateLogs = allLogs.filter { log ->
                processor.supportedTypeIds().contains(log.typeId) &&
                    log.seqId > processorLastSeqId
            }.sortedBy { it.seqId }

            // Dedup combo/dual bolus delivery updates: keep only the first (start-time) entry per
            // pump bolusID; nocturne collapses the rest via the stable sync_identifier.
            val (relevantLogs, newMaxBolusId) = processor.dedupeComboBolus(candidateLogs, currentMaxBolusId)
            // Progress counts the pre-dedup candidate set, not items uploaded: DEVICE_STATUS
            // aggregates 1,107 logs into 1 item, and combo-bolus dedup collapses several logs into
            // one treatment (still synced via sync_identifier). Using candidateLogs.size (before
            // dedup) keeps both from stalling the bar. Capture the baseline, then clamp after.
            val processorStartLogs = processedLogs

            if (relevantLogs.isEmpty()) {
                Timber.d("No logs for processor ${processorType.name}")
                // Still advance cursor to latest so it doesn't re-scan
                processorStateDao.upsert(
                    NightscoutProcessorState(
                        processorType = processorType.name,
                        lastProcessedSeqId = latestSeqId,
                        lastSuccessTime = LocalDateTime.now(),
                        maxProcessedBolusId = newMaxBolusId,
                    )
                )
                continue
            }

            try {
                val uploadedCount = processor.process(relevantLogs, config)
                totalUploaded += uploadedCount

                // Success: advance this processor's cursor
                processorStateDao.upsert(
                    NightscoutProcessorState(
                        processorType = processorType.name,
                        lastProcessedSeqId = latestSeqId,
                        lastSuccessTime = LocalDateTime.now(),
                        maxProcessedBolusId = newMaxBolusId,
                    )
                )

                // Progress counts *logs consumed*, not items uploaded: a processor like DEVICE_STATUS
                // aggregates 1,107 logs into a single item, so a per-item hook would stall the bar.
                // Clamp up to the log target here; never regress below the per-chunk hook's value.
                processedLogs = processedLogs
                    .coerceAtLeast(processorStartLogs + candidateLogs.size)
                    .coerceAtMost(syncableCount)
                onProgress?.invoke(processedLogs, syncableCount)
                Timber.i("${processorType.name}: processed ${relevantLogs.size} logs, uploaded $uploadedCount items")
            } catch (e: Exception) {
                Timber.e(e, "Error processing ${processorType.name}")
                // Do NOT advance this processor's cursor -- it will retry next sync
            }
        }

        // Clear the per-chunk hook now that the run is done.
        nightscoutApi.onChunkProgress = null
        onProgress?.invoke(processedLogs, syncableCount)

        // Update global sync state (used as fallback minimum for new processors)
        syncStateDao.updateLastProcessed(latestSeqId, LocalDateTime.now())
        Timber.i("Updated sync state: lastProcessedSeqId=$latestSeqId")

        // Clear retroactive range if it was set
        if (syncState.retroactiveStartTime != null) {
            syncStateDao.setRetroactiveRange(null, null)
            Timber.i("Cleared retroactive sync range")
        }

        return SyncResult.Success(
            processedCount = allLogs.size,
            uploadedCount = totalUploaded,
            seqIdRange = startSeqId to latestSeqId
        )
    }

    /**
     * Upload a pump profile to Nightscout.
     *
     * Profiles are not history-log-based — they represent the current pump configuration.
     * Call this on pump connect or when a profile change is detected.
     */
    suspend fun uploadProfile(profile: NightscoutProfile): Boolean {
        if (!config.enabled || !config.isValid()) {
            Timber.d("Nightscout sync disabled or invalid, skipping profile upload")
            return false
        }

        return try {
            val result = nightscoutApi.uploadProfile(profile)
            if (result.isSuccess && result.getOrNull() == true) {
                Timber.i("Uploaded profile to Nightscout")
                true
            } else {
                Timber.e("Profile upload failed: ${result.exceptionOrNull()}")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error uploading profile")
            false
        }
    }

    /**
     * Perform retroactive sync for a specific time range
     */
    suspend fun syncRetroactive(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): SyncResult {
        Timber.i("Starting retroactive sync: $startTime to $endTime")
        // setRetroactiveRange is an UPDATE WHERE id=1, a no-op when no state row exists yet
        // (first sync). Ensure the row exists so the retroactive range persists; otherwise
        // syncAll() would create a fresh state without it and fall back to the lookback range.
        if (syncStateDao.getState() == null) {
            syncStateDao.upsert(
                NightscoutSyncState(
                    firstEnabledTime = LocalDateTime.now(),
                    lookbackHours = config.initialLookbackHours,
                    retroactiveStartTime = startTime,
                    retroactiveEndTime = endTime
                )
            )
        } else {
            syncStateDao.setRetroactiveRange(startTime, endTime)
        }
        return syncAll(onProgress)
    }

    /**
     * Determine the sequence ID range to process based on sync state
     */
    private suspend fun determineSeqIdRange(syncState: NightscoutSyncState): Pair<Long, Long> {
        // Case 1: Retroactive sync requested
        if (syncState.retroactiveStartTime != null && syncState.retroactiveEndTime != null) {
            return findSeqIdRangeForTimeRange(
                syncState.retroactiveStartTime!!,
                syncState.retroactiveEndTime!!
            )
        }

        // Case 2: Resume from last processed (IGNORES lookbackHours changes!)
        if (syncState.lastProcessedSeqId > 0) {
            val startSeqId = syncState.lastProcessedSeqId + 1
            val latestLog = historyLogRepo.getLatest(pumpSid).first()
            val endSeqId = latestLog?.seqId ?: Long.MAX_VALUE

            Timber.d("Resuming from lastProcessedSeqId=${syncState.lastProcessedSeqId}")
            return startSeqId to endSeqId
        }

        // Case 3: First-time sync - use lookbackHours
        val lookbackTime = LocalDateTime.now().minusHours(syncState.lookbackHours.toLong())
        val logs = historyLogRepo.getAll(pumpSid).first()
        val startSeqId = logs.firstOrNull { it.pumpTimeLocal() >= lookbackTime }?.seqId ?: 0L

        val latestLog = historyLogRepo.getLatest(pumpSid).first()
        val endSeqId = latestLog?.seqId ?: Long.MAX_VALUE

        Timber.d("First-time sync with ${syncState.lookbackHours}h lookback from $lookbackTime")
        return startSeqId to endSeqId
    }

    /**
     * Find sequence ID range for a specific time range
     */
    private suspend fun findSeqIdRangeForTimeRange(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Pair<Long, Long> {
        val logs = historyLogRepo.getAll(pumpSid).first()

        val startSeqId = logs.firstOrNull { it.pumpTimeLocal() >= startTime }?.seqId ?: 0L
        val endSeqId = logs.lastOrNull { it.pumpTimeLocal() <= endTime }?.seqId ?: Long.MAX_VALUE

        Timber.d("Retroactive sync range: seqId $startSeqId..$endSeqId for time $startTime..$endTime")
        return startSeqId to endSeqId
    }
    /**
     * Reset every per-processor cursor (and the global fallback) to below [belowSeqId] so a
     * retroactive re-send re-processes and re-uploads the selected range instead of skipping data
     * the cursors already marked done. maxProcessedBolusId is cleared so combo/dual boluses in the
     * range resend their start entry.
     */
    private suspend fun resetCursorsForRetroactive(belowSeqId: Long) {
        for (state in processorStateDao.getAll()) {
            processorStateDao.upsert(
                NightscoutProcessorState(
                    processorType = state.processorType,
                    lastProcessedSeqId = belowSeqId,
                    lastSuccessTime = null,
                    maxProcessedBolusId = 0
                )
            )
        }
        syncStateDao.updateLastProcessed(belowSeqId, LocalDateTime.now())
    }
}

/**
 * Result of a sync operation
 */
sealed class SyncResult {
    object Disabled : SyncResult()
    object InvalidConfig : SyncResult()
    object NoData : SyncResult()
    data class Success(
        val processedCount: Int,
        val uploadedCount: Int,
        val seqIdRange: Pair<Long, Long>
    ) : SyncResult()
}
