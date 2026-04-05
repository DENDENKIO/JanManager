package com.example.janmanager.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.local.entity.ScanItem
import com.example.janmanager.data.local.entity.ScanSession
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderItemInfo(
    val scanItem: ScanItem,
    val product: ProductMaster?,
    val isVisible: Boolean = true
)

@HiltViewModel
class OrderListViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _session = MutableStateFlow<ScanSession?>(null)
    val session = _session.asStateFlow()

    private val _items = MutableStateFlow<List<OrderItemInfo>>(emptyList())
    val items = _items.asStateFlow()

    private val _hiddenBarcodes = MutableStateFlow<Set<Long>>(emptySet())
    val hiddenBarcodes = _hiddenBarcodes.asStateFlow()

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            _session.value = scanRepository.getSessionById(sessionId)
            
            combine(
                scanRepository.getItemsForSession(sessionId),
                _hiddenBarcodes
            ) { items, hiddenSet ->
                items.map { item ->
                    val product = productRepository.getProductById(item.productId)
                    OrderItemInfo(
                        scanItem = item,
                        product = product,
                        isVisible = !hiddenSet.contains(item.id)
                    )
                }
            }.collect { infoList ->
                _items.value = infoList
            }
        }
    }

    fun toggleBarcodeVisibility(itemId: Long) {
        val current = _hiddenBarcodes.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _hiddenBarcodes.value = current
    }

    fun getProgress(): Pair<Int, Int> {
        val total = _items.value.size
        val hidden = _hiddenBarcodes.value.size
        return hidden to total
    }
}
