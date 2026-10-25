package com.jwoglom.controlx2.util

import com.jwoglom.controlx2.db.historylog.HistoryLogAnchorItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class HistoryLogAnchorLookupTest {
    // JANUARY_1_2008_UNIX_EPOCH = 1199145600. pumpTimeSec + that = epoch seconds (UTC).
    // pumpTimeSec is seconds since 2008-01-01; the lookup adds the epoch base. So dayOffset 0 -> 2008-01-01.
    private val day = 86400L

    private fun anchor(id: Long, dayOffset: Long) =
        HistoryLogAnchorItem(pumpSid = 1, anchorId = id, pumpTimeSec = dayOffset * day)

    private fun at(year: Int, month: Int, day: Int, hour: Int): LocalDateTime =
        LocalDateTime.of(year, month, day, hour, 0, 0).atOffset(ZoneOffset.UTC).toLocalDateTime()

    @Test
    fun emptyAnchors_returnsNull() {
        val lookup = HistoryLogAnchorLookup(emptyList())
        assertNull(lookup.dateForId(100000))
    }

    @Test
    fun singleAnchor_returnsItsDate() {
        val lookup = HistoryLogAnchorLookup(listOf(anchor(100000, 0)))
        assertEquals(at(2008, 1, 1, 0), lookup.dateForId(100000))
        assertEquals(at(2008, 1, 1, 0), lookup.dateForId(200000)) // above -> clamp to it
        assertEquals(at(2008, 1, 1, 0), lookup.dateForId(50000)) // below -> clamp to it
    }

    @Test
    fun exactAnchorMatches_returnExactDate() {
        val lookup = HistoryLogAnchorLookup(
            listOf(anchor(100000, 0), anchor(110000, 1), anchor(120000, 2))
        )
        assertEquals(at(2008, 1, 1, 0), lookup.dateForId(100000))
        assertEquals(at(2008, 1, 2, 0), lookup.dateForId(110000))
        assertEquals(at(2008, 1, 3, 0), lookup.dateForId(120000))
    }

    @Test
    fun midpoint_interpolatesLinearly() {
        // Anchors 1 day apart, 10000 IDs apart. Midpoint is 12h into the day.
        val lookup = HistoryLogAnchorLookup(listOf(anchor(100000, 0), anchor(110000, 1)))
        assertEquals(at(2008, 1, 1, 12), lookup.dateForId(105000))
    }

    @Test
    fun nonUniformSpacing_interpolatesByFraction() {
        // 30000 IDs apart, 3 days apart. At +10000 IDs we are 1/3 of the way -> 1 day.
        val lookup = HistoryLogAnchorLookup(listOf(anchor(100000, 0), anchor(130000, 3)))
        assertEquals(at(2008, 1, 2, 0), lookup.dateForId(110000))
        // At +25000 IDs -> 5/6 of the way -> 2.5 days -> 2008-01-03T12:00.
        assertEquals(at(2008, 1, 3, 12), lookup.dateForId(125000))
    }

    @Test
    fun belowAllAnchors_clampsToEarliest() {
        val lookup = HistoryLogAnchorLookup(listOf(anchor(110000, 1), anchor(120000, 2)))
        assertEquals(at(2008, 1, 2, 0), lookup.dateForId(100000))
    }

    @Test
    fun aboveAllAnchors_clampsToLatest() {
        val lookup = HistoryLogAnchorLookup(listOf(anchor(100000, 0), anchor(110000, 1)))
        assertEquals(at(2008, 1, 2, 0), lookup.dateForId(120000))
    }

    @Test
    fun unsortedAnchors_stillInterpolates() {
        val lookup = HistoryLogAnchorLookup(
            listOf(anchor(110000, 1), anchor(100000, 0)) // reversed on purpose
        )
        assertEquals(at(2008, 1, 1, 12), lookup.dateForId(105000))
    }
    // --- idForDate (inverse of dateForId) ---

    @Test
    fun idForDate_emptyAnchors_returnsFallback() {
        val lookup = HistoryLogAnchorLookup(emptyList())
        assertEquals(777L, lookup.idForDate(at(2008, 1, 5, 0), fallback = 777L))
    }

    @Test
    fun idForDate_exactAnchorDate_returnsItsId() {
        val lookup = HistoryLogAnchorLookup(
            listOf(anchor(100000, 0), anchor(110000, 1), anchor(120000, 2))
        )
        assertEquals(100000L, lookup.idForDate(at(2008, 1, 1, 0), fallback = 0L))
        assertEquals(120000L, lookup.idForDate(at(2008, 1, 3, 0), fallback = 0L))
    }

    @Test
    fun idForDate_interpolatesBetweenAnchors() {
        // 1 day apart, 10000 IDs apart. 12h in -> midpoint id; 8h in -> 1/3 of the way.
        val lookup = HistoryLogAnchorLookup(listOf(anchor(100000, 0), anchor(110000, 1)))
        assertEquals(105000L, lookup.idForDate(at(2008, 1, 1, 12), fallback = 0L))
        assertEquals(103333L, lookup.idForDate(at(2008, 1, 1, 8), fallback = 0L))
    }

    @Test
    fun idForDate_belowAllAnchors_returnsFallback() {
        val lookup = HistoryLogAnchorLookup(listOf(anchor(110000, 1), anchor(120000, 2)))
        assertEquals(42L, lookup.idForDate(at(2008, 1, 1, 0), fallback = 42L))
    }

    @Test
    fun idForDate_aboveAllAnchors_returnsHeadAnchor() {
        val lookup = HistoryLogAnchorLookup(listOf(anchor(100000, 0), anchor(110000, 1)))
        assertEquals(110000L, lookup.idForDate(at(2008, 1, 10, 0), fallback = 0L))
    }

    // --- recentStart ---

    @Test
    fun recentStart_defaultsTo24hWindow() {
        // 4 anchors, 1 day apart. head at day 3 -> 24h back is day 2 (id 120000).
        val lookup = HistoryLogAnchorLookup(
            listOf(anchor(100000, 0), anchor(110000, 1), anchor(120000, 2), anchor(130000, 3))
        )
        assertEquals(120000L, lookup.recentStart(head = 130000L, fallback = 100000L))
    }

    @Test
    fun recentStart_respectsCustomHours() {
        val lookup = HistoryLogAnchorLookup(
            listOf(anchor(100000, 0), anchor(110000, 1), anchor(120000, 2), anchor(130000, 3))
        )
        // 48h back from day 3 -> day 1 (id 110000).
        assertEquals(110000L, lookup.recentStart(head = 130000L, fallback = 100000L, hours = 48))
    }

    @Test
    fun recentStart_noAnchors_fallsBackToFullSpan() {
        val lookup = HistoryLogAnchorLookup(emptyList())
        assertEquals(100000L, lookup.recentStart(head = 130000L, fallback = 100000L))
    }

    @Test
    fun recentStart_windowLargerThanHistory_fallsBackToFullSpan() {
        // Data spans 24h (day 0..day 1); asking for 48h back lands before the oldest anchor.
        val lookup = HistoryLogAnchorLookup(listOf(anchor(100000, 0), anchor(120000, 1)))
        assertEquals(100000L, lookup.recentStart(head = 120000L, fallback = 100000L, hours = 48))
    }
}
