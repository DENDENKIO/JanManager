package com.example.janmanager.data.repository

import com.example.janmanager.data.local.dao.OcrScanHistoryDao
import com.example.janmanager.data.local.entity.OcrScanHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrScanHistoryRepository @Inject constructor(
    private val dao: OcrScanHistoryDao
) {
    fun getAll(): Flow<List<OcrScanHistory>> = dao.getAll()
    suspend fun insert(history: OcrScanHistory) = dao.insert(history)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
}
