package com.example.janmanager.ui.ai

import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.InfoSource
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.settings.SettingsDataStore
import com.example.janmanager.util.AiPromptBuilder
import com.example.janmanager.util.AiResponseData
import com.example.janmanager.util.AiResponseParser
import com.example.janmanager.util.AiParseResult
import com.example.janmanager.util.AiWebViewInteractor
import com.example.janmanager.util.ClipboardHelper
import com.example.janmanager.util.WebViewJsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiFetchUiState(
    val unfetchedProducts: List<ProductMaster> = emptyList(),
    val currentIndex: Int = -1,
    val isRunning: Boolean = false,
    val currentStatus: String = "待機中",
    val lastResult: AiResponseData? = null,
    val showPreview: Boolean = false,
    val aiUrl: String? = null
)

@HiltViewModel
class AiFetchViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiFetchUiState())
    val uiState: StateFlow<AiFetchUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private val _proceedSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val interactor = AiWebViewInteractor()
    private var targetBaseUrl: String = "gemini.google.com"

    init {
        viewModelScope.launch {
            repository.getUnfetchedProducts().collect { products ->
                _uiState.value = _uiState.value.copy(unfetchedProducts = products)
            }
        }
        viewModelScope.launch {
            val aiSelection = settingsDataStore.aiSelectionFlow.first()
            val url: String
            val inputSel: List<String>
            val sendSel: List<String>
            val responseSel: List<String>

            if (aiSelection == "PERPLEXITY") {
                url = "https://www.perplexity.ai/"
                targetBaseUrl = "perplexity.ai"
                inputSel = WebViewJsHelper.PERPLEXITY_INPUT_SELECTORS
                sendSel = WebViewJsHelper.PERPLEXITY_SEND_SELECTORS
                responseSel = WebViewJsHelper.PERPLEXITY_RESPONSE_SELECTORS
            } else {
                url = "https://gemini.google.com/app?hl=ja"
                targetBaseUrl = "gemini.google.com"
                inputSel = WebViewJsHelper.GEMINI_INPUT_SELECTORS
                sendSel = WebViewJsHelper.GEMINI_SEND_SELECTORS
                responseSel = WebViewJsHelper.GEMINI_RESPONSE_SELECTORS
            }
            _uiState.value = _uiState.value.copy(aiUrl = url)

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

    fun setWebView(wv: WebView) {
        interactor.webView = wv
    }

    fun startAutoFetch() {
        if (_uiState.value.unfetchedProducts.isEmpty()) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true, currentIndex = 0)
            while (_uiState.value.currentIndex < _uiState.value.unfetchedProducts.size
                && _uiState.value.isRunning
            ) {
                val product = _uiState.value.unfetchedProducts[_uiState.value.currentIndex]
                val result = interactor.executeFullFlow(product.janCode, targetBaseUrl) { status ->
                    _uiState.value = _uiState.value.copy(currentStatus = status)
                }

                if (result.success && result.data != null && _uiState.value.isRunning) {
                    _uiState.value = _uiState.value.copy(
                        lastResult = result.data,
                        showPreview = true,
                        currentStatus = "取得成功"
                    )
                    // ユーザーが承認/却下するまで待機
                    _proceedSignal.first()
                } else if (_uiState.value.isRunning) {
                    _uiState.value = _uiState.value.copy(
                        currentStatus = "エラー: ${result.errorMessage ?: "不明なエラー"}"
                    )
                    delay(2000)
                }

                if (_uiState.value.isRunning) {
                    _uiState.value = _uiState.value.copy(currentIndex = _uiState.value.currentIndex + 1)
                }
            }
            _uiState.value = _uiState.value.copy(isRunning = false, currentStatus = "完了")
        }
    }

    fun stopFetch() {
        _uiState.value = _uiState.value.copy(isRunning = false, currentStatus = "停止中")
        fetchJob?.cancel()
    }

    fun onAcceptResult() {
        viewModelScope.launch {
            val result = _uiState.value.lastResult ?: return@launch
            val product = _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex) ?: return@launch
            val updatedProduct = product.copy(
                makerName = result.maker_name,
                makerNameKana = result.maker_name_kana,
                productName = result.product_name,
                productNameKana = result.product_name_kana,
                spec = result.spec,
                infoFetched = true,
                infoSource = if (_uiState.value.aiUrl.orEmpty().contains("gemini")) InfoSource.AI_GEMINI else InfoSource.AI_PERPLEXITY,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateProduct(updatedProduct)
            if (result.maker_name.isNotEmpty()) {
                val prefix = product.janCode.take(7)
                repository.cacheMaker(prefix, result.maker_name, result.maker_name_kana)
            }
            _uiState.value = _uiState.value.copy(showPreview = false, lastResult = null)
            _proceedSignal.tryEmit(Unit)
        }
    }

    fun onRejectResult() {
        _uiState.value = _uiState.value.copy(showPreview = false, lastResult = null)
        _proceedSignal.tryEmit(Unit)
    }

    fun copyPromptToClipboard(context: Context) {
        val product = _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex) ?: return
        val prompt = AiPromptBuilder.buildPrompt(product.janCode)
        ClipboardHelper.copyToClipboard(context, prompt)
    }

    fun tryManualCapture(context: Context) {
        viewModelScope.launch {
            val product = _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex) ?: return@launch

            // WebViewから取得を試みる
            var parseResult = interactor.extractCurrentResponse(product.janCode)

            // 失敗ならクリップボードから試みる
            if (parseResult !is AiParseResult.Success) {
                val clipboardText = ClipboardHelper.readFromClipboard(context)
                val cbResult = clipboardText?.let { AiResponseParser.parseResponse(it, product.janCode) }
                if (cbResult is AiParseResult.Success) {
                    parseResult = cbResult
                }
            }

            if (parseResult is AiParseResult.Success) {
                _uiState.value = _uiState.value.copy(
                    lastResult = parseResult.data,
                    showPreview = true,
                    currentStatus = "手動取得成功"
                )
            } else {
                val errorMsg = when (parseResult) {
                    is AiParseResult.JanMismatch -> "JAN不一致: ${parseResult.actual}"
                    is AiParseResult.InvalidFormat -> "形式エラー"
                    is AiParseResult.NotFound -> "見つかりません"
                    else -> "手動取得失敗"
                }
                _uiState.value = _uiState.value.copy(currentStatus = errorMsg)
            }
        }
    }
}
