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
import com.example.janmanager.util.JanCodeUtil
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

enum class LinkageSlot {
    OLD_JAN, NEW_JAN, PACKAGE
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val groupRepository: GroupRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val isItfEnabled = settingsDataStore.isItfEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentTab = MutableStateFlow(ScanModeTab.CONTINUOUS)
    val currentTab = _currentTab.asStateFlow()

    // Continuous Mode State
    private val _recentlyScanned = MutableStateFlow<List<String>>(emptyList())
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

    private var webView: WebView? = null
    private var inputSelector = "div.ql-editor"  // Geminiデフォルト（Quill Editor）
    private var sendButtonSelector = "button[aria-label='プロンプトを送信']"
    private var responseSelector = ".response-content, .model-response-text"

    // Linkage Mode State
    private val _activeLinkageSlot = MutableStateFlow(LinkageSlot.OLD_JAN)
    val activeLinkageSlot = _activeLinkageSlot.asStateFlow()
    
    private val _linkageOldJan = MutableStateFlow("")
    val linkageOldJan = _linkageOldJan.asStateFlow()
    
    private val _linkageNewJan = MutableStateFlow("")
    val linkageNewJan = _linkageNewJan.asStateFlow()

    private val _linkagePackage = MutableStateFlow("")
    val linkagePackage = _linkagePackage.asStateFlow()

    init {
        viewModelScope.launch {
            val aiSelection = settingsDataStore.aiSelectionFlow.first()
            _aiUrl.value = if (aiSelection == "PERPLEXITY") {
                "https://www.perplexity.ai/"
            } else {
                "https://gemini.google.com/app?hl=ja"
            }
            
            if (aiSelection == "PERPLEXITY") {
                // Perplexity: Lexical Editor
                inputSelector = "#ask-input"
                sendButtonSelector = "button[aria-label='送信']"
                responseSelector = ".prose"
            } else {
                // Gemini: Quill Editor
                inputSelector = "div.ql-editor"
                sendButtonSelector = "button[aria-label='プロンプトを送信']"
                responseSelector = ".response-content, .model-response-text"
            }
        }
    }

    fun setTab(tab: ScanModeTab) {
        _currentTab.value = tab
    }
    
    fun setLinkageSlot(slot: LinkageSlot) {
        _activeLinkageSlot.value = slot
    }

    fun processBarcode(barcode: String) {
        val type = JanCodeUtil.detectCodeType(barcode)
        
        // ITF Control
        if (!isItfEnabled.value && type == BarcodeType.ITF14) {
            return // Ignore ITF if disabled
        }

        // Convert ITF to JAN if necessary or keep it based on modes
        val normalizedBarcode = if (type == BarcodeType.ITF14) JanCodeUtil.itfToJan(barcode) else barcode

        when (_currentTab.value) {
            ScanModeTab.CONTINUOUS -> {
                val list = _recentlyScanned.value.toMutableList()
                list.add(0, normalizedBarcode)
                _recentlyScanned.value = list.take(50) // Keep last 50
            }
            ScanModeTab.CONFIRM -> {
                _lastConfirmBarcode.value = normalizedBarcode
                viewModelScope.launch {
                    _confirmProduct.value = productRepository.getProductByJan(normalizedBarcode)
                }
            }
            ScanModeTab.LINKAGE -> {
                when (_activeLinkageSlot.value) {
                    LinkageSlot.OLD_JAN -> {
                        _linkageOldJan.value = normalizedBarcode
                        _activeLinkageSlot.value = LinkageSlot.NEW_JAN
                    }
                    LinkageSlot.NEW_JAN -> {
                        _linkageNewJan.value = normalizedBarcode
                        _activeLinkageSlot.value = LinkageSlot.PACKAGE
                    }
                    LinkageSlot.PACKAGE -> {
                        _linkagePackage.value = normalizedBarcode
                    }
                }
            }
        }
    }
    
    fun executeLinkage() {
        viewModelScope.launch {
            if (_linkageOldJan.value.isNotEmpty() && _linkageNewJan.value.isNotEmpty()) {
                productRepository.linkRenewal(_linkageOldJan.value, _linkageNewJan.value)
            }
            
            // clear
            _linkageOldJan.value = ""
            _linkageNewJan.value = ""
            _linkagePackage.value = ""
            _activeLinkageSlot.value = LinkageSlot.OLD_JAN
        }
    }

    // AI Fetch Functions
    fun setWebView(wv: WebView) {
        webView = wv
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
            _aiFetchStatus.value = "プロンプト入力中..."
            val prompt = AiPromptBuilder.buildPrompt(janCode)
            
            val injectJs = WebViewJsHelper.getInjectPromptJsForRichEditor(inputSelector, prompt)
            webView?.evaluateJavascript(injectJs, null)
            delay(1000)
            
            _aiFetchStatus.value = "送信中..."
            val sendJs = WebViewJsHelper.getClickSendJs(sendButtonSelector)
            webView?.evaluateJavascript(sendJs, null)
            
            _aiFetchStatus.value = "回答待ち..."
            repeat(20) {
                val extractJs = WebViewJsHelper.getExtractResponseJs(responseSelector)
                val rawResponse = kotlin.coroutines.suspendCoroutine<String?> { continuation ->
                    webView?.post {
                        webView?.evaluateJavascript(extractJs) { result ->
                            continuation.resumeWith(Result.success(result))
                        }
                    }
                }
                
                if (rawResponse != null && rawResponse != "null" && rawResponse.length > 2) {
                    val cleanResponse = rawResponse.removePrefix("\"").removeSuffix("\"").replace("\\n", "\n").replace("\\\"", "\"")
                    val parsed = AiResponseParser.parseResponse(cleanResponse, janCode)
                    if (parsed != null) {
                        _aiResultPreview.value = parsed
                        _aiFetchStatus.value = "取得完了"
                        return@launch
                    }
                }
                delay(1000)
            }
            _aiFetchStatus.value = "タイムアウト"
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
