package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.itemLruCache
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.nightscout.NightscoutProcessorStateDao
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutAnnouncement
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutDeviceStatus
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import io.mockk.mockk
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutProfile
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for the bolus -> Nightscout treatment mapping.
 *
 * These run on the host (no instrumented device, no network server) and verify the field
 * computation in ProcessBolus directly: most importantly that an extended/combo bolus uploads
 * insulin = the TOTAL programmed dose (immediate + extended), not the immediate portion — the
 * bug these fields exist to fix — and that the nocturne combo markers (type, programmed,
 * syncIdentifier) are set correctly for each bolus kind.
 */
class ProcessBolusTest {

    @Before
    fun clearCache() = itemLruCache.evictAll()

    /** NightscoutApi that records the treatments ProcessBolus tries to upload. */
    private class CapturingApi : NightscoutApi {
        val treatments = mutableListOf<NightscoutTreatment>()
        override suspend fun uploadEntries(entries: List<NightscoutEntry>): Result<Int> =
            Result.success(0)

        override suspend fun uploadTreatments(treatments: List<NightscoutTreatment>): Result<Int> {
            this.treatments.addAll(treatments)
            return Result.success(treatments.size)
        }

        override suspend fun uploadDeviceStatus(status: NightscoutDeviceStatus): Result<Boolean> =
            Result.success(true)

        override suspend fun getLastEntries(count: Int): Result<List<NightscoutEntry>> =
            Result.success(emptyList())

        override suspend fun getLastTreatment(eventType: String): Result<NightscoutTreatment?> =
            Result.success(null)

        override suspend fun uploadProfile(profile: NightscoutProfile): Result<Boolean> =
            Result.success(true)

        override suspend fun getAnnouncements(count: Int): Result<List<NightscoutAnnouncement>> =
            Result.success(emptyList())

        override suspend fun getAnnouncementsSince(
            sinceTimestamp: Long?,
            sinceId: String?,
            count: Int
        ): Result<List<NightscoutAnnouncement>> = Result.success(emptyList())
    }

    /**
     * Build a HistoryLogItem wrapping a real BolusDeliveryHistoryLog.
     * Constructor field order: pumpTimeSec, sequenceNum, bolusID, statusId, types, source,
     * reserved, requestedNow, requestedLater, correction, extendedDurationRequested, deliveredTotal.
     */
    private fun bolus(
        seqNum: Long,
        bolusID: Int,
        requestedNow: Int,
        requestedLater: Int,
        duration: Int,
        deliveredTotal: Int
    ): HistoryLogItem {
        val message = BolusDeliveryHistoryLog(
            4_242L,       // pumpTimeSec (arbitrary; not asserted)
            seqNum,         // sequenceNum -> HistoryLogItem.seqId -> clientTreatmentId
            bolusID,
            2,
            setOf(BolusDeliveryHistoryLog.BolusType.NOW),
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS,
            0,
            requestedNow,
            requestedLater,
            0,
            duration,
            deliveredTotal
        )
        return HistoryLogItem(message)
    }

    /** Run ProcessBolus over [logs] and return the treatments it attempted to upload. */
    private fun process(logs: List<HistoryLogItem>): List<NightscoutTreatment> {
        val api = CapturingApi()
        val processor = ProcessBolus(api, mockk<HistoryLogRepo>(relaxed = true), mockk<NightscoutProcessorStateDao>(relaxed = true))
        runBlocking { processor.process(logs, NightscoutSyncConfig()) }
        return api.treatments
    }

    @Test
    fun standardBolus_uploadsInsulinOnly_noComboFields() {
        val treatments = process(listOf(bolus(3000L, 1, 3000, 0, 0, 3000)))

        assertEquals(1, treatments.size)
        val t = treatments[0]
        assertEquals("Bolus", t.eventType)
        assertEquals(3.0, t.insulin!!, 0.001)
        // Non-extended boluses carry no combo/dual markers.
        assertNull(t.type)
        assertNull(t.programmed)
        assertNull(t.enteredInsulin)
        assertNull(t.splitNow)
        assertNull(t.duration)
        // syncIdentifier defaults to the seqId for standard boluses.
        assertEquals("3000", t.syncIdentifier)
        // Nocturne identity fields are present on every treatment.
        assertEquals("ControlX2", t.dataSource)
        assertEquals("ControlX2", t.device)
    }

    @Test
    fun comboDualBolus_uploadsTotalProgrammedDose() {
        // 10U total: 5U now (50%) + 5U over 120 min (50%).
        val treatments = process(listOf(bolus(25001L, 1, 5000, 5000, 120, 10000)))

        assertEquals(1, treatments.size)
        val t = treatments[0]
        assertEquals("Combo Bolus", t.eventType)
        // The fix: insulin is the TOTAL programmed dose, not the 5U immediate portion.
        assertEquals(10.0, t.insulin!!, 0.001)
        assertEquals(10.0, t.programmed!!, 0.001)
        assertEquals(10.0, t.enteredInsulin!!, 0.001)
        assertEquals("dual", t.type) // immediate > 0
        assertEquals(50, t.splitNow)
        assertEquals(50, t.splitExt)
        assertEquals(120, t.duration)
        assertEquals(2.5, t.relative!!, 0.001) // 5U / 120min * 60
        // Stable dedup key is the pump bolusID, not the seqId.
        assertEquals("1", t.syncIdentifier)
        assertEquals("25001", t.clientTreatmentId)
    }

    @Test
    fun squareBolus_uploadsAsSquareType() {
        // Pure extended: 0U now + 6U over 120 min.
        val treatments = process(listOf(bolus(27001L, 1, 0, 6000, 120, 6000)))

        assertEquals(1, treatments.size)
        val t = treatments[0]
        assertEquals("Combo Bolus", t.eventType)
        assertEquals(6.0, t.insulin!!, 0.001)
        assertEquals(6.0, t.programmed!!, 0.001)
        assertEquals("square", t.type) // immediate == 0
        assertEquals(0, t.splitNow)
        assertEquals(100, t.splitExt)
        assertEquals(3.0, t.relative!!, 0.001) // 6U / 120min * 60
        assertEquals("1", t.syncIdentifier)
    }
}
