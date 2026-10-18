package com.jwoglom.controlx2.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLogAnchorManagerTest {
    // SampleSpacingIds = 10_000; DbCountGrowthThreshold = 5_000 (see HistoryLogAnchorManager).

    @Test
    fun firstRunAlwaysSamples() {
        assertTrue(
            HistoryLogAnchorManager.shouldSample(
                prevOldest = null, prevHead = null, prevCount = 0,
                curOldest = 1_000, curHead = 1_000, count = 0
            )
        )
    }

    @Test
    fun noDataYetDoesNotSample() {
        // Not the first run (bounds set), but history has not fetched (DB empty) -> nothing to sample.
        assertFalse(
            HistoryLogAnchorManager.shouldSample(
                prevOldest = 0, prevHead = 0, prevCount = 0,
                curOldest = 100_000, curHead = 100_000, count = 0
            )
        )
    }

    @Test
    fun headAdvanceTriggersSample() {
        assertTrue(
            HistoryLogAnchorManager.shouldSample(
                prevOldest = 0, prevHead = 0, prevCount = 0,
                curOldest = 0, curHead = 10_000, count = 1
            )
        )
        assertFalse(
            HistoryLogAnchorManager.shouldSample(
                prevOldest = 0, prevHead = 0, prevCount = 0,
                curOldest = 0, curHead = 9_999, count = 1
            )
        )
    }

    @Test
    fun significantDbFillTriggersSample() {
        assertTrue(
            HistoryLogAnchorManager.shouldSample(
                prevOldest = 0, prevHead = 0, prevCount = 0,
                curOldest = 0, curHead = 0, count = 5_000
            )
        )
        assertFalse(
            HistoryLogAnchorManager.shouldSample(
                prevOldest = 0, prevHead = 0, prevCount = 0,
                curOldest = 0, curHead = 0, count = 4_999
            )
        )
    }

    @Test
    fun noGrowthNoSample() {
        // Data exists but neither head, oldest, nor DB-count moved since last sample -> skip.
        assertFalse(
            HistoryLogAnchorManager.shouldSample(
                prevOldest = 50_000, prevHead = 50_000, prevCount = 10_000,
                curOldest = 50_000, curHead = 50_000, count = 10_000
            )
        )
    }

    @Test
    fun olderDataTriggersResample() {
        // A new older ID appeared (oldest moved earlier) -> re-sample to cover it, even when head
        // and count haven't crossed their thresholds. This is what keeps dates correct as history
        // is fetched older.
        assertTrue(
            HistoryLogAnchorManager.shouldSample(
                prevOldest = 100_000, prevHead = 200_000, prevCount = 10_000,
                curOldest = 90_000, curHead = 200_000, count = 10_000
            )
        )
    }
}
