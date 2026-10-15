package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName
import com.jwoglom.controlx2.sync.nightscout.NightscoutTimestampPolicy
import java.time.LocalDateTime

/**
 * Nightscout treatment (bolus, basal, carbs, etc.)
 * API endpoint: /api/v1/treatments
 */
data class NightscoutTreatment(
    @SerializedName("eventType")
    val eventType: String,  // "Bolus", "Temp Basal", "Site Change", etc.

    @SerializedName("created_at")
    val createdAt: String,  // RFC3339/ISO instant in UTC (Z)

    @SerializedName("timestamp")
    val timestamp: Long,  // Unix timestamp in milliseconds

    @SerializedName("utcOffset")
    val utcOffset: Int,  // Minutes offset for pump-local wall time (e.g. -420)

    @SerializedName("insulin")
    val insulin: Double? = null,  // Insulin in units

    @SerializedName("carbs")
    val carbs: Double? = null,  // Carbs in grams

    @SerializedName("duration")
    val duration: Int? = null,  // Duration in minutes (for temp basal)

    @SerializedName("rate")
    val rate: Double? = null,  // Basal rate in U/hr

    @SerializedName("absolute")
    val absolute: Double? = null,  // Absolute basal rate in U/hr

    @SerializedName("reason")
    val reason: String? = null,  // Reason for treatment

    @SerializedName("automatic")
    val automatic: Boolean? = null,  // Whether the basal was algorithm-driven (Control-IQ): true=Algorithm origin, false=Manual origin

    @SerializedName("notes")
    val notes: String? = null,  // Additional notes

    @SerializedName("enteredBy")
    val enteredBy: String = "ControlX2",

    // Nocturne keys/splits its entity store by data_source; absent it basals render under the
    // wrong series and combo/dual boluses carry no stable key to upsert on. Default covers every
    // processor, which all build via fromTimestamp().
    @SerializedName("data_source")
    val dataSource: String = "ControlX2",

    @SerializedName("_id")
    val id: String? = null,  // Nightscout-assigned ID (on read)

    @SerializedName("id")
    val clientTreatmentId: String? = null,  // NightScout client idempotency key (JSON "id"); nocturne ignores lowercase "id"

    // Loop/Nocturne idempotency key. Nocturne maps this to entity identity (unlike lowercase "id",
    // which it deliberately ignores), so re-uploading the same seqId upserts instead of inserting a
    // duplicate combo/dual bolus. Set from seqId in fromTimestamp().
    @SerializedName("syncIdentifier")
    val syncIdentifier: String? = null,

    @SerializedName("device")
    val device: String = "ControlX2",

    // Combo/extended bolus fields (per Nightscout "Combo Bolus" eventType)
    @SerializedName("enteredinsulin")
    val enteredInsulin: Double? = null,  // Total insulin (immediate + extended)
    // Total programmed units. Nocturne's Bolus.Programmed drives the displayed combo dose;
    // without it a combo bolus records null and shows only the immediate portion.
    @SerializedName("programmed")
    val programmed: Double? = null,

    @SerializedName("splitNow")
    val splitNow: Int? = null,  // Percentage delivered immediately (0-100)

    @SerializedName("splitExt")
    val splitExt: Int? = null,  // Percentage delivered over extended period (0-100)

    // Nocturne bolus kind: "normal", "square" (pure extended), or "dual" (combo). Maps to
    // nocturne's BolusType (ParseBolusType); absent it renders as a plain bolus, no combo marker.
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("relative")
    val relative: Double? = null,  // Extended portion rate in U/hr

    @SerializedName("percent")
    val percent: Int? = null,  // Temp basal percent change from profile (-50 = half, 0 = normal, 100 = double)

    // Echoed to nocturne as additionalProperties.basalOrigin so a Profile-sourced (scheduled)
    // basal renders as the Scheduled basal line, not the Manual (blue) temp bar. Null when unset.
    @SerializedName("additionalProperties")
    val additionalProperties: Map<String, String>? = null
 ) {
    companion object {
        fun fromTimestamp(
            eventType: String,
            timestamp: LocalDateTime,
            seqId: Long,
            syncIdentifier: String? = null,
            insulin: Double? = null,
            carbs: Double? = null,
            duration: Int? = null,
            rate: Double? = null,
            absolute: Double? = null,
            reason: String? = null,
            automatic: Boolean? = null,
            notes: String? = null,
            enteredInsulin: Double? = null,
            programmed: Double? = null,
            splitNow: Int? = null,
            splitExt: Int? = null,
            type: String? = null,
            relative: Double? = null,
            percent: Int? = null,
            additionalProperties: Map<String, String>? = null
        ): NightscoutTreatment {
            // Nightscout expects timezone-aware timestamps and matching epoch/offset from one instant.
            val nightscoutTimestamp = NightscoutTimestampPolicy.fromPumpTime(timestamp, "treatment")
            return NightscoutTreatment(
                eventType = eventType,
                createdAt = nightscoutTimestamp.isoInstant,
                timestamp = nightscoutTimestamp.epochMillis,
                utcOffset = nightscoutTimestamp.utcOffsetMinutes,
                insulin = insulin,
                carbs = carbs,
                duration = duration,
                rate = rate,
                absolute = absolute,
                reason = reason,
                automatic = automatic,
                notes = notes,
                clientTreatmentId = seqId.toString(),
                syncIdentifier = syncIdentifier ?: seqId.toString(),
                enteredInsulin = enteredInsulin,
                programmed = programmed,
                splitNow = splitNow,
                splitExt = splitExt,
                type = type,
                relative = relative,
                percent = percent,
                additionalProperties = additionalProperties
            )
        }
    }
}
