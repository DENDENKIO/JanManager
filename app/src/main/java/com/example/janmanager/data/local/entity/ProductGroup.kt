package com.example.janmanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_group")
data class ProductGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "group_name")
    val groupName: String,
    
    @ColumnInfo(name = "tag_color")
    val tagColor: Int, // e.g. Color value or resource ID
    
    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,
    
    @ColumnInfo(name = "end_date")
    val endDate: Long,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "memo")
    val memo: String = "",
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
