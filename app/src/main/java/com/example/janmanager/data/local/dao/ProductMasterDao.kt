package com.example.janmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.local.entity.ProductStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductMasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductMaster): Long

    @Update
    suspend fun update(product: ProductMaster)

    @Delete
    suspend fun delete(product: ProductMaster)

    @Query("SELECT * FROM product_master ORDER BY id DESC")
    fun getAllProducts(): Flow<List<ProductMaster>>

    @Query("SELECT * FROM product_master WHERE id = :id")
    suspend fun getProductById(id: Long): ProductMaster?

    @Query("SELECT * FROM product_master WHERE jan_code = :janCode LIMIT 1")
    suspend fun getProductByJan(janCode: String): ProductMaster?

    @Query("SELECT * FROM product_master WHERE jan_code LIKE :query || '%' OR jan_code = :query ORDER BY jan_code ASC")
    fun searchByJanCode(query: String): Flow<List<ProductMaster>>

    @Query("SELECT * FROM product_master WHERE product_name_kana LIKE '%' || :query || '%' ORDER BY product_name_kana ASC")
    fun searchByProductNameKana(query: String): Flow<List<ProductMaster>>

    @Query("SELECT * FROM product_master WHERE maker_name LIKE '%' || :query || '%' OR maker_name_kana LIKE '%' || :query || '%' ORDER BY maker_name ASC")
    fun searchByMakerName(query: String): Flow<List<ProductMaster>>

    @Query("SELECT * FROM product_master WHERE spec LIKE '%' || :query || '%' ORDER BY id DESC")
    fun searchBySpec(query: String): Flow<List<ProductMaster>>

    @Query("SELECT * FROM product_master WHERE status = :status ORDER BY id DESC")
    fun getProductsByStatus(status: ProductStatus): Flow<List<ProductMaster>>

    @Query("SELECT * FROM product_master WHERE info_fetched = 0 ORDER BY id ASC")
    fun getUnfetchedProducts(): Flow<List<ProductMaster>>
}
