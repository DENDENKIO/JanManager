package com.example.janmanager.data.repository

import com.example.janmanager.data.local.dao.MakerCacheDao
import com.example.janmanager.data.local.dao.PackageUnitDao
import com.example.janmanager.data.local.dao.ProductMasterDao
import com.example.janmanager.data.local.entity.MakerCache
import com.example.janmanager.data.local.entity.PackageUnit
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.local.entity.ProductStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductMasterDao,
    private val makerCacheDao: MakerCacheDao,
    private val packageUnitDao: PackageUnitDao
) {
    suspend fun insertProduct(product: ProductMaster): Long {
        return productDao.insert(product)
    }

    suspend fun updateProduct(product: ProductMaster) {
        productDao.update(product)
    }

    suspend fun deleteProduct(product: ProductMaster) {
        productDao.delete(product)
    }

    suspend fun getProductByJan(janCode: String): ProductMaster? {
        return productDao.getProductByJan(janCode)
    }

    suspend fun getProductById(id: Long): ProductMaster? {
        return productDao.getProductById(id)
    }

    fun searchProducts(query: String, type: SearchType): Flow<List<ProductMaster>> {
        return when (type) {
            SearchType.JAN -> productDao.searchByJanCode(query)
            SearchType.NAME_KANA -> productDao.searchByProductNameKana(query)
            SearchType.MAKER -> productDao.searchByMakerName(query)
            SearchType.SPEC -> productDao.searchBySpec(query)
        }
    }

    fun getUnfetchedProducts(): Flow<List<ProductMaster>> {
        return productDao.getUnfetchedProducts()
    }

    fun getProductsByStatus(status: ProductStatus): Flow<List<ProductMaster>> {
        return productDao.getProductsByStatus(status)
    }

    suspend fun setProductDiscontinued(janCode: String) {
        productDao.getProductByJan(janCode)?.let {
            productDao.update(it.copy(status = ProductStatus.DISCONTINUED, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun linkRenewal(oldJan: String, newJan: String) {
        val oldProduct = productDao.getProductByJan(oldJan)
        val newProduct = productDao.getProductByJan(newJan)

        if (oldProduct != null) {
            productDao.update(oldProduct.copy(renewedToJan = newJan, status = ProductStatus.RENEWED, updatedAt = System.currentTimeMillis()))
        }
        if (newProduct != null) {
            productDao.update(newProduct.copy(renewedFromJan = oldJan, updatedAt = System.currentTimeMillis()))
        }
    }

    // Maker Cache ops
    suspend fun cacheMaker(prefix: String, name: String, kana: String) {
        makerCacheDao.insert(MakerCache(prefix, name, kana))
    }

    suspend fun getMakerByPrefix(prefix: String): MakerCache? {
        return makerCacheDao.getByPrefix(prefix)
    }

    // PackageOps
    suspend fun addPackageUnit(unit: PackageUnit): Long {
        return packageUnitDao.insert(unit)
    }

    fun getPackageUnits(productId: Long): Flow<List<PackageUnit>> {
        return packageUnitDao.getPackageUnitsForProduct(productId)
    }
    
    suspend fun getPackageUnitByBarcode(barcode: String): PackageUnit? {
        return packageUnitDao.getPackageUnitByBarcode(barcode)
    }

    suspend fun deletePackageUnit(unit: PackageUnit) {
        packageUnitDao.delete(unit)
    }

    suspend fun deleteAllProducts() {
        productDao.deleteAll()
    }
}

enum class SearchType {
    JAN, NAME_KANA, MAKER, SPEC
}
