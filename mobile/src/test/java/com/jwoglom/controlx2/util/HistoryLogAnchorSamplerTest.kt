package com.jwoglom.controlx2.util

import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorDao
import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorItem
import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.SeqIdPumpTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HistoryLogAnchorSamplerTest {
    private val day = 86400L

    private fun item(seqId: Long, dayOffset: Long) =
        HistoryLogItem(
            seqId = seqId, pumpSid = 1, typeId = 1, cargo = ByteArray(0),
            pumpTimeSec = dayOffset * day, pumpTime = LocalDateTime.now()
        )

    /** In-memory anchor DAO that records what the sampler inserts. */
    private class FakeAnchorDao : HistoryLogAnchorDao {
        val inserted = mutableListOf<HistoryLogAnchorItem>()
        override suspend fun insertAll(anchors: List<HistoryLogAnchorItem>) { inserted.addAll(anchors) }
        override fun getAll(pumpSid: Int): List<HistoryLogAnchorItem> = inserted.toList()
        override fun getMinAnchor(pumpSid: Int): Long? = inserted.minOfOrNull { it.anchorId }
        override fun getMaxAnchor(pumpSid: Int): Long? = inserted.maxOfOrNull { it.anchorId }
        override fun count(pumpSid: Int): Long = inserted.size.toLong()
    }

    private fun sampler(spacing: Long = 10_000L, dao: HistoryLogDummyDao = HistoryLogDummyDao(mutableListOf())) =
        HistoryLogAnchorSampler(HistoryLogAnchorRepo(FakeAnchorDao()), dao, 1, sampleSpacing = spacing)

    // --- anchorTargets (pure) ---

    @Test
    fun anchorTargets_walksHeadDownOldest() {
        val targets = sampler().anchorTargets(oldestId = 1000, headId = 32000)
        assertEquals(listOf(1000L, 2000L, 12000L, 22000L, 32000L), targets)
    }

    @Test
    fun anchorTargets_headBelowOldest_empty() {
        assertTrue(sampler().anchorTargets(oldestId = 5000, headId = 3000).isEmpty())
    }

    @Test
    fun anchorTargets_headEqualsOldest_singlePoint() {
        assertEquals(listOf(1000L), sampler().anchorTargets(oldestId = 1000, headId = 1000))
    }

    @Test
    fun anchorTargets_includesBothEndpointsAndDedupes() {
        // head=20000 lands exactly on a step; oldest added explicitly, no dupes.
        val targets = sampler(spacing = 10_000L).anchorTargets(oldestId = 10000, headId = 20000)
        assertEquals(listOf(10000L, 20000L), targets)
    }

    // --- sample ---

    @Test
    fun sample_recordsClosestAtOrBelowPerTarget() = runBlocking {
        val dao = HistoryLogDummyDao(mutableListOf(item(1000, 0), item(11000, 1), item(21000, 2), item(31000, 3)))
        val result = sampler(dao = dao).sample(oldestId = 1000, headId = 31000)
        // Targets ascending [1000,11000,21000,31000]; each has exact data.
        assertEquals(listOf(0L, 1L, 2L, 3L), result.map { it.pumpTimeSec / day })
        assertEquals(listOf(1000L, 11000L, 21000L, 31000L), result.map { it.anchorId })
    }

    @Test
    fun sample_fallsBackToClosestExistingAcrossGaps() = runBlocking {
        // Data only at 1000 and 31000; mid targets resolve to the nearest existing seqId below.
        val dao = HistoryLogDummyDao(mutableListOf(item(1000, 0), item(31000, 3)))
        val result = sampler(dao = dao).sample(oldestId = 1000, headId = 31000)
        assertEquals(listOf(0L, 0L, 0L, 3L), result.map { it.pumpTimeSec / day })
        // Oldest anchor present so the bottom of the range has a reference.
        assertTrue(result.any { it.anchorId == 1000L })
    }

    @Test
    fun sample_emptyRange_returnsEmpty() = runBlocking {
        val dao = HistoryLogDummyDao(mutableListOf())
        assertTrue(sampler(dao = dao).sample(oldestId = 5000, headId = 3000).isEmpty())
    }
}
