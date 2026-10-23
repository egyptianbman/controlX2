package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CartridgeFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog
import timber.log.Timber

/**
 * Process cartridge changes for Nightscout upload
 *
 * Converts cartridge change records to Nightscout "Insulin Change" or "Site Change" treatments
 */
class ProcessCartridge(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.CARTRIDGE

    override fun supportedTypeIds(): Set<Int> {
        return setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CartridgeFilledHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[TubingFilledHistoryLog::class.java],
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[CannulaFilledHistoryLog::class.java]
        )

    }

    /**
     * Minimum prime (units) below which an aborted tubing/cannula fill is treated as a pump
     * artifact rather than a real site change. Real fills complete (status=COMPLETED) and prime a
     * meaningful amount (tubing >= 10U); the guard below also requires an aborted status, so this
     * only ever rejects fills that aborted with a negligible prime.
     */
    private val MIN_REAL_PRIME_UNITS = 1.0f

    /** Raw pumpx2 CompletionStatus id for an aborted fill (CompletionStatus.USER_ABORTED). A
     * tubing/cannula fill that aborted with a negligible prime is a pump artifact, not a real site
     * change.
     */
    private val STATUS_USER_ABORTED = 0L

    /**
     * True when a tubing/cannula fill is a pump artifact, not a real site change. The pump emits
     * zero-prime, USER_ABORTED fills immediately after a Control-IQ mode change (e.g. toggling
     * Exercise); uploading those renders phantom "Site Change" treatments in Nocturne. A fill is
     * only an artifact when it aborted AND primed a negligible amount, so real completed fills
     * (cannula 0.3U, tubing >= 10U) and a legitimately aborted-but-primed fill both pass through.
     * Read from raw cargo because the pumpx2 completion-status/prime getters on these classes do
     * not resolve under Kotlin (a sibling getter returns an un-importable nested enum).
     */
    private fun isSpuriousFill(cargo: ByteArray): Boolean {
        if (cargo.size < 18) return false
        return fillCompletionStatus(cargo) == STATUS_USER_ABORTED &&
            fillPrimeSize(cargo) < MIN_REAL_PRIME_UNITS
    }

    /** Reads the little-endian uint32 completion-status id from a fill cargo at bytes [14..18). */
    private fun fillCompletionStatus(cargo: ByteArray): Long {
        // Each shift must bind before the bitwise-or. In Kotlin `and`/`or` outrank `shl`, so
        // unparenthesized shifts fold into the running `or` and the trailing `shl 24` shifts
        // the whole value out -> this returned 0 for every completionStatus < 256, making the
        // spurious-fill guard treat every low-prime fill as aborted. Parenthesize each shift.
        val bits = (cargo[14].toInt() and 0xFF) or
            ((cargo[15].toInt() and 0xFF) shl 8) or
            ((cargo[16].toInt() and 0xFF) shl 16) or
            ((cargo[17].toInt() and 0xFF) shl 24)
        return bits.toLong()
    }

    /** Reads the little-endian float prime size from a fill cargo at bytes [10..14). */
    private fun fillPrimeSize(cargo: ByteArray): Float {
        val b0 = cargo[10].toInt() and 0xFF
        val b1 = cargo[11].toInt() and 0xFF
        val b2 = cargo[12].toInt() and 0xFF
        val b3 = cargo[13].toInt() and 0xFF
        val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        return java.lang.Float.intBitsToFloat(bits)
    }

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Convert history logs to Nightscout treatments
        val treatments = logs.mapNotNull { item ->
            try {
                cartridgeToNightscoutTreatment(item, config)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert cartridge change seqId=${item.seqId}")
                null
            }
        }

        if (treatments.isEmpty()) {
            Timber.d("${processorName()}: No treatments to upload")
            return 0
        }

        // Upload to Nightscout
        val result = nightscoutApi.uploadTreatments(treatments)
        return result.getOrElse {
            Timber.e(it, "${processorName()}: Upload failed")
            0
        }
    }

    private fun cartridgeToNightscoutTreatment(item: HistoryLogItem, config: NightscoutSyncConfig): NightscoutTreatment? {
        val parsed = item.parse()

        return when (parsed) {
            is CartridgeFilledHistoryLog -> {
                NightscoutTreatment.fromTimestamp(
                    eventType = "Insulin Change",
                    timestamp = item.pumpTimeLocal(),
                    seqId = item.seqId,
                    notes = config.buildNotes { append("Cartridge filled with ${parsed.insulinDisplay}U (actual: ${parsed.insulinActual}U)") }
                )
            }
            is TubingFilledHistoryLog -> {
                if (parsed.cargo != null && isSpuriousFill(parsed.cargo)) {
                    Timber.d("Skipping spurious tubing fill seqId=${item.seqId} (aborted)")
                    return null
                }
                NightscoutTreatment.fromTimestamp(
                    eventType = "Site Change",
                    timestamp = item.pumpTimeLocal(),
                    seqId = item.seqId,
                    notes = config.buildNotes { append("Tubing filled/primed with ${parsed.primeSize}U") }
                )
            }
            is CannulaFilledHistoryLog -> {
                if (parsed.cargo != null && isSpuriousFill(parsed.cargo)) {
                    Timber.d("Skipping spurious cannula fill seqId=${item.seqId} (aborted)")
                    return null
                }
                NightscoutTreatment.fromTimestamp(
                    eventType = "Site Change",
                    timestamp = item.pumpTimeLocal(),
                    seqId = item.seqId,
                    notes = config.buildNotes { append("Cannula filled/primed with ${parsed.primeSize}U") }
                )
            }
            else -> {
                Timber.w("Unexpected cartridge type: ${parsed.javaClass.simpleName}")
                null
            }
        }
    }
}
