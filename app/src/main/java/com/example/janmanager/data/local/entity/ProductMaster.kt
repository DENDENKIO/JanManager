package com.example.janmanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_master",
    indices = [
        Index(value = ["jan_code"], unique = true)
    ]
)
data class ProductMaster(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "jan_code")
    val janCode: String,
    
    @ColumnInfo(name = "maker_jan_prefix")
    val makerJanPrefix: String,
    
    @ColumnInfo(name = "maker_name")
    val makerName: String,
    
    @ColumnInfo(name = "maker_name_kana")
    val makerNameKana: String,
    
    @ColumnInfo(name = "product_name")
    val productName: String,
    
    @ColumnInfo(name = "product_name_kana")
    val productNameKana: String,
    
    @ColumnInfo(name = "spec")
    val spec: String,
    
    @ColumnInfo(name = "status")
    val status: ProductStatus = ProductStatus.ACTIVE,
    
    @ColumnInfo(name = "renewed_from_jan")
    val renewedFromJan: String? = null,
    
    @ColumnInfo(name = "renewed_to_jan")
    val renewedToJan: String? = null,
    
    @ColumnInfo(name = "info_source")
    val infoSource: InfoSource = InfoSource.NONE,
    
    @ColumnInfo(name = "info_fetched")
    val infoFetched: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
