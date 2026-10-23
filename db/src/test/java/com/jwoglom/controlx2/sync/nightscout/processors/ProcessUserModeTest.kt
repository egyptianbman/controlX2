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
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutStateSpan
import io.mockk.mockk
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQUserModeChangeHistoryLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset

/**
 * JVM unit tests for Control-IQ mode changes -> Nocturne PumpMode state spans.
 *
 * Covers the self-contained-span design: each record becomes one PumpMode span with a look-ahead
 * endMills (null for the last), any pre-existing open span before the first record is closed at T1,
 * and re-sync never re-closes a span already at T1.
 */
class ProcessUserModeTest {

    // ControlIQUserModeChangeHistoryLog currentUserMode ids.
    private val MODE_NORMAL = 0
    private val MODE_SLEEP = 1
    private val MODE_EXERCISE = 2
    private val MODE_EATING_SOON = 3

    @Before
    fun clearCache() = itemLruCache.evictAll()

    /** NightscoutApi that records state-span calls made by ProcessUserMode. */
    private class CapturingApi : NightscoutApi {
        val createdSpans = mutableListOf<NightscoutStateSpan>()
        val closedSpans = mutableListOf<Pair<String, Long>>()
        var openSpansOnFetch = mutableListOf<NightscoutStateSpan>()

        override suspend fun getActivePumpModeSpans(): Result<List<NightscoutStateSpan>> =
            Result.success(openSpansOnFetch.toList())

        override suspend fun createStateSpan(span: NightscoutStateSpan): Result<NightscoutStateSpan> {
            createdSpans.add(span)
            return Result.success(span)
        }

        override suspend fun closeStateSpan(id: String, endMills: Long): Result<Boolean> {
            closedSpans.add(id to endMills)
            return Result.success(true)
        }

        override suspend fun uploadEntries(entries: List<NightscoutEntry>): Result<Int> = Result.success(0)
        override suspend fun uploadTreatments(treatments: List<NightscoutTreatment>): Result<Int> = Result.success(0)
        override suspend fun uploadDeviceStatus(status: NightscoutDeviceStatus): Result<Boolean> = Result.success(true)
        override suspend fun getLastEntries(count: Int): Result<List<NightscoutEntry>> = Result.success(emptyList())
        override suspend fun getLastTreatment(eventType: String): Result<NightscoutTreatment?> = Result.success(null)
        override suspend fun uploadProfile(profile: NightscoutProfile): Result<Boolean> = Result.success(true)
        override suspend fun getAnnouncements(count: Int): Result<List<NightscoutAnnouncement>> =
            Result.success(emptyList())
        override suspend fun getAnnouncementsSince(
            sinceTimestamp: Long?,
            sinceId: String?,
            count: Int
        ): Result<List<NightscoutAnnouncement>> = Result.success(emptyList())
    }

    private fun mode(seq: Long, pumpTimeSec: Long, currentUserMode: Int): HistoryLogItem {
        val message = ControlIQUserModeChangeHistoryLog(
            pumpTimeSec, seq, currentUserMode, 0, 0, 0, 0, 0, 0, 0, 0
        )
        return HistoryLogItem(message)
    }

    private fun HistoryLogItem.millis(): Long =
        pumpTimeLocal().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun process(
        logs: List<HistoryLogItem>,
        initialOpenSpans: List<NightscoutStateSpan> = emptyList()
    ): CapturingApi {
        val api = CapturingApi().apply { openSpansOnFetch = initialOpenSpans.toMutableList() }
        runBlocking { ProcessUserMode(api, mockk<HistoryLogRepo>(relaxed = true)).process(logs, NightscoutSyncConfig()) }
        return api
    }

    @Test
    fun singleExerciseMode_createsOpenSpan() {
        val rec = mode(seq = 1, pumpTimeSec = 1_000, currentUserMode = MODE_EXERCISE)
        val api = process(listOf(rec))

        assertEquals(1, api.createdSpans.size)
        val span = api.createdSpans[0]
        assertEquals("PumpMode", span.category)
        assertEquals("Exercise", span.state)
        assertEquals(rec.millis(), span.startMills)
        assertNull(span.endMills)  // last (only) record stays open
        assertEquals("controlx2_mode_1", span.originalId)
        assertEquals(emptyList<Pair<String, Long>>(), api.closedSpans)  // no pre-existing spans to close
    }

