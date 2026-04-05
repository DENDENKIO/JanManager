package com.example.janmanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "maker_cache")
data class MakerCache(
    @PrimaryKey
    @ColumnInfo(name = "maker_jan_prefix")
    val makerJanPrefix: String,
    
    @ColumnInfo(name = "maker_name")
    val makerName: String,
    
    @ColumnInfo(name = "maker_name_kana")
    val makerNameKana: String
)
