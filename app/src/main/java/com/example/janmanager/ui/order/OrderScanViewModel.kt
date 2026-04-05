package com.example.janmanager.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductStatus
import com.example.janmanager.data.local.entity.ScanSession
import com.example.janmanager.data.local.entity.SessionStatus
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.repository.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderScanUiState(
    val currentSession: ScanSession? = null,
    val lastScannedJan: String = "",
    val lastProductName: String = "",
    val scanCount: Int = 0,
    val isDuplicate: Boolean = false,
    val isDiscontinued: Boolean = false,
    val errorMessage: String = ""
)

@HiltViewModel
class OrderScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderScanUiState())
    val uiState: StateFlow<OrderScanUiState> = _uiState.asStateFlow()

    init {
        // Find an open session or create a new one
        viewModelScope.launch {
            val openSessions = scanRepository.getSessionsByStatus(SessionStatus.OPEN).first()
            val session = if (openSessions.isNotEmpty()) {
                openSessions.first()
            } else {
                val newId = scanRepository.createSession("Session ${System.currentTimeMillis()}")
                scanRepository.getSessionById(newId)
            }
            
            session?.let {
                _uiState.value = _uiState.value.copy(currentSession = it)
                updateScanCount(it.id)
            }
        }
    }

    private fun updateScanCount(sessionId: Long) {
        viewModelScope.launch {
            scanRepository.getItemsForSession(sessionId).collect { items ->
                _uiState.value = _uiState.value.copy(scanCount = items.size)
            }
        }
    }

    fun processBarcode(barcode: String) {
        val session = _uiState.value.currentSession ?: return
        
        viewModelScope.launch {
            val product = productRepository.getProductByJan(barcode)
            val isDiscontinued = product?.status == ProductStatus.DISCONTINUED
            val isDuplicate = scanRepository.hasDuplicateJan(session.id, barcode)
            
            if (product != null) {
                scanRepository.addItemToSession(session.id, product.id, barcode)
                _uiState.value = _uiState.value.copy(
                    lastScannedJan = barcode,
                    lastProductName = product.productName,
                    isDuplicate = isDuplicate,
                    isDiscontinued = isDiscontinued,
                    errorMessage = ""
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "商品未登録: $barcode"
                )
            }
        }
    }

    fun completeSession() {
        viewModelScope.launch {
            _uiState.value.currentSession?.let { session ->
                scanRepository.updateSession(session.copy(status = SessionStatus.COMPLETED))
                _uiState.value = _uiState.value.copy(currentSession = null)
            }
        }
    }
}
