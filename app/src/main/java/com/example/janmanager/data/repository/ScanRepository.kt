package com.example.janmanager.data.repository

import com.example.janmanager.data.local.dao.ScanItemDao
import com.example.janmanager.data.local.dao.ScanSessionDao
import com.example.janmanager.data.local.entity.ScanItem
import com.example.janmanager.data.local.entity.ScanSession
import com.example.janmanager.data.local.entity.SessionStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val sessionDao: ScanSessionDao,
    private val itemDao: ScanItemDao
) {
    fun getAllSessions(): Flow<List<ScanSession>> = sessionDao.getAllSessions()

    fun getSessionsByStatus(status: SessionStatus): Flow<List<ScanSession>> = sessionDao.getSessionsByStatus(status)

    suspend fun getSessionById(id: Long): ScanSession? = sessionDao.getSessionById(id)

    suspend fun createSession(name: String): Long {
        return sessionDao.insert(ScanSession(sessionName = name))
    }

    suspend fun updateSession(session: ScanSession) = sessionDao.update(session)

    suspend fun deleteSession(session: ScanSession) = sessionDao.delete(session)

    fun getItemsForSession(sessionId: Long): Flow<List<ScanItem>> = itemDao.getItemsForSession(sessionId)

    suspend fun addItemToSession(sessionId: Long, productId: Long, barcode: String): Long {
        val order = itemDao.getMaxScanOrder(sessionId) + 1
        return itemDao.insert(ScanItem(sessionId = sessionId, productId = productId, scannedBarcode = barcode, scanOrder = order))
    }

    suspend fun hasDuplicateJan(sessionId: Long, barcode: String): Boolean {
        return itemDao.hasDuplicateJan(sessionId, barcode)
    }

    suspend fun deleteItem(item: ScanItem) {
        itemDao.delete(item)
    }
}
