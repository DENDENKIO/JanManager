package com.example.janmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.janmanager.data.local.entity.ProductGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: ProductGroup): Long

    @Update
    suspend fun update(group: ProductGroup)

    @Delete
    suspend fun delete(group: ProductGroup)

    @Query("SELECT * FROM product_group WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: Long): ProductGroup?

    @Query("SELECT * FROM product_group ORDER BY created_at DESC")
    fun getAllGroups(): Flow<List<ProductGroup>>

    @Query("SELECT * FROM product_group WHERE is_active = :isActive ORDER BY created_at DESC")
    fun getGroupsByActiveStatus(isActive: Boolean): Flow<List<ProductGroup>>
    
    @Query("UPDATE product_group SET is_active = 0 WHERE is_active = 1 AND end_date < :currentTimeMillis")
    suspend fun deactivateExpiredGroups(currentTimeMillis: Long): Int
}
