package com.example.janmanager.di

import android.content.Context
import androidx.room.Room
import com.example.janmanager.data.local.AppDatabase
import com.example.janmanager.data.local.MIGRATION_1_2
import com.example.janmanager.data.local.dao.MakerCacheDao
import com.example.janmanager.data.local.dao.OcrScanHistoryDao
import com.example.janmanager.data.local.dao.PackageUnitDao
import com.example.janmanager.data.local.dao.ProductGroupDao
import com.example.janmanager.data.local.dao.ProductGroupItemDao
import com.example.janmanager.data.local.dao.ProductMasterDao
import com.example.janmanager.data.local.dao.ScanItemDao
import com.example.janmanager.data.local.dao.ScanSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "jan_manager.db"
        ).addMigrations(MIGRATION_1_2).build()
    }

    @Provides
    fun provideProductMasterDao(db: AppDatabase): ProductMasterDao = db.productMasterDao()

    @Provides
    fun providePackageUnitDao(db: AppDatabase): PackageUnitDao = db.packageUnitDao()

    @Provides
    fun provideMakerCacheDao(db: AppDatabase): MakerCacheDao = db.makerCacheDao()

    @Provides
    fun provideScanSessionDao(db: AppDatabase): ScanSessionDao = db.scanSessionDao()

    @Provides
    fun provideScanItemDao(db: AppDatabase): ScanItemDao = db.scanItemDao()

    @Provides
    fun provideProductGroupDao(db: AppDatabase): ProductGroupDao = db.productGroupDao()

    @Provides
    fun provideProductGroupItemDao(db: AppDatabase): ProductGroupItemDao = db.productGroupItemDao()

    @Provides
    fun provideOcrScanHistoryDao(db: AppDatabase): OcrScanHistoryDao = db.ocrScanHistoryDao()
}