    @Test
    fun consecutiveModes_getLookAheadEndMills() {
        val exercise = mode(seq = 1, pumpTimeSec = 1_000, currentUserMode = MODE_EXERCISE)
        val sleep = mode(seq = 2, pumpTimeSec = 2_000, currentUserMode = MODE_SLEEP)
        val api = process(listOf(exercise, sleep))

        assertEquals(2, api.createdSpans.size)
        // First span ends when the second begins; second (last) stays open.
        assertEquals(exercise.millis(), api.createdSpans[0].startMills)
        assertEquals(sleep.millis(), api.createdSpans[0].endMills)
        assertEquals(sleep.millis(), api.createdSpans[1].startMills)
        assertNull(api.createdSpans[1].endMills)
    }

    @Test
    fun modeMapping_mapsEveryRecognizedMode() {
        val normal = mode(seq = 1, pumpTimeSec = 1_000, currentUserMode = MODE_NORMAL)
        val sleep = mode(seq = 2, pumpTimeSec = 2_000, currentUserMode = MODE_SLEEP)
        val exercise = mode(seq = 3, pumpTimeSec = 3_000, currentUserMode = MODE_EXERCISE)
        val eatingSoon = mode(seq = 4, pumpTimeSec = 4_000, currentUserMode = MODE_EATING_SOON)
        val api = process(listOf(normal, sleep, exercise, eatingSoon))

        assertEquals(listOf("Automatic", "Sleep", "Exercise", "Automatic"), api.createdSpans.map { it.state })
        assertEquals(4, api.createdSpans.size)
    }

    @Test
    fun preExistingOpenSpanBeforeFirstRecord_isClosedAtT1() {
        val exercise = mode(seq = 1, pumpTimeSec = 1_000, currentUserMode = MODE_EXERCISE)
        val firstStart = exercise.millis()
        // A pre-existing span (from an earlier sync) still open well before T1.
        val preExisting = NightscoutStateSpan(
            id = "pre1", category = "PumpMode", state = "Sleep",
            startMills = firstStart - 60_000, endMills = null
        )
        val api = process(listOf(exercise), initialOpenSpans = listOf(preExisting))

        // The pre-existing span is closed at T1; the new Exercise span is posted open.
        assertEquals(listOf("pre1" to firstStart), api.closedSpans)
        assertEquals(1, api.createdSpans.size)
        assertEquals("Exercise", api.createdSpans[0].state)
        assertNull(api.createdSpans[0].endMills)
    }

    @Test
    fun reSync_openSpanAlreadyAtT1_isNotReclosed() {
        val exercise = mode(seq = 1, pumpTimeSec = 1_000, currentUserMode = MODE_EXERCISE)
        val firstStart = exercise.millis()
        // Re-sync: the last span from the prior batch is still open, its startMills = T1 (not < T1).
        val stillOpen = NightscoutStateSpan(
            id = "prev", category = "PumpMode", state = "Exercise",
            startMills = firstStart, endMills = null
        )
        val api = process(listOf(exercise), initialOpenSpans = listOf(stillOpen))

        assertEquals(emptyList<Pair<String, Long>>(), api.closedSpans)  // guard leaves it open
        assertEquals(1, api.createdSpans.size)  // re-posted, idempotent by originalId
        assertEquals("controlx2_mode_1", api.createdSpans[0].originalId)
    }

    @Test
    fun unrecognizedMode_skippedWithoutTimelineGap() {
        // seq 2 has an unrecognized mode id (9); it must be skipped, and seq 1's endMills must
        // look ahead to seq 3 (the next RECORDED span), not the skipped seq 2.
        val exercise = mode(seq = 1, pumpTimeSec = 1_000, currentUserMode = MODE_EXERCISE)
        val unknown = mode(seq = 2, pumpTimeSec = 2_000, currentUserMode = 9)
        val sleep = mode(seq = 3, pumpTimeSec = 3_000, currentUserMode = MODE_SLEEP)
        val api = process(listOf(exercise, unknown, sleep))

        assertEquals(2, api.createdSpans.size)
        assertEquals("Exercise", api.createdSpans[0].state)
        assertEquals(sleep.millis(), api.createdSpans[0].endMills)  // skips over seq 2
        assertEquals("Sleep", api.createdSpans[1].state)
        assertNull(api.createdSpans[1].endMills)
    }
}
