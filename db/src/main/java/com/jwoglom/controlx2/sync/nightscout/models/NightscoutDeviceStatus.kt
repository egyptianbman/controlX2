package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName
import com.jwoglom.controlx2.sync.nightscout.NightscoutTimestampPolicy
import java.time.LocalDateTime

/**
 * Nocturne V4 pump snapshot.
 *
 * Uploaded via POST /api/v4/device-status/pump as a one-element array. Flat shape (no nested pump
 * object): reservoir, batteryPercent, clock, iob, pumpStatus, pumpMode, bolusing, suspended plus
 * the identity fields [dataSource] and [syncIdentifier] that make the upsert idempotent.
 */
data class NightscoutDeviceStatus(
    @SerializedName("timestamp")
    val timestamp: String,

    @SerializedName("dataSource")
    val dataSource: String = "ControlX2",

    @SerializedName("syncIdentifier")
    val syncIdentifier: String,

    @SerializedName("reservoir")
    val reservoir: Double? = null,

    @SerializedName("batteryPercent")
    val batteryPercent: Int? = null,

    @SerializedName("clock")
    val clock: String? = null,

    @SerializedName("iob")
    val iob: Double? = null,

    @SerializedName("pumpStatus")
    val pumpStatus: String? = null,

    @SerializedName("pumpMode")
    val pumpMode: String? = null,

    @SerializedName("bolusing")
    val bolusing: Boolean? = null,

    @SerializedName("suspended")
    val suspended: Boolean? = null
)

/**
 * Build a flat Nocturne V4 pump snapshot from the accumulated device status.
 *
 * [syncIdentifier] is derived deterministically from the instant so a retry of the same sync
 * cycle upserts the existing snapshot instead of inserting a duplicate.
 */
fun createDeviceStatus(
    timestamp: LocalDateTime,
    batteryPercent: Int? = null,
    reservoirUnits: Double? = null,
    iob: Double? = null,
    pumpStatus: String? = null,
    pumpMode: String? = null,
    suspended: Boolean? = null,
    bolusing: Boolean? = null
): NightscoutDeviceStatus {
    // Nightscout expects timezone-aware timestamps and matching offset from the same instant.
    val nightscoutTimestamp = NightscoutTimestampPolicy.fromPumpTime(timestamp, "devicestatus")
    return NightscoutDeviceStatus(
        timestamp = nightscoutTimestamp.isoInstant,
        dataSource = "ControlX2",
        syncIdentifier = nightscoutTimestamp.isoInstant,
        reservoir = reservoirUnits,
        batteryPercent = batteryPercent,
        clock = nightscoutTimestamp.isoInstant,
        iob = iob,
        pumpStatus = pumpStatus,
        pumpMode = pumpMode,
        bolusing = bolusing,
        suspended = suspended
    )
}
