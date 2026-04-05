package com.example.janmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.janmanager.data.local.entity.ProductGroupItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductGroupItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ProductGroupItem): Long

    @Update
    suspend fun update(item: ProductGroupItem)

    @Delete
    suspend fun delete(item: ProductGroupItem)

    @Query("SELECT * FROM product_group_item WHERE group_id = :groupId ORDER BY added_at DESC")
    fun getItemsForGroup(groupId: Long): Flow<List<ProductGroupItem>>
    
    @Query("SELECT EXISTS(SELECT 1 FROM product_group_item WHERE group_id = :groupId AND jan_code = :janCode LIMIT 1)")
    suspend fun isItemInGroup(groupId: Long, janCode: String): Boolean
}
