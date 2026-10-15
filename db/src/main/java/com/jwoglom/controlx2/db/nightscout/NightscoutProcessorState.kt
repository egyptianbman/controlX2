package com.jwoglom.controlx2.db.nightscout

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = NightscoutProcessorStateTable)
data class NightscoutProcessorState(
    @PrimaryKey
    val processorType: String,
    val lastProcessedSeqId: Long,
    val lastSuccessTime: LocalDateTime? = null,
    // Monotonic cursor over Tandem pump bolus IDs. Combo/dual boluses arrive as many history-log
    // entries (one per extended-delivery update) sharing a single pump bolusID; this tracks the
    // highest ID already uploaded so later updates are skipped and only the start entry is sent.
    val maxProcessedBolusId: Int = 0
)

const val NightscoutProcessorStateTable = "nightscout_processor_state"
