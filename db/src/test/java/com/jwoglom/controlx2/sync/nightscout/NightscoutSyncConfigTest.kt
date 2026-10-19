package com.jwoglom.controlx2.sync.nightscout

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NightscoutSyncConfig, focused on the includeNotes gate (buildNotes).
 *
 * includeNotes defaults off so Nocturne gets no notes until the user opts in; buildNotes is the
 * single place every processor routes notes through, so it must return null when disabled.
 */
class NightscoutSyncConfigTest {

    @Test
    fun includeNotesDefaultsOff() {
        val config = NightscoutSyncConfig()
        assertFalse(config.includeNotes)
    }

    @Test
    fun buildNotesReturnsNullWhenDisabled() {
        val config = NightscoutSyncConfig(includeNotes = false)
        assertNull(config.buildNotes { append("should not be built") })
    }

    @Test
    fun buildNotesReturnsStringWhenEnabled() {
        val config = NightscoutSyncConfig(includeNotes = true)
        assertEquals("rate: 1.5U/hr", config.buildNotes { append("rate: 1.5U/hr") })
    }

    @Test
    fun buildNotesBuildsMultiPartWhenEnabled() {
        val config = NightscoutSyncConfig(includeNotes = true)
        assertEquals(
            "Basal delivery, commanded rate: 1.5U/hr",
            config.buildNotes {
                append("Basal delivery")
                append(", commanded rate: 1.5U/hr")
            }
        )
    }
}
