package com.example.janmanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_item",
    foreignKeys = [
        ForeignKey(
            entity = ScanSession::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE // Assuming if product goes, scan item goes or NO_ACTION
        )
    ],
    indices = [
        Index("session_id"),
        Index("product_id")
    ]
)
data class ScanItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    
    @ColumnInfo(name = "product_id")
    val productId: Long,
    
    @ColumnInfo(name = "scanned_barcode")
    val scannedBarcode: String,
    
    @ColumnInfo(name = "scan_order")
    val scanOrder: Int,
    
    @ColumnInfo(name = "scanned_at")
    val scannedAt: Long = System.currentTimeMillis()
)
