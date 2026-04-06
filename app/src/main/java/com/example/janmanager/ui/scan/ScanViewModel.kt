package com.example.janmanager.ui.scan

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.BarcodeType
import com.example.janmanager.data.local.entity.InfoSource
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.GroupRepository
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.settings.SettingsDataStore
import com.example.janmanager.util.AiPromptBuilder
import com.example.janmanager.util.AiResponseData
import com.example.janmanager.util.AiResponseParser
import com.example.janmanager.util.AiParseResult
import com.example.janmanager.util.AiWebViewInteractor
import com.example.janmanager.util.JanCodeUtil
import com.example.janmanager.util.SoundHelper
import com.example.janmanager.util.WebViewJsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScanModeTab {
    CONTINUOUS, CONFIRM, LINKAGE
}

data class RecentScan(
    val jan: String,
    val productName: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LinkageSlot {
    PIECE, PACK, CASE
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val groupRepository: GroupRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val isItfEnabled = settingsDataStore.isItfEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanSoundEnabled = settingsDataStore.scanSoundEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentTab = MutableStateFlow(ScanModeTab.CONTINUOUS)
    val currentTab = _currentTab.asStateFlow()

    // Continuous Mode State
    private val _recentlyScanned = MutableStateFlow<List<RecentScan>>(emptyList())
    val recentlyScanned = _recentlyScanned.asStateFlow()

    // Confirm Mode State
    private val _confirmProduct = MutableStateFlow<ProductMaster?>(null)
    val confirmProduct = _confirmProduct.asStateFlow()
    private val _lastConfirmBarcode = MutableStateFlow("")
    val lastConfirmBarcode = _lastConfirmBarcode.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val confirmProductGroups: StateFlow<List<Int>> = _lastConfirmBarcode
        .flatMapLatest { jan ->
            if (jan.isEmpty()) return@flatMapLatest flowOf(emptyList<Int>())
            groupRepository.getActiveGroups().flatMapLatest { activeGroups ->
                if (activeGroups.isEmpty()) return@flatMapLatest flowOf(emptyList<Int>())
                val flows = activeGroups.map { group ->
                    groupRepository.getGroupItems(group.id).map { items -> 
                        if (items.any { it.janCode == jan }) group.tagColor else null
                    }
                }
                combine(flows) { colors -> colors.filterNotNull() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Fetch in Confirm Mode
    private val _showAiSheet = MutableStateFlow(false)
    val showAiSheet = _showAiSheet.asStateFlow()
    
    private val _aiFetchStatus = MutableStateFlow("")
    val aiFetchStatus = _aiFetchStatus.asStateFlow()
    
    private val _aiResultPreview = MutableStateFlow<AiResponseData?>(null)
    val aiResultPreview = _aiResultPreview.asStateFlow()

    private val _aiUrl = MutableStateFlow<String?>(null)
    val aiUrl = _aiUrl.asStateFlow()

    private val interactor = AiWebViewInteractor()
    private var targetBaseUrl: String = "gemini.google.com"

    // Linkage Mode State (Packaging Units)
    private val _activeLinkageSlot = MutableStateFlow(LinkageSlot.PIECE)
    val activeLinkageSlot = _activeLinkageSlot.asStateFlow()
    
    private val _linkagePieceJan = MutableStateFlow("")
    val linkagePieceJan = _linkagePieceJan.asStateFlow()
    
    private val _linkagePackJan = MutableStateFlow("")
    val linkagePackJan = _linkagePackJan.asStateFlow()

    private val _linkageCaseJan = MutableStateFlow("")
    val linkageCaseJan = _linkageCaseJan.asStateFlow()

    private val _linkagePackQty = MutableStateFlow(6)
    val linkagePackQty = _linkagePackQty.asStateFlow()

    private val _linkageCaseQty = MutableStateFlow(12)
    val linkageCaseQty = _linkageCaseQty.asStateFlow()

    init {
        viewModelScope.launch {
            val aiSelection = settingsDataStore.aiSelectionFlow.first()
            val inputSel: List<String>
            val sendSel: List<String>
            val responseSel: List<String>

            if (aiSelection == "PERPLEXITY") {
                _aiUrl.value = "https://www.perplexity.ai/"
                targetBaseUrl = "perplexity.ai"
                inputSel = WebViewJsHelper.PERPLEXITY_INPUT_SELECTORS
                sendSel = WebViewJsHelper.PERPLEXITY_SEND_SELECTORS
                responseSel = WebViewJsHelper.PERPLEXITY_RESPONSE_SELECTORS
            } else {
                _aiUrl.value = "https://gemini.google.com/app?hl=ja"
                targetBaseUrl = "gemini.google.com"
                inputSel = WebViewJsHelper.GEMINI_INPUT_SELECTORS
                sendSel = WebViewJsHelper.GEMINI_SEND_SELECTORS
                responseSel = WebViewJsHelper.GEMINI_RESPONSE_SELECTORS
            }

            var manualInput: String? = null
            var manualSend: String? = null
            var manualResponse: String? = null
            val config = settingsDataStore.selectorConfigFlow.first()
            if (config.isNotEmpty()) {
                val parts = config.split("|")
                if (parts.size == 3) {
                    manualInput = parts[0].ifEmpty { null }
                    manualSend = parts[1].ifEmpty { null }
                    manualResponse = parts[2].ifEmpty { null }
                }
            }

            interactor.configure(inputSel, sendSel, responseSel, manualInput, manualSend, manualResponse)
        }
    }

    fun setTab(tab: ScanModeTab) {
        _currentTab.value = tab
    }
    fun processBarcode(barcode: String) {
        val normalizedInput = com.example.janmanager.util.Normalizer.toHalfWidth(barcode).trim()
        val type = JanCodeUtil.detectCodeType(normalizedInput)
        
        // ITF Control
        if (!isItfEnabled.value && type == BarcodeType.ITF14) {
            return // Ignore ITF if disabled
        }

        // Convert ITF to JAN if necessary or keep it based on modes
        val normalizedBarcode = if (type == BarcodeType.ITF14) JanCodeUtil.itfToJan(normalizedInput) else normalizedInput

        if (scanSoundEnabled.value) {
            SoundHelper.playSuccessBeep()
        }

        when (_currentTab.value) {
            ScanModeTab.CONTINUOUS -> {
                viewModelScope.launch {
                    val product = productRepository.getProductByJan(normalizedBarcode)
                    val name = product?.productName ?: "未登録"
                    val list = _recentlyScanned.value.toMutableList()
                    list.add(0, RecentScan(normalizedBarcode, name))
                    _recentlyScanned.value = list.take(50) // Keep last 50
                }
            }
            ScanModeTab.CONFIRM -> {
                _lastConfirmBarcode.value = normalizedBarcode
                viewModelScope.launch {
                    _confirmProduct.value = productRepository.getProductByJan(normalizedBarcode)
                }
            }
            ScanModeTab.LINKAGE -> {
                when (_activeLinkageSlot.value) {
                    LinkageSlot.PIECE -> {
                        _linkagePieceJan.value = normalizedBarcode
                        _activeLinkageSlot.value = LinkageSlot.PACK
                    }
                    LinkageSlot.PACK -> {
                        _linkagePackJan.value = normalizedBarcode
                        _activeLinkageSlot.value = LinkageSlot.CASE
                    }
                    LinkageSlot.CASE -> {
                        _linkageCaseJan.value = normalizedBarcode
                    }
                }
            }
        }
    }
    
    fun setLinkageSlot(slot: LinkageSlot) {
        _activeLinkageSlot.value = slot
    }

    fun clearLinkageSlot(slot: LinkageSlot) {
        when (slot) {
            LinkageSlot.PIECE -> _linkagePieceJan.value = ""
            LinkageSlot.PACK -> _linkagePackJan.value = ""
            LinkageSlot.CASE -> _linkageCaseJan.value = ""
        }
    }

    fun setPackQty(qty: Int) { _linkagePackQty.value = qty }
    fun setCaseQty(qty: Int) { _linkageCaseQty.value = qty }

    fun executePackageLinkage() {
        viewModelScope.launch {
            val pieceJan = _linkagePieceJan.value
            if (pieceJan.isEmpty()) return@launch

            val product = productRepository.getProductByJan(pieceJan) ?: return@launch

            // Pack Unit
            if (_linkagePackJan.value.isNotEmpty()) {
                val packUnit = com.example.janmanager.data.local.entity.PackageUnit(
                    productId = product.id,
                    barcode = _linkagePackJan.value,
                    barcodeType = JanCodeUtil.detectCodeType(_linkagePackJan.value),
                    packageType = com.example.janmanager.data.local.entity.PackageType.PACK,
                    packageLabel = "パック",
                    quantityPerUnit = _linkagePackQty.value
                )
                productRepository.addPackageUnit(packUnit)
            }

            // Case Unit
            if (_linkageCaseJan.value.isNotEmpty()) {
                val caseUnit = com.example.janmanager.data.local.entity.PackageUnit(
                    productId = product.id,
                    barcode = _linkageCaseJan.value,
                    barcodeType = JanCodeUtil.detectCodeType(_linkageCaseJan.value),
                    packageType = com.example.janmanager.data.local.entity.PackageType.CASE,
                    packageLabel = "ケース",
                    quantityPerUnit = _linkageCaseQty.value
                )
                productRepository.addPackageUnit(caseUnit)
            }
            
            // clear
            _linkagePieceJan.value = ""
            _linkagePackJan.value = ""
            _linkageCaseJan.value = ""
            _activeLinkageSlot.value = LinkageSlot.PIECE
        }
    }

    // AI Fetch Functions
    fun setWebView(wv: WebView) {
        interactor.webView = wv
    }

    fun openAiFetchSheet() {
        _showAiSheet.value = true
        _aiFetchStatus.value = "待機中"
        _aiResultPreview.value = null
    }

    fun closeAiFetchSheet() {
        _showAiSheet.value = false
    }

    fun startSingleAiFetch() {
        val janCode = _lastConfirmBarcode.value
        if (janCode.isEmpty()) return
        viewModelScope.launch {
            _aiFetchStatus.value = "実行中..."
            val result = interactor.executeFullFlow(janCode, targetBaseUrl) { status ->
                _aiFetchStatus.value = status
            }
            if (result.success && result.data != null) {
                _aiResultPreview.value = result.data
                _aiFetchStatus.value = "取得成功"
            } else {
                _aiFetchStatus.value = "エラー: ${result.errorMessage ?: "取得失敗"}"
            }
        }
    }

    fun startBatchAiFetch() {
        viewModelScope.launch {
            val unfetched = productRepository.getUnfetchedProducts().first()
            if (unfetched.isEmpty()) {
                _aiFetchStatus.value = "未取得商品なし"
                return@launch
            }

            _aiFetchStatus.value = "一括開始: ${unfetched.size}件"
            unfetched.forEachIndexed { index, product ->
                _lastConfirmBarcode.value = product.janCode
                _aiFetchStatus.value = "一括取得 (${index + 1}/${unfetched.size}): ${product.janCode}"

                val result = interactor.executeFullFlow(product.janCode, targetBaseUrl) { status ->
                    _aiFetchStatus.value = "(${index + 1}/${unfetched.size}) $status"
                }

                if (result.success && result.data != null) {
                    _aiResultPreview.value = result.data
                    _aiFetchStatus.value = "(${index + 1}/${unfetched.size}) 取得成功: ${product.janCode}"
                    // ユーザーが結果を確認する時間を取る
                    // TODO: 将来的には承認待ちフローに変更することを推奨
                    delay(3000)
                } else {
                    _aiFetchStatus.value = "(${index + 1}/${unfetched.size}) 失敗: ${result.errorMessage ?: "不明"}"
                    delay(2000)
                }
            }
            _aiFetchStatus.value = "一括取得完了"
        }
    }

    fun acceptAiResult() {
        val result = _aiResultPreview.value ?: return
        viewModelScope.launch {
            val janCode = _lastConfirmBarcode.value
            val existing = productRepository.getProductByJan(janCode)
            
            val prefix = JanCodeUtil.extractMakerPrefix(janCode)
            
            val product = if (existing != null) {
                existing.copy(
                    makerName = result.maker_name,
                    makerNameKana = result.maker_name_kana,
                    productName = result.product_name,
                    productNameKana = result.product_name_kana,
                    spec = result.spec,
                    infoFetched = true,
                    infoSource = if (_aiUrl.value.orEmpty().contains("gemini")) InfoSource.AI_GEMINI else InfoSource.AI_PERPLEXITY,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                ProductMaster(
                    janCode = janCode,
                    makerJanPrefix = prefix,
                    makerName = result.maker_name,
                    makerNameKana = result.maker_name_kana,
                    productName = result.product_name,
                    productNameKana = result.product_name_kana,
                    spec = result.spec,
                    infoFetched = true,
                    infoSource = if (_aiUrl.value.orEmpty().contains("gemini")) InfoSource.AI_GEMINI else InfoSource.AI_PERPLEXITY
                )
            }
            
            if (existing != null) {
                productRepository.updateProduct(product)
            } else {
                productRepository.insertProduct(product)
            }
            
            _confirmProduct.value = product
            _showAiSheet.value = false
        }
    }
}
