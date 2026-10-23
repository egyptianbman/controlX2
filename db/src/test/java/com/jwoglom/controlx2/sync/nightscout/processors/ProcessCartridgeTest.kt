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
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutProfile
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutStateSpan
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import io.mockk.mockk
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for the cartridge -> Nightscout treatment mapping.
 *
 * Covers the exercise-mode fix: the pump emits a zero-prime, USER_ABORTED tubing/cannula fill
 * right after a Control-IQ mode change (e.g. toggling Exercise). Those artifacts must NOT become
 * "Site Change" treatments, while real completed fills (cannula ~0.3U, tubing >= 12U) and a
 * legitimately aborted-but-primed fill must still upload.
 */
class ProcessCartridgeTest {

    // CompletionStatus raw ids (USER_ABORTED=0, COMPLETED=3).
    private val STATUS_ABORTED = 0L
    private val STATUS_COMPLETED = 3L

    @Before
    fun clearCache() = itemLruCache.evictAll()

    /** NightscoutApi that records the treatments ProcessCartridge tries to upload. */
    private class CapturingApi : NightscoutApi {
        val treatments = mutableListOf<NightscoutTreatment>()
        override suspend fun uploadEntries(entries: List<NightscoutEntry>): Result<Int> = Result.success(0)
        override suspend fun uploadTreatments(treatments: List<NightscoutTreatment>): Result<Int> {
            this.treatments.addAll(treatments)
            return Result.success(treatments.size)
        }
        override suspend fun uploadDeviceStatus(status: NightscoutDeviceStatus): Result<Boolean> = Result.success(true)
        override suspend fun getLastEntries(count: Int): Result<List<NightscoutEntry>> = Result.success(emptyList())
        override suspend fun getLastTreatment(eventType: String): Result<NightscoutTreatment?> = Result.success(null)
        override suspend fun uploadProfile(profile: NightscoutProfile): Result<Boolean> = Result.success(true)
        override suspend fun getAnnouncements(count: Int): Result<List<NightscoutAnnouncement>> = Result.success(emptyList())
        override suspend fun getAnnouncementsSince(sinceTimestamp: Long?, sinceId: String?, count: Int): Result<List<NightscoutAnnouncement>> =
            Result.success(emptyList())
        override suspend fun getActivePumpModeSpans(): Result<List<NightscoutStateSpan>> = Result.success(emptyList())
        override suspend fun createStateSpan(span: NightscoutStateSpan): Result<NightscoutStateSpan> = Result.success(span)
        override suspend fun closeStateSpan(id: String, endMills: Long): Result<Boolean> = Result.success(true)
    }

    private fun tubing(seq: Long, prime: Float, status: Long, position: Long = 0L): HistoryLogItem {
        val message = TubingFilledHistoryLog(4_242L, seq, prime, status, position)
        return HistoryLogItem(message)
    }

    private fun cannula(seq: Long, prime: Float, status: Long): HistoryLogItem {
        val message = CannulaFilledHistoryLog(4_242L, seq, prime, status)
        return HistoryLogItem(message)
    }

    private fun process(logs: List<HistoryLogItem>): List<NightscoutTreatment> {
        val api = CapturingApi()
        val processor = ProcessCartridge(api, mockk<HistoryLogRepo>(relaxed = true))
        runBlocking { processor.process(logs, NightscoutSyncConfig()) }
        return api.treatments
    }

    @Test
    fun realTubingFill_uploadsAsSiteChange() {
        val treatments = process(listOf(tubing(100L, 17.09f, STATUS_COMPLETED, 0xFFFFFFFFL)))

        assertEquals(1, treatments.size)
        assertEquals("Site Change", treatments[0].eventType)
    }

    @Test
    fun realCannulaFill_uploadsAsSiteChange() {
        val treatments = process(listOf(cannula(101L, 0.3f, STATUS_COMPLETED)))

        assertEquals(1, treatments.size)
        assertEquals("Site Change", treatments[0].eventType)
    }

    @Test
    fun spuriousTubingFill_abortedWithNegligiblePrime_uploadsNothing() {
        // The artifact the pump emits after toggling Exercise: aborted, prime ~0.
        val treatments = process(listOf(tubing(102L, 0.0f, STATUS_ABORTED, 0L)))

        assertTrue("Exercise-mode artifact must not upload as a Site Change", treatments.isEmpty())
    }

    @Test
    fun spuriousCannulaFill_abortedWithNegligiblePrime_uploadsNothing() {
        val treatments = process(listOf(cannula(103L, 0.0f, STATUS_ABORTED)))

        assertTrue(treatments.isEmpty())
    }

    @Test
    fun abortedTubingFillWithRealPrime_stillUploads() {
        // Aborted but primed a meaningful amount: a real (incomplete) site change, not an artifact.
        val treatments = process(listOf(tubing(104L, 15.0f, STATUS_ABORTED, 0L)))

        assertEquals(1, treatments.size)
        assertEquals("Site Change", treatments[0].eventType)
    }
}
