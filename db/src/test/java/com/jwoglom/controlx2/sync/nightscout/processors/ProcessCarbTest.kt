package com.jwoglom.controlx2.sync.nightscout.processors

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.itemLruCache
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncConfig
import com.jwoglom.controlx2.sync.nightscout.api.NightscoutApi
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutAnnouncement
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutDeviceStatus
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutProfile
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutStateSpan
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEnteredHistoryLog
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for the carb -> Nightscout treatment mapping, focused on the dedup that fixes the
 * double-sync bug: the pump logs a fresh CarbEnteredHistoryLog every time the carbs field is
 * confirmed, so editing the carbs before approving a bolus produces a tight cluster of entries.
 * Only the final value before the BolusRequested (typeId 64, the "bolus boundary") is real; the
 * earlier edits must not become separate carb intakes in nocturne.
 */
class ProcessCarbTest {

    @Before
    fun clearCache() = itemLruCache.evictAll()

    /** NightscoutApi that records the treatments ProcessCarb tries to upload. */
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
        override suspend fun getActivePumpModeSpans(): Result<List<NightscoutStateSpan>> = Result.success(emptyList())
        override suspend fun createStateSpan(span: NightscoutStateSpan): Result<NightscoutStateSpan> = Result.success(span)
        override suspend fun closeStateSpan(id: String, endMills: Long): Result<Boolean> = Result.success(true)
    }

    /** Build a HistoryLogItem wrapping a real CarbEnteredHistoryLog with [seqNum] as seqId and pump clock. */
    private fun carb(seqNum: Long, carbs: Float): HistoryLogItem =
        HistoryLogItem(CarbEnteredHistoryLog(seqNum, seqNum, carbs))

    /**
     * Run the real coordinator path over [logs]: dedupe against [boundaries] (BolusRequested seqIds),
     * then upload. Returns the treatments that would hit nocturne.
     */
    private fun process(logs: List<HistoryLogItem>, boundaries: Set<Long>): List<NightscoutTreatment> {
        val api = CapturingApi()
        val processor = ProcessCarb(api, mockk<HistoryLogRepo>(relaxed = true))
        val (relevant, _) = processor.dedupeComboBolus(logs.sortedBy { it.seqId }, 0, boundaries)
        runBlocking { processor.process(relevant, NightscoutSyncConfig()) }
        return api.treatments
    }

    /** The core bug: six carb edits before the bolus approval collapse to a single intake = final value. */
    @Test
    fun carbClusterBeforeBolus_keepsOnlyFinalValue() {
        val edits = listOf(101L, 102L, 103L, 104L, 105L, 106L).mapIndexed { i, seq -> carb(seq, (12 + i * 2).toFloat()) }
        // Final edit is 22g. Boundary is the BolusRequested that approves the bolus.
        val treatments = process(edits, setOf(107L))

        assertEquals(1, treatments.size)
        val t = treatments[0]
        assertEquals("Carb Correction", t.eventType)
        assertEquals(22.0, t.carbs!!, 0.001)
        assertEquals("106", t.syncIdentifier) // the surviving edit's seqId
    }

    /** A single carb entry before the bolus is real and must survive. */
    @Test
    fun singleCarbBeforeBolus_keepsIt() {
        val treatments = process(listOf(carb(200L, 45f)), setOf(201L))

        assertEquals(1, treatments.size)
        assertEquals(45.0, treatments[0].carbs!!, 0.001)
        assertEquals("200", treatments[0].syncIdentifier)
    }

    /** Carbs entered after the last boundary (a carb-only entry, no bolus) are never dropped. */
    @Test
    fun trailingCarbsAfterBoundary_keptUnchanged() {
        val logs = listOf(
            carb(101L, 10f),
            carb(102L, 12f),
            carb(103L, 30f), // after the boundary -> carb-only, kept as-is
        )
        val treatments = process(logs, setOf(103L))

        // 101 and 102 are a cluster before the boundary -> keep only 102 (12g). 103 is trailing -> kept.
        assertEquals(2, treatments.size)
        assertEquals(12.0, treatments[0].carbs!!, 0.001)
        assertEquals("102", treatments[0].syncIdentifier)
        assertEquals(30.0, treatments[1].carbs!!, 0.001)
        assertEquals("103", treatments[1].syncIdentifier)
    }

    /** Without a boundary there is nothing to dedup against: every carb entry passes through. */
    @Test
    fun emptyBoundaryPassesThroughUnchanged() {
        val edits = listOf(101L, 102L, 103L).mapIndexed { i, seq -> carb(seq, (10 + i).toFloat()) }
        val treatments = process(edits, emptySet())

        assertEquals(3, treatments.size)
        assertEquals(listOf(10.0, 11.0, 12.0), treatments.map { it.carbs })
    }

    /** Two separate bolus approvals each keep only their own last carb edit. */
    @Test
    fun twoBolusBoundaries_eachKeepsItsLastCarb() {
        val logs = listOf(
            carb(101L, 10f),
            carb(102L, 12f),
            carb(103L, 14f),
            carb(111L, 30f),
            carb(112L, 32f),
        )
        val treatments = process(logs, setOf(104L, 113L))

        assertEquals(2, treatments.size)
        assertEquals(14.0, treatments[0].carbs!!, 0.001)
        assertEquals("103", treatments[0].syncIdentifier)
        assertEquals(32.0, treatments[1].carbs!!, 0.001)
        assertEquals("112", treatments[1].syncIdentifier)
    }
}
