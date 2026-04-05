package com.example.janmanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_group_item",
    foreignKeys = [
        ForeignKey(
            entity = ProductGroup::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductMaster::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("group_id"),
        Index("product_id")
    ]
)
data class ProductGroupItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "group_id")
    val groupId: Long,
    
    @ColumnInfo(name = "product_id")
    val productId: Long,
    
    @ColumnInfo(name = "jan_code")
    val janCode: String,
    
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
