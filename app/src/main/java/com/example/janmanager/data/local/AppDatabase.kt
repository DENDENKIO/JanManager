package com.example.janmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.janmanager.data.local.converter.Converters
import com.example.janmanager.data.local.dao.MakerCacheDao
import com.example.janmanager.data.local.dao.PackageUnitDao
import com.example.janmanager.data.local.dao.OcrScanHistoryDao
import com.example.janmanager.data.local.dao.ProductGroupDao
import com.example.janmanager.data.local.dao.ProductGroupItemDao
import com.example.janmanager.data.local.dao.ProductMasterDao
import com.example.janmanager.data.local.dao.ScanItemDao
import com.example.janmanager.data.local.dao.ScanSessionDao
import com.example.janmanager.data.local.entity.MakerCache
import com.example.janmanager.data.local.entity.OcrScanHistory
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
        ProductGroupItem::class,
        OcrScanHistory::class
    ],
    version = 2,
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
    abstract fun ocrScanHistoryDao(): OcrScanHistoryDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ocr_scan_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                janCode TEXT NOT NULL,
                productName TEXT NOT NULL,
                scannedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
