package com.jwoglom.controlx2.db.historylog

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

const val HistoryLogAnchorTable = "pumpdata_historylog_anchor"

/**
 * A sampled (seqId -> pump clock) reference point used to turn an arbitrary sequence ID into a date.
 * The background sampler records one of these every ~10,000 IDs across the fetched history; the UI
 * interpolates between them so the range slider can show human-readable dates without a query per ID.
 */
@Entity(
    tableName = HistoryLogAnchorTable,
    primaryKeys = ["pumpSid", "anchorId"]
)
class HistoryLogAnchorItem(
    val pumpSid: Int,
    val anchorId: Long,       // seqId at which pumpTimeSec was recorded
    val pumpTimeSec: Long     // pump clock (seconds since 2008-01-01) at that anchor
)

@Dao
interface HistoryLogAnchorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(anchors: List<HistoryLogAnchorItem>)

    @Query("SELECT * FROM $HistoryLogAnchorTable WHERE pumpSid = :pumpSid ORDER BY anchorId ASC")
    fun getAll(pumpSid: Int): List<HistoryLogAnchorItem>

    @Query("SELECT MIN(anchorId) FROM $HistoryLogAnchorTable WHERE pumpSid = :pumpSid")
    fun getMinAnchor(pumpSid: Int): Long?

    @Query("SELECT MAX(anchorId) FROM $HistoryLogAnchorTable WHERE pumpSid = :pumpSid")
    fun getMaxAnchor(pumpSid: Int): Long?

    @Query("SELECT COUNT(*) FROM $HistoryLogAnchorTable WHERE pumpSid = :pumpSid")
    fun count(pumpSid: Int): Long
}
