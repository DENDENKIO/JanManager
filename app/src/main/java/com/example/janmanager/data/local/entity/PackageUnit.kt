package com.example.janmanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "package_unit",
    foreignKeys = [
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("product_id")
    ]
)
data class PackageUnit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "product_id")
    val productId: Long,
    
    @ColumnInfo(name = "barcode")
    val barcode: String,
    
    @ColumnInfo(name = "barcode_type")
    val barcodeType: BarcodeType,
    
    @ColumnInfo(name = "package_type")
    val packageType: PackageType,
    
    @ColumnInfo(name = "package_label")
    val packageLabel: String?,
    
    @ColumnInfo(name = "quantity_per_unit")
    val quantityPerUnit: Int
)
