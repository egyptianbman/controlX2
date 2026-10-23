package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.ProcessorType
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutStateSpan
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQUserModeChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import timber.log.Timber
import java.time.ZoneOffset

/**
 * Control-IQ user mode changes.
 *
 * Nocturne has no NightScout treatment eventType for Control-IQ modes, so ControlX2 reports them as
 * PumpMode state spans (api/v4/state-spans) instead of treatments. Each typeId-229 history log
 * becomes one PumpMode span: it starts when the mode takes effect and ends when the next mode
 * change is recorded (look-ahead), or stays open (endMills=null) for the most recent change.
 *
 * State spans are self-contained: each POST carries an explicit endMills, so re-syncing never
 * relies on the server auto-closing a previous span (Nocturne only auto-closes same-state spans, so
 * a cross-state handoff like Sleep->Manual would otherwise leak an open span). Before posting, any
 * PumpMode span still open before the first record's time (startMills < T1) is closed at T1, which
 * handoffs a pre-existing mode cleanly — including the prior sync batch's last open span, whose
 * startMills (its own time) is < the new batch's T1. The startMills < T1 guard is also what skips
 * re-closing on re-sync (the last span's startMills = Tn, not < T1), so it is retained on purpose.
 */
class ProcessUserMode(
    nightscoutApi: NightscoutApi,
    historyLogRepo: HistoryLogRepo
) : BaseProcessor(nightscoutApi, historyLogRepo) {

    override fun processorType() = ProcessorType.USER_MODE

    override fun supportedTypeIds(): Set<Int> =
        setOfNotNull(
            HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID[ControlIQUserModeChangeHistoryLog::class.java]
        )

    override suspend fun process(
        logs: List<HistoryLogItem>,
        config: NightscoutSyncConfig
    ): Int {
        if (logs.isEmpty()) {
            return 0
        }

        // Coordinator passes logs sorted by seqId (chronological); sort defensively so the
        // look-ahead endMills are correct regardless of caller ordering.
        val records = logs.sortedBy { it.seqId }

        // One span per record with a recognized mode. endMills is filled in separately via look-ahead
        // so skipped (unrecognized) records don't leave a gap in the timeline.
        val spans = records.mapIndexedNotNull { _, item ->
            val state = mappedState(item) ?: return@mapIndexedNotNull null
            NightscoutStateSpan(
                category = "PumpMode",
                state = state,
                startMills = item.pumpTimeMillis(),
                originalId = "controlx2_mode_${item.seqId}"
            )
        }

        if (spans.isEmpty()) {
            Timber.d("${processorName()}: No recognizable mode changes to upload")
            return 0
        }

        // Look-ahead endMills: each span ends when the next recorded span begins; the last stays open.
        val spansWithEndMills = spans.mapIndexed { index, span ->
            if (index < spans.lastIndex) span.copy(endMills = spans[index + 1].startMills)
            else span.copy(endMills = null)
        }
        // Close any PumpMode span still open before the first record's time (pre-existing mode, or
        // the prior batch's last open span). The startMills < T1 guard leaves the current batch's own
        // last span (startMills = Tn, not < T1) open on re-sync.
        val firstStart = spansWithEndMills.first().startMills
        nightscoutApi.getActivePumpModeSpans().getOrElse { e ->
            Timber.e(e, "Failed to fetch open PumpMode spans; skipping pre-close")
            emptyList<NightscoutStateSpan>()
        }.filter { it.endMills == null && it.startMills < firstStart }
          .forEach { span ->
              val id = span.id ?: span.originalId
              when {
                  id == null -> Timber.w("Open PumpMode span missing id/originalId; cannot close")
                  else -> nightscoutApi.closeStateSpan(id, firstStart).getOrElse { e ->
                      Timber.e(e, "Failed to close open PumpMode span $id at $firstStart")
                  }
              }
          }

        // Post each span. Each POST is self-contained with an explicit endMills.
        var count = 0
        for (span in spansWithEndMills) {
            nightscoutApi.createStateSpan(span).fold(
                onSuccess = { count++ },
                onFailure = { e -> Timber.e(e, "Failed to create PumpMode span ${span.originalId}") }
            )
        }
        return count
    }

    /**
     * Map a mode-change record to a Nocturne PumpModeState string, or null if unrecognized.
     *
     * Eating Soon (id 3) is a closed-loop post-meal sub-mode, so it maps to "Automatic" — Nocturne
     * has no dedicated Eating Soon PumpModeState. (Peer design listed Standard/Sleep/Exercise/Manual/
     * Suspended; Manual and Suspended are not pump UserMode values and arrive from elsewhere.)
     */
    private fun mappedState(item: HistoryLogItem): String? {
        val parsed = item.parse()
        if (parsed !is ControlIQUserModeChangeHistoryLog) {
            Timber.w("Unexpected mode type: ${parsed.javaClass.simpleName}")
            return null
        }
        val mode = parsed.currentUserModeEnum
        if (mode == null) {
            Timber.w("Unrecognized Control-IQ mode ${parsed.currentUserMode}; skipping seq ${item.seqId}")
            return null
        }
        return when (mode) {
            ControlIQUserModeChangeHistoryLog.UserMode.NORMAL -> "Automatic"
            ControlIQUserModeChangeHistoryLog.UserMode.SLEEPING -> "Sleep"
            ControlIQUserModeChangeHistoryLog.UserMode.EXERCISING -> "Exercise"
            ControlIQUserModeChangeHistoryLog.UserMode.EATING_SOON -> "Automatic"
        }
    }

    private fun HistoryLogItem.pumpTimeMillis(): Long =
        pumpTimeLocal().toInstant(ZoneOffset.UTC).toEpochMilli()
}
