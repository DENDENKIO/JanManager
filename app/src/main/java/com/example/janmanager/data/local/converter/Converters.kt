package com.example.janmanager.data.local.converter

import androidx.room.TypeConverter
import com.example.janmanager.data.local.entity.BarcodeType
import com.example.janmanager.data.local.entity.InfoSource
import com.example.janmanager.data.local.entity.PackageType
import com.example.janmanager.data.local.entity.ProductStatus
import com.example.janmanager.data.local.entity.SessionStatus

class Converters {
    @TypeConverter
    fun fromProductStatus(value: ProductStatus): String = value.name

    @TypeConverter
    fun toProductStatus(value: String): ProductStatus = enumValueOf(value)

    @TypeConverter
    fun fromPackageType(value: PackageType): String = value.name

    @TypeConverter
    fun toPackageType(value: String): PackageType = enumValueOf(value)

    @TypeConverter
    fun fromBarcodeType(value: BarcodeType): String = value.name

    @TypeConverter
    fun toBarcodeType(value: String): BarcodeType = enumValueOf(value)

    @TypeConverter
    fun fromInfoSource(value: InfoSource): String = value.name

    @TypeConverter
    fun toInfoSource(value: String): InfoSource = enumValueOf(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus): String = value.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = enumValueOf(value)
}
