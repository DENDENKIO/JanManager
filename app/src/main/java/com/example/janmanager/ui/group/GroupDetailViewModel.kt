package com.example.janmanager.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductGroup
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.GroupRepository
import com.example.janmanager.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupProductInfo(
    val janCode: String,
    val productName: String,
    val makerName: String,
    val spec: String,
    val addedAt: Long
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _group = MutableStateFlow<ProductGroup?>(null)
    val group: StateFlow<ProductGroup?> = _group.asStateFlow()

    private val _items = MutableStateFlow<List<GroupProductInfo>>(emptyList())
    val items: StateFlow<List<GroupProductInfo>> = _items.asStateFlow()

    fun loadGroup(groupId: Long) {
        viewModelScope.launch {
            _group.value = groupRepository.getGroupById(groupId)
            
            groupRepository.getGroupItems(groupId).collect { items ->
                val infoList = items.map { item ->
                    val product = productRepository.getProductByJan(item.janCode)
                    GroupProductInfo(
                        janCode = item.janCode,
                        productName = product?.productName ?: "不明",
                        makerName = product?.makerName ?: "",
                        spec = product?.spec ?: "",
                        addedAt = item.addedAt
                    )
                }
                _items.value = infoList
            }
        }
    }
}
