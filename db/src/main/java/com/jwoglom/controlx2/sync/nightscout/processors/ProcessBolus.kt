package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.db.nightscout.NightscoutProcessorStateDao
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber

/**
 * Process bolus deliveries for Nightscout upload
 *
 * Handles standard boluses as "Bolus" events and extended/combo boluses
 * as "Combo Bolus" events per the Nightscout treatment schema.
 */
class ProcessBolus(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo,
    private val processorStateDao: NightscoutProcessorStateDao
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.BOLUS

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[BolusDeliveryHistoryLog::class.java]
        )
    }
    /**
     * Keep only the first (start-time) entry per combo/dual bolus; drop later extended-delivery
     * updates. The pump emits one history-log entry per update for a combo bolus, all sharing a
     * single pump bolusID but with distinct seqIds/timestamps. nocturne dedups on sync_identifier,
     * so a stable key (the pump bolusID) lets it collapse the updates into one treatment. Standard
     * (non-extended) boluses are not split, so they always pass through unchanged.
     */
    override fun dedupeComboBolus(
        logs: List<HistoryLogItem>,
        maxProcessedBolusId: Int
    ): Pair<List<HistoryLogItem>, Int> {
        val seen = HashSet<Int>()
        val result = mutableListOf<HistoryLogItem>()
        var newMax = maxProcessedBolusId
        for (item in logs) { // already sorted by seqId
            val bolus = item.parse() as? BolusDeliveryHistoryLog ?: continue
            val isExtended = bolus.requestedLater > 0 && bolus.extendedDurationRequested > 0
            if (!isExtended) {
                result.add(item)
                continue
            }
            val bolusID = bolus.bolusID
            if (bolusID <= maxProcessedBolusId) continue // already uploaded in an earlier sync
            if (!seen.add(bolusID)) continue // keep only the first (start-time) entry per combo bolus
            result.add(item)
            newMax = maxOf(newMax, bolusID)
        }
        return result to newMax
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
                bolusToNightscoutTreatment(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert bolus seqId=${item.seqId}")
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

    private fun bolusToNightscoutTreatment(item: HistoryLogItem): NightscoutTreatment? {
        val parsed = item.parse()

        if (parsed !is BolusDeliveryHistoryLog) {
            Timber.w("Unexpected bolus type: ${parsed.javaClass.simpleName}")
            return null
        }

        val totalInsulin = parsed.deliveredTotal / 1000.0
        val immediateInsulin = parsed.requestedNow / 1000.0
        val extendedInsulin = parsed.requestedLater / 1000.0
        val extendedDurationMinutes = parsed.extendedDurationRequested

        val isExtended = extendedInsulin > 0 && extendedDurationMinutes > 0

        return if (isExtended) {
            buildComboBolus(item, parsed, immediateInsulin, extendedInsulin, extendedDurationMinutes)
        } else {
            // A standard bolus history-log record is emitted twice: a STARTED marker (status=1,
            // deliveredTotal=0) before delivery and a COMPLETED record (status=0, deliveredTotal>0)
            // after. nocturne's native Tandem connector only ever uploads a completed bolus, so the
            // STARTED marker is spurious here — drop it. Combo/dual boluses are unaffected: they use
            // the programmed requestedNow/requestedLater amounts, not deliveredTotal.
            if (totalInsulin == 0.0) {
                return null
            }

            buildStandardBolus(item, parsed, totalInsulin)
        }
    }

    private fun buildStandardBolus(
        item: HistoryLogItem,
        bolus: BolusDeliveryHistoryLog,
        totalInsulin: Double
    ): NightscoutTreatment {
        val notes = buildBolusNotes(bolus)

        return NightscoutTreatment.fromTimestamp(
            eventType = "Bolus",
            timestamp = item.pumpTimeLocal(),
            seqId = item.seqId,
            insulin = totalInsulin,
            notes = notes
        )
    }

    private fun buildComboBolus(
        item: HistoryLogItem,
        bolus: BolusDeliveryHistoryLog,
        immediateInsulin: Double,
        extendedInsulin: Double,
        extendedDurationMinutes: Int
    ): NightscoutTreatment {
        // Per Nightscout/Nocturne spec:
        // insulin = total programmed units (immediate + extended); Nocturne renders this as the bolus dose
        // programmed = total programmed units (immediate + extended); drives Bolus.Programmed
        // type = "dual" (combo) when an immediate portion exists, else "square" (pure extended)
        // enteredinsulin = total (immediate + extended)
        // splitNow/splitExt = percentages of the total programmed dose (must sum to 100)
        // relative = extended rate in U/hr
        // duration = extended duration in minutes
        // syncIdentifier = the pump bolusID, stable across a combo bolus's delivery-update entries,
        //   so nocturne upserts them into one treatment instead of building one row per update
        val totalProgrammed = immediateInsulin + extendedInsulin
        val splitNow = if (totalProgrammed > 0) {
            ((immediateInsulin / totalProgrammed) * 100).toInt()
        } else 0
        val splitExt = 100 - splitNow

        val relativeRate = if (extendedDurationMinutes > 0) {
            extendedInsulin / extendedDurationMinutes * 60
        } else 0.0

        val notes = buildString {
            append(buildBolusNotes(bolus))
            append(", combo: ${immediateInsulin}U now + ${extendedInsulin}U over ${extendedDurationMinutes}min")
        }

        return NightscoutTreatment.fromTimestamp(
            eventType = "Combo Bolus",
            timestamp = item.pumpTimeLocal(),
            seqId = item.seqId,
            syncIdentifier = bolus.bolusID.toString(),
            insulin = totalProgrammed,
            programmed = totalProgrammed,
            type = if (immediateInsulin > 0) "dual" else "square",
            duration = extendedDurationMinutes,
            notes = notes,
            enteredInsulin = totalProgrammed,
            splitNow = splitNow,
            splitExt = splitExt,
            relative = relativeRate
        )
    }

    private fun buildBolusNotes(bolus: BolusDeliveryHistoryLog): String {
        return buildString {
            append("Bolus delivery")

            try {
                val bolusTypes = bolus.bolusTypes
                if (bolusTypes.isNotEmpty()) {
                    append(", types: ${bolusTypes.joinToString(", ") { it.name }}")
                }

                bolus.bolusSource?.let {
                    append(", source: ${it.name}")
                }

                append(", ID: ${bolus.bolusID}")

                if (bolus.requestedNow > 0) {
                    append(", requested: ${bolus.requestedNow / 1000.0}U")
                }

                if (bolus.correction > 0) {
                    append(", correction: ${bolus.correction / 1000.0}U")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error building bolus notes")
            }
        }
    }
}
