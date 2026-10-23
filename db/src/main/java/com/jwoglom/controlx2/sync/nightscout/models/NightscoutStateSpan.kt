package com.jwoglom.controlx2.sync.nightscout.models

import com.google.gson.annotations.SerializedName

/**
 * Nocturne V4 state span (POST /api/v4/state-spans).
 *
 * Control-IQ mode changes are time-ranged states, not point-in-time treatments, so ControlX2
 * reports them as PumpMode spans. Each span has an explicit [startMills] and [endMills];
 * [endMills]=null means the state is still open. Nocturne addresses spans by [id] (UUID), captured
 * on POST/GET responses. [originalId] is our idempotency key: re-syncing the same record POSTs the
 * same originalId and Nocturne upserts in place rather than inserting a duplicate.
 */
data class NightscoutStateSpan(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("originalId")
    val originalId: String? = null,

    @SerializedName("category")
    val category: String,

    @SerializedName("state")
    val state: String,

    @SerializedName("startMills")
    val startMills: Long,

    @SerializedName("endMills")
    val endMills: Long? = null,

    @SerializedName("source")
    val source: String = "controlx2"
)
