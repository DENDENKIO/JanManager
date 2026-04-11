package com.example.janmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_scan_history")
data class OcrScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val janCode: String,
    val productName: String,  // DB に商品なければ空文字 ""
    val scannedAt: Long       // System.currentTimeMillis()
)
