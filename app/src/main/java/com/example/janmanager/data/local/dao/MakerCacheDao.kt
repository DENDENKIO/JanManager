package com.example.janmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.janmanager.data.local.entity.MakerCache
import kotlinx.coroutines.flow.Flow

@Dao
interface MakerCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(makerCache: MakerCache)

    @Update
    suspend fun update(makerCache: MakerCache)

    @Delete
    suspend fun delete(makerCache: MakerCache)

    @Query("SELECT * FROM maker_cache WHERE maker_jan_prefix = :prefix LIMIT 1")
    suspend fun getByPrefix(prefix: String): MakerCache?
    
    @Query("SELECT * FROM maker_cache ORDER BY maker_name ASC")
    fun getAllMakers(): Flow<List<MakerCache>>
}
