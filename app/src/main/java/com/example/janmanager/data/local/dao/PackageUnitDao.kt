package com.example.janmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.janmanager.data.local.entity.PackageUnit
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageUnitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(packageUnit: PackageUnit): Long

    @Update
    suspend fun update(packageUnit: PackageUnit)

    @Delete
    suspend fun delete(packageUnit: PackageUnit)

    @Query("SELECT * FROM package_unit WHERE product_id = :productId ORDER BY quantity_per_unit ASC")
    fun getPackageUnitsForProduct(productId: Long): Flow<List<PackageUnit>>
    
    @Query("SELECT * FROM package_unit WHERE barcode = :barcode LIMIT 1")
    suspend fun getPackageUnitByBarcode(barcode: String): PackageUnit?
}
