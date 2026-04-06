package com.example.janmanager.ui.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.janmanager.data.local.entity.BarcodeType
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.local.entity.ProductStatus
import com.example.janmanager.data.local.entity.ScanSession
import com.example.janmanager.data.local.entity.SessionStatus
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.repository.ScanRepository
import com.example.janmanager.data.settings.SettingsDataStore
import com.example.janmanager.ui.navigation.Route
import com.example.janmanager.util.JanCodeUtil
import com.example.janmanager.util.SoundHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderScanUiState(
    val currentSession: ScanSession? = null,
    val lastScannedJan: String = "",
    val lastProductName: String = "",
    val scanCount: Int = 0,
    val isDuplicate: Boolean = false,
    val isDiscontinued: Boolean = false,
    val errorMessage: String = "",
    val pendingDiscontinuedProduct: ProductMaster? = null
)

@HiltViewModel
class OrderScanViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val productRepository: ProductRepository,
    private val settingsDataStore: SettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Retrieve sessionId from the route automatically
    private val routeData = savedStateHandle.toRoute<Route.OrderScan>()
    private val sessionId: Long = routeData.sessionId

    private val _uiState = MutableStateFlow(OrderScanUiState())
    val uiState: StateFlow<OrderScanUiState> = _uiState.asStateFlow()

    val isItfEnabled = settingsDataStore.isItfEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanSoundEnabled = settingsDataStore.scanSoundEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val session = scanRepository.getSessionById(sessionId)
            session?.let {
                _uiState.value = _uiState.value.copy(currentSession = it)
                updateScanCount(it.id)
            } ?: run {
                _uiState.value = _uiState.value.copy(errorMessage = "セッションが見つかりません ID: $sessionId")
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
        if (session.status == SessionStatus.COMPLETED) {
            _uiState.value = _uiState.value.copy(errorMessage = "完了済みのセッションには追加できません")
            return
        }
        
        viewModelScope.launch {
            val normalizedInput = com.example.janmanager.util.Normalizer.toHalfWidth(barcode).trim()
            if (normalizedInput.isEmpty()) return@launch

            // ITF 14-digit check
            val type = JanCodeUtil.detectCodeType(normalizedInput)
            if (type == BarcodeType.ITF14 && !isItfEnabled.value) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "ITFコードの読み取りは無効に設定されています"
                )
                return@launch
            }

            // 1. 商品検索 (優先順位: 商品マスタ直接 -> 包装単位 -> 新規仮作成)
            var product = productRepository.getProductByJan(normalizedInput)
            if (product == null) {
                val packUnit = productRepository.getPackageUnitByBarcode(normalizedInput)
                if (packUnit != null) {
                    product = productRepository.getProductById(packUnit.productId)
                }
            }

            // 見つからない場合は新商品として仮登録
            if (product == null) {
                val prefix = JanCodeUtil.extractMakerPrefix(normalizedInput)
                val makerCache = productRepository.getMakerByPrefix(prefix)
                
                val stubProduct = ProductMaster(
                    janCode = normalizedInput,
                    makerJanPrefix = prefix,
                    makerName = makerCache?.makerName ?: "",
                    makerNameKana = makerCache?.makerNameKana ?: "",
                    productName = "（未登録商品）",
                    productNameKana = "",
                    spec = "",
                    infoFetched = false
                )
                val newId = productRepository.insertProduct(stubProduct)
                product = stubProduct.copy(id = newId)
            }

            // 2. セッション内の重複チェック
            val isDuplicate = scanRepository.hasDuplicateJan(session.id, normalizedInput)
            if (isDuplicate) {
                _uiState.value = _uiState.value.copy(
                    lastScannedJan = normalizedInput,
                    lastProductName = product.productName,
                    isDuplicate = true,
                    isDiscontinued = false,
                    errorMessage = ""
                )
                return@launch
            }

            // 3. 終売品警告または直接追加
            val isDiscontinued = product.status == ProductStatus.DISCONTINUED
            if (isDiscontinued) {
                _uiState.value = _uiState.value.copy(
                    lastScannedJan = normalizedInput,
                    lastProductName = product.productName,
                    isDuplicate = false,
                    isDiscontinued = true,
                    pendingDiscontinuedProduct = product,
                    errorMessage = ""
                )
            } else {
                saveToSessionInternal(session.id, product, normalizedInput)
            }
        }
    }

    private suspend fun saveToSessionInternal(sessionId: Long, product: ProductMaster, barcode: String) {
        scanRepository.addItemToSession(sessionId, product.id, barcode)
        if (scanSoundEnabled.value) {
            SoundHelper.playSuccessBeep()
        }
        _uiState.value = _uiState.value.copy(
            lastScannedJan = barcode,
            lastProductName = product.productName,
            isDuplicate = false,
            isDiscontinued = false,
            errorMessage = ""
        )
    }

    fun confirmDiscontinued() {
        val session = _uiState.value.currentSession ?: return
        val product = _uiState.value.pendingDiscontinuedProduct ?: return
        
        viewModelScope.launch {
            saveToSessionInternal(session.id, product, product.janCode)
            _uiState.value = _uiState.value.copy(
                pendingDiscontinuedProduct = null,
                isDiscontinued = false
            )
        }
    }

    fun cancelDiscontinued() {
        _uiState.value = _uiState.value.copy(
            pendingDiscontinuedProduct = null,
            isDiscontinued = false
        )
    }

    fun completeSession() {
        viewModelScope.launch {
            _uiState.value.currentSession?.let { session ->
                scanRepository.updateSession(session.copy(status = SessionStatus.COMPLETED))
                _uiState.value = _uiState.value.copy(currentSession = session.copy(status = SessionStatus.COMPLETED))
            }
        }
    }
}
