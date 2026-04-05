package com.example.janmanager.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductGroup
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.GroupRepository
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.util.JanCodeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScannedGroupItem(
    val janCode: String,
    val productName: String,
    val isNew: Boolean,
    val alreadyInGroup: Boolean = false
)

@HiltViewModel
class GroupScanViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _group = MutableStateFlow<ProductGroup?>(null)
    val group: StateFlow<ProductGroup?> = _group.asStateFlow()

    private val _scannedItems = MutableStateFlow<List<ScannedGroupItem>>(emptyList())
    val scannedItems: StateFlow<List<ScannedGroupItem>> = _scannedItems.asStateFlow()

    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage.asStateFlow()

    fun loadGroup(groupId: Long) {
        viewModelScope.launch {
            _group.value = groupRepository.getGroupById(groupId)
        }
    }

    fun processBarcode(barcode: String) {
        val currentGroup = _group.value ?: return
        if (!currentGroup.isActive) {
            _lastMessage.value = "このグループは終了しています"
            return
        }

        val janCode = barcode // Assume it's already normalized or handled by the fragment/screen
        
        viewModelScope.launch {
            val product = productRepository.getProductByJan(janCode)
            
            if (product == null) {
                // Register as basic product first (Phase 2 requirement usually)
                val newProduct = ProductMaster(
                    janCode = janCode,
                    makerJanPrefix = JanCodeUtil.extractMakerPrefix(janCode),
                    makerName = "",
                    makerNameKana = "",
                    productName = "未登録商品",
                    productNameKana = "",
                    spec = ""
                )
                val id = productRepository.insertProduct(newProduct)
                addToGroup(currentGroup.id, id, janCode, "未登録商品", isNew = true)
            } else {
                addToGroup(currentGroup.id, product.id, janCode, product.productName, isNew = false)
            }
        }
    }

    private suspend fun addToGroup(groupId: Long, productId: Long, janCode: String, productName: String, isNew: Boolean) {
        val added = groupRepository.addProductToGroup(groupId, productId, janCode)
        
        val newItem = ScannedGroupItem(
            janCode = janCode,
            productName = productName,
            isNew = isNew,
            alreadyInGroup = !added
        )

        _scannedItems.value = (listOf(newItem) + _scannedItems.value).take(50)
        
        if (!added) {
            _lastMessage.value = "重複: $janCode は既に追加されています"
        } else {
            _lastMessage.value = "追加: $productName"
        }
    }
}
