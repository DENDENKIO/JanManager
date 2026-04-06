package com.example.janmanager.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.PackageType
import com.example.janmanager.data.local.entity.PackageUnit
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.util.JanCodeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val repository: ProductRepository
) : ViewModel() {

    private val _product = MutableStateFlow<ProductMaster?>(null)
    val product = _product.asStateFlow()

    private val _packages = MutableStateFlow<List<PackageUnit>>(emptyList())
    val packages = _packages.asStateFlow()

    fun loadProduct(janCode: String) {
        viewModelScope.launch {
            val loaded = repository.getProductByJan(janCode)
            _product.value = loaded
            if (loaded != null) {
                repository.getPackageUnits(loaded.id).collect {
                    _packages.value = it
                }
            }
        }
    }

    fun saveProductChanges(updated: ProductMaster) {
        viewModelScope.launch {
            repository.updateProduct(updated)
            _product.value = updated
        }
    }

    fun linkRenewalTarget(newJan: String) {
        viewModelScope.launch {
            val current = _product.value ?: return@launch
            repository.linkRenewal(current.janCode, newJan)
            // Reload
            loadProduct(current.janCode)
        }
    }

    fun restoreProductStatus() {
        viewModelScope.launch {
            val current = _product.value ?: return@launch
            repository.setProductActive(current.janCode)
            loadProduct(current.janCode)
        }
    }

    fun unlinkRenewal() {
        viewModelScope.launch {
            val current = _product.value ?: return@launch
            repository.unlinkRenewal(current.janCode)
            loadProduct(current.janCode)
        }
    }

    fun discontinueProduct() {
        viewModelScope.launch {
            val current = _product.value ?: return@launch
            repository.setProductDiscontinued(current.janCode)
            loadProduct(current.janCode)
        }
    }

    fun addPackageUnit(barcode: String, unitType: PackageType, quantity: Int) {
        viewModelScope.launch {
            val current = _product.value ?: return@launch
            val barcodeType = JanCodeUtil.detectCodeType(barcode)
            val newUnit = PackageUnit(
                productId = current.id,
                barcode = barcode,
                barcodeType = barcodeType,
                packageType = unitType,
                packageLabel = null,
                quantityPerUnit = quantity
            )
            repository.addPackageUnit(newUnit)
        }
    }

    fun deletePackageUnit(unit: PackageUnit) {
        viewModelScope.launch {
            repository.deletePackageUnit(unit)
        }
    }
}
