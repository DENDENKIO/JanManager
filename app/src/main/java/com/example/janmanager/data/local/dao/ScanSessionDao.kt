package com.example.janmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.janmanager.data.local.entity.ScanSession
import com.example.janmanager.data.local.entity.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ScanSession): Long

    @Update
    suspend fun update(session: ScanSession)

    @Delete
    suspend fun delete(session: ScanSession)

    @Query("SELECT * FROM scan_session WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ScanSession?

    @Query("SELECT * FROM scan_session ORDER BY id DESC")
    fun getAllSessions(): Flow<List<ScanSession>>

    @Query("SELECT * FROM scan_session WHERE status = :status ORDER BY id DESC")
    fun getSessionsByStatus(status: SessionStatus): Flow<List<ScanSession>>
}
