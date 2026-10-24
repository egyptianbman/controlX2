package com.jwoglom.controlx2.sync.nightscout.api

import com.jwoglom.controlx2.sync.nightscout.models.NightscoutDeviceStatus
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutAnnouncement
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutEntry
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutProfile
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutTreatment
import com.jwoglom.controlx2.sync.nightscout.models.NightscoutStateSpan

/**
 * Nightscout REST API interface
 *
 * Note: This is a basic implementation using HttpURLConnection.
 * In the future, this could be replaced with Retrofit for more robust HTTP handling.
 */
interface NightscoutApi {
    /**
     * Invoked after each upload chunk is posted, with the item count in that chunk.
     *
     * Drives the Nightscout sync progress bar in log-granularity steps: one call per ~chunk-size
     * items, so a 40k-log sync advances ~100 at a time instead of jumping a whole processor's
     * worth at once. Optional cross-cutting hook -- the coordinator sets it for a sync run and
     * clears it after. Mocks leave it null (no-op), so they need no changes.
     */
    var onChunkProgress: ((items: Int) -> Unit)?
        get() = null
        set(value) {}

    /**
     * Upload CGM entries to Nightscout
     * POST /api/v1/entries
     */
    suspend fun uploadEntries(entries: List<NightscoutEntry>): Result<Int>

    /**
     * Upload treatments to Nightscout
     * POST /api/v1/treatments
     */
    suspend fun uploadTreatments(treatments: List<NightscoutTreatment>): Result<Int>

    /**
     * Upload device status to Nightscout
     * POST /api/v1/devicestatus
     */
    suspend fun uploadDeviceStatus(status: NightscoutDeviceStatus): Result<Boolean>

    /**
     * Get last N entries from Nightscout
     * GET /api/v1/entries?count=N
     */
    suspend fun getLastEntries(count: Int = 1): Result<List<NightscoutEntry>>

    /**
     * Get last treatment of a specific event type
     * GET /api/v1/treatments?eventType=X&count=1
     */
    suspend fun getLastTreatment(eventType: String): Result<NightscoutTreatment?>

    /**
     * Upload profile to Nightscout
     * POST /api/v1/profile
     */
    suspend fun uploadProfile(profile: NightscoutProfile): Result<Boolean>

    /**
     * Get latest announcements
     * GET /api/v1/treatments?find[eventType]=Announcement&count=N
     */
    suspend fun getAnnouncements(count: Int = 10): Result<List<NightscoutAnnouncement>>

    /**
     * Get announcements after a point in time and/or id marker
     */
    suspend fun getAnnouncementsSince(
        sinceTimestamp: Long? = null,
        sinceId: String? = null,
        count: Int = 100
    ): Result<List<NightscoutAnnouncement>>

    /**
     * Fetch currently open PumpMode state spans.
     *
     * GET /api/v4/state-spans?category=PumpMode&active=true
     *
     * Response envelope: {"data":[...],"pagination":{...}}. Used before posting new mode spans to
     * close any span left open from a prior sync (cross-state handoff). On re-sync the last span is
     * still returned here, so callers must filter by startMills, not assume the list is empty.
     */
    suspend fun getActivePumpModeSpans(): Result<List<NightscoutStateSpan>>

    /**
     * Create (or upsert by originalId) a PumpMode state span.
     *
     * POST /api/v4/state-spans -> 201; response body is the created span (includes Nocturne [id]).
     * Each POST is self-contained with an explicit endMills. Requires the devices.readwrite
     * permission (separate from treatments.readwrite); a secret lacking it 403s.
     */
    suspend fun createStateSpan(span: NightscoutStateSpan): Result<NightscoutStateSpan>

    /**
     * Close an open PumpMode state span by setting its [endMills].
     *
     * PUT /api/v4/state-spans/{id} {"endMills": <millis>}. Idempotent: closing an already-closed
     * span is a harmless no-op.
     */
    suspend fun closeStateSpan(id: String, endMills: Long): Result<Boolean>
}
