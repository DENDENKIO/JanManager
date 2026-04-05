package com.example.janmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.janmanager.data.local.converter.Converters
import com.example.janmanager.data.local.dao.MakerCacheDao
import com.example.janmanager.data.local.dao.PackageUnitDao
import com.example.janmanager.data.local.dao.ProductGroupDao
import com.example.janmanager.data.local.dao.ProductGroupItemDao
import com.example.janmanager.data.local.dao.ProductMasterDao
import com.example.janmanager.data.local.dao.ScanItemDao
import com.example.janmanager.data.local.dao.ScanSessionDao
import com.example.janmanager.data.local.entity.MakerCache
import com.example.janmanager.data.local.entity.PackageUnit
import com.example.janmanager.data.local.entity.ProductGroup
import com.example.janmanager.data.local.entity.ProductGroupItem
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.local.entity.ScanItem
import com.example.janmanager.data.local.entity.ScanSession

@Database(
    entities = [
        ProductMaster::class,
        PackageUnit::class,
        MakerCache::class,
        ScanSession::class,
        ScanItem::class,
        ProductGroup::class,
        ProductGroupItem::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productMasterDao(): ProductMasterDao
    abstract fun packageUnitDao(): PackageUnitDao
    abstract fun makerCacheDao(): MakerCacheDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun scanItemDao(): ScanItemDao
    abstract fun productGroupDao(): ProductGroupDao
    abstract fun productGroupItemDao(): ProductGroupItemDao
}
