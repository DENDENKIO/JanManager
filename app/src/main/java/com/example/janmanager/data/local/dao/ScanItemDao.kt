package com.example.janmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.janmanager.data.local.entity.ScanItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScanItem): Long

    @Update
    suspend fun update(item: ScanItem)

    @Delete
    suspend fun delete(item: ScanItem)

    @Query("SELECT * FROM scan_item WHERE session_id = :sessionId ORDER BY scan_order ASC")
    fun getItemsForSession(sessionId: Long): Flow<List<ScanItem>>

    @Query("SELECT EXISTS(SELECT 1 FROM scan_item WHERE session_id = :sessionId AND scanned_barcode = :barcode LIMIT 1)")
    suspend fun hasDuplicateJan(sessionId: Long, barcode: String): Boolean
    
    @Query("SELECT COALESCE(MAX(scan_order), 0) FROM scan_item WHERE session_id = :sessionId")
    suspend fun getMaxScanOrder(sessionId: Long): Int
}
