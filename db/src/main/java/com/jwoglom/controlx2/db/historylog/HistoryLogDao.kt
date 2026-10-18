package com.jwoglom.controlx2.db.historylog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class HistoryLogTypeStats(
    val typeId: Int,
    val count: Long,
    val latestSeqId: Long?,
    val latestPumpTimeSec: Long?
)
data class SeqIdPumpTime(
    val seqId: Long,
    val pumpTimeSec: Long
)
data class HistoryLogSeqRange(
    val minSeqId: Long?,
    val maxSeqId: Long?,
)

@Dao
interface HistoryLogDao {
    @Query("""
        SELECT COUNT(seqId) FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
    """)
    fun getCount(pumpSid: Int): Flow<Long?>

    @Query("""
        SELECT COUNT(seqId) FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId >= :min
        AND seqId <= :max
    """)
    fun getCount(pumpSid: Int, min: Long, max: Long): Flow<Long?>

    @Query("""
        SELECT COUNT(seqId) FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId >= :minSeqId
    """)
    fun getCountAboveSeqId(pumpSid: Int, minSeqId: Long): Flow<Long?>
    /** Min/max seqId present for [pumpSid] — the DB's actual history span, for anchor sampling. */
    @Query("SELECT MIN(seqId) AS minSeqId, MAX(seqId) AS maxSeqId FROM $HistoryLogTable WHERE pumpSid = :pumpSid")
    fun getSeqRange(pumpSid: Int): Flow<HistoryLogSeqRange?>

    @Query("""
        SELECT DISTINCT seqId
        FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId >= :min
        AND seqId <= :max
        ORDER BY seqId ASC
    """)
    fun getAllIds(pumpSid: Int, min: Long, max: Long): List<Long>

    // Closest real seqId at or below [target] with its pump clock. The anchor sampler uses this to
    // record a dated reference even when the exact sample ID is missing (retention gaps).
    @Query("SELECT seqId AS seqId, pumpTimeSec AS pumpTimeSec FROM $HistoryLogTable WHERE pumpSid = :pumpSid AND seqId <= :target ORDER BY seqId DESC LIMIT 1")
    fun getClosestAtOrBelow(pumpSid: Int, target: Long): SeqIdPumpTime?

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        ORDER BY seqId ASC
    """)
    fun getAll(pumpSid: Int): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId = :typeId
        ORDER BY seqId ASC
    """)
    fun getAllForType(pumpSid: Int, typeId: Int): Flow<List<HistoryLogItem>>


    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId = :typeId
        ORDER BY seqId DESC
        LIMIT :maxItems
    """)
    fun getLatestItemsForType(pumpSid: Int, typeId: Int, maxItems: Int): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId IN(:typeIds)
        ORDER BY seqId DESC
        LIMIT :maxItems
    """)
    fun getLatestItemsForTypes(pumpSid: Int, typeIds: List<Int>, maxItems: Int): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId BETWEEN :seqIdMin AND :seqIdMax
        ORDER BY seqId ASC
    """)
    fun getRange(pumpSid: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId = :typeId
        AND seqId BETWEEN :seqIdMin AND :seqIdMax
        ORDER BY seqId ASC
    """)
    fun getRangeForType(pumpSid: Int, typeId: Int, seqIdMin: Long, seqIdMax: Long): Flow<List<HistoryLogItem>>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND seqId = (
            SELECT MAX(seqId) FROM $HistoryLogTable
            WHERE pumpSid = :pumpSid
        )
        LIMIT 1
    """)
    fun getLatest(pumpSid: Int): Flow<HistoryLogItem?>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId = :typeId
        ORDER BY seqId DESC
        LIMIT 1
    """)
    fun getLatestForType(pumpSid: Int, typeId: Int): Flow<HistoryLogItem?>

    @Query("""
        SELECT typeId AS typeId, COUNT(*) AS count, MAX(seqId) AS latestSeqId, MAX(pumpTimeSec) AS latestPumpTimeSec
        FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        GROUP BY typeId
    """)
    fun getTypeStats(pumpSid: Int): List<HistoryLogTypeStats>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        ORDER BY seqId ASC
        LIMIT 1
    """)
    fun getOldest(pumpSid: Int): Flow<HistoryLogItem?>

    @Query("""
        SELECT * FROM $HistoryLogTable
        WHERE pumpSid = :pumpSid
        AND typeId IN(:typeIds)
        AND pumpTimeSec >= :minPumpTimeSec
        ORDER BY seqId ASC
    """)
    fun getItemsForTypesSince(pumpSid: Int, typeIds: List<Int>, minPumpTimeSec: Long): Flow<List<HistoryLogItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(historyLogItem: HistoryLogItem)

    @Query("DELETE FROM $HistoryLogTable")
    suspend fun deleteAll()
}