package com.jwoglom.controlx2.db.historylog

import androidx.annotation.WorkerThread

/**
 * Persistence for the anchor table. The sampler writes anchors; the lookup reads them. The read
 * methods mirror HistoryLogRepo.getAllIds (non-suspend List); call them from a background thread.
 */
class HistoryLogAnchorRepo(private val anchorDao: HistoryLogAnchorDao) {

    @WorkerThread
    suspend fun insertAll(anchors: List<HistoryLogAnchorItem>) {
        anchorDao.insertAll(anchors)
    }

    @WorkerThread
    fun getAll(pumpSid: Int): List<HistoryLogAnchorItem> = anchorDao.getAll(pumpSid)

    @WorkerThread
    fun getMinAnchor(pumpSid: Int): Long? = anchorDao.getMinAnchor(pumpSid)

    @WorkerThread
    fun getMaxAnchor(pumpSid: Int): Long? = anchorDao.getMaxAnchor(pumpSid)

    @WorkerThread
    fun count(pumpSid: Int): Long = anchorDao.count(pumpSid)
}
