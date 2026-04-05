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

    fun exportCsv(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val csv = StringBuilder()
            csv.append("JAN,商品名,メーカー名,規格,スキャン日時,完了\n")
            _items.value.forEach { info ->
                val time = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.JAPAN).format(java.util.Date(info.scanItem.scannedAt))
                val status = if (info.isVisible) "未済" else "済"
                csv.append("${info.scanItem.scannedBarcode},${info.product?.productName ?: ""},${info.product?.makerName ?: ""},${info.product?.spec ?: ""},$time,$status\n")
            }
            
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csv.toString().toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteItem(info: OrderItemInfo) {
        viewModelScope.launch {
            scanRepository.deleteItem(info.scanItem)
            // After delete, the Flow from repository will trigger update if collected correctly.
            // But here loadSession uses combine which collects perpetually. 
            // So it should just work.
        }
    }
}
