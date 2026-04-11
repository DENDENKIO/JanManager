package com.example.janmanager.data.local.dao

import androidx.room.*
import com.example.janmanager.data.local.entity.OcrScanHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: OcrScanHistory)

    @Query("SELECT * FROM ocr_scan_history ORDER BY scannedAt DESC")
    fun getAll(): Flow<List<OcrScanHistory>>

    @Query("DELETE FROM ocr_scan_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM ocr_scan_history")
    suspend fun deleteAll()
}
