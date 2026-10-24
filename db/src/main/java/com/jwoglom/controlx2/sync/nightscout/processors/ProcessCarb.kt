package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEnteredHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process carb entries for Nightscout upload
 *
 * Converts CarbEnteredHistoryLog records to Nightscout "Carb Correction" treatments.
 * Per Nightscout spec, "Carb Correction" is the standard eventType for carb-only entries.
 */
class ProcessCarb(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CARB

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CarbEnteredHistoryLog::class.java]
        )
    }

    /**
     * Keep only the last carb entry before each bolus boundary.
     *
     * The Tandem pump logs a fresh CarbEnteredHistoryLog every time the carbs field is confirmed,
     * so editing the carbs before approving a bolus produces a tight cluster of entries (one per
     * edit). Only the final value is real; the earlier ones are intermediate edits that were never
     * delivered. [bolusBoundarySeqIds] is the set of seqIds marking a bolus boundary (a
     * BolusRequested, typeId 64); we keep the last carb entry before each boundary. Carbs with no
     * following boundary (a carb-only entry) are kept unchanged so they are never dropped.
     */
    override fun dedupeComboBolus(
        logs: List<HistoryLogItem>,
        maxProcessedBolusId: Int,
        bolusBoundarySeqIds: Set<Long>
    ): Pair<List<HistoryLogItem>, Int> {
        if (bolusBoundarySeqIds.isEmpty()) {
            return logs to maxProcessedBolusId
        }
        val boundaries = bolusBoundarySeqIds.sorted()
        val kept = mutableListOf<HistoryLogItem>()
        var carbIndex = 0
        for (boundary in boundaries) {
            // Advance to the last carb entry strictly before this boundary.
            var lastBefore = -1
            while (carbIndex < logs.size && logs[carbIndex].seqId < boundary) {
                lastBefore = carbIndex
                carbIndex++
            }
            if (lastBefore >= 0) {
                kept.add(logs[lastBefore])
            }
        }
        // Trailing carbs after the last boundary: keep unchanged (no following bolus to approve).
        while (carbIndex < logs.size) {
            kept.add(logs[carbIndex])
            carbIndex++
        }
        return kept to maxProcessedBolusId
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        val treatments = logs.mapNotNull { item ->
            try {
                carbToNightscoutTreatment(item, config)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert carb entry seqId=${item.seqId}")
                null
            }
        }

        if (treatments.isEmpty()) {
            Timber.d("${processorName()}: No treatments to upload")
            return 0
        }

        val result = nightscoutApi.uploadTreatments(treatments)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun carbToNightscoutTreatment(item: HistoryLogItem, config: NightscoutSyncConfig): NightscoutTreatment? {
        val parsed = item.parse()

        if (parsed !is CarbEnteredHistoryLog) {
            Timber.w("Unexpected carb type: ${parsed.javaClass.simpleName}")
            return null
        }

        val carbs = parsed.carbs.toInt()
        if (carbs <= 0) {
            Timber.d("Skipping zero/negative carb entry: $carbs")
            return null
        }

        return NightscoutTreatment.fromTimestamp(
            eventType = "Carb Correction",
            timestamp = item.pumpTimeLocal(),
            seqId = item.seqId,
            carbs = carbs.toDouble(),
            notes = config.buildNotes { append("Carb entry: ${carbs}g") }
        )
    }
}
