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
    val aiUrl: String? = null  // null = 設定読み込み待ち
)

@HiltViewModel
class AiFetchViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiFetchUiState())
    val uiState: StateFlow<AiFetchUiState> = _uiState.asStateFlow()

    private var webView: WebView? = null
    private var fetchJob: Job? = null
    private val _proceedSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Manual Selectors (from settings)
    private var manualInputSelector: String? = null
    private var manualSendButtonSelector: String? = null
    private var manualResponseSelector: String? = null
    
    // Fallback Chains
    private var inputSelectors: List<String> = WebViewJsHelper.GEMINI_INPUT_SELECTORS
    private var sendSelectors: List<String> = WebViewJsHelper.GEMINI_SEND_SELECTORS
    private var responseSelectors: List<String> = WebViewJsHelper.GEMINI_RESPONSE_SELECTORS

    init {
        viewModelScope.launch {
            repository.getUnfetchedProducts().collect { products ->
                _uiState.value = _uiState.value.copy(unfetchedProducts = products)
            }
        }
        
        viewModelScope.launch {
            val aiSelection = settingsDataStore.aiSelectionFlow.first()
            val url = if (aiSelection == "PERPLEXITY") {
                "https://www.perplexity.ai/"
            } else {
                "https://gemini.google.com/app?hl=ja"
            }
            _uiState.value = _uiState.value.copy(aiUrl = url)
            
            // Update selectors based on AI selection
            if (aiSelection == "PERPLEXITY") {
                inputSelectors = WebViewJsHelper.PERPLEXITY_INPUT_SELECTORS
                sendSelectors = WebViewJsHelper.PERPLEXITY_SEND_SELECTORS
                responseSelectors = WebViewJsHelper.PERPLEXITY_RESPONSE_SELECTORS
            } else {
                inputSelectors = WebViewJsHelper.GEMINI_INPUT_SELECTORS
                sendSelectors = WebViewJsHelper.GEMINI_SEND_SELECTORS
                responseSelectors = WebViewJsHelper.GEMINI_RESPONSE_SELECTORS
            }
            
            val config = settingsDataStore.selectorConfigFlow.first()
            if (config.isNotEmpty()) {
                val parts = config.split("|")
                if (parts.size == 3) {
                    manualInputSelector = parts[0].ifEmpty { null }
                    manualSendButtonSelector = parts[1].ifEmpty { null }
                    manualResponseSelector = parts[2].ifEmpty { null }
                }
            }
        }
    }

    fun setWebView(wv: WebView) {
        webView = wv
    }

    fun startAutoFetch() {
        if (_uiState.value.unfetchedProducts.isEmpty()) return
        
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true, currentIndex = 0)
            
            while (_uiState.value.currentIndex < _uiState.value.unfetchedProducts.size && _uiState.value.isRunning) {
                val product = _uiState.value.unfetchedProducts[_uiState.value.currentIndex]
                val success = fetchProductInfo(product)
                
                if (success && _uiState.value.isRunning) {
                    // Wait for user to Accept or Reject
                    _proceedSignal.first()
                } else if (_uiState.value.isRunning) {
                    // Failure or timeout: wait a bit before next or let user handle
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

    private suspend fun fetchProductInfo(product: ProductMaster): Boolean {
        _uiState.value = _uiState.value.copy(currentStatus = "${product.janCode} 取得中...")
        
        // 0. Wait for WebView to be ready and on correct base URL
        val targetBaseUrl = if (_uiState.value.aiUrl.orEmpty().contains("perplexity")) "perplexity.ai" else "gemini.google.com"
        var isPageReady = false
        for (wait in 0 until 10) {
            val currentUrl = evaluateJsSync("window.location.href") ?: ""
            if (currentUrl.contains(targetBaseUrl)) {
                isPageReady = true
                break
            }
            _uiState.value = _uiState.value.copy(currentStatus = "ページ遷移待ち... (${wait+1}/10)")
            delay(1000)
        }
        
        if (!isPageReady) {
            _uiState.value = _uiState.value.copy(currentStatus = "エラー: ページが正しく読み込まれませんでした。")
            return false
        }

        val prompt = AiPromptBuilder.buildPrompt(product.janCode)
        var lastError = ""
        
        // Retry Loop for Injection
        for (retry in 0 until 3) {
            _uiState.value = _uiState.value.copy(currentStatus = "${product.janCode} プロンプト注入中... (試行 ${retry + 1}/3)")
            
            // 1. Inject Prompt using Fallback Chain
            val injectJs = WebViewJsHelper.getInjectPromptJsWithFallback(inputSelectors, manualInputSelector, prompt)
            val injectSuccess = evaluateJsSync(injectJs) == "true"
            
            if (injectSuccess) {
                delay(800)
                // 2. Click Send using Fallback Chain
                val sendJs = WebViewJsHelper.getClickSendJsWithFallback(sendSelectors, manualSendButtonSelector)
                val sendSuccess = evaluateJsSync(sendJs) == "true"
                if (sendSuccess) {
                    lastError = ""
                    break // Successfully injected and sent
                } else {
                    lastError = "送信ボタンが見つかりません"
                }
            } else {
                lastError = "入力欄への貼り付け失敗"
            }
            delay(2000) // Wait before retry
        }
        
        if (lastError.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(currentStatus = "エラー: $lastError。AIサイトの構造が変わった可能性があります。")
            return false
        }
        
        delay(2000) // Initial wait for generation
        
        // 3. Polling for response using Fallback Chain
        var result: AiResponseData? = null
        for (i in 0 until 20) { // Max 20 seconds polling
            if (!_uiState.value.isRunning) break
            
            val extractJs = WebViewJsHelper.getExtractResponseJsWithFallback(responseSelectors, manualResponseSelector)
            val rawResponse = evaluateJsSync(extractJs)
            
            if (rawResponse != null && rawResponse != "null" && rawResponse.length > 2) {
                val cleanResponse = rawResponse.removePrefix("\"").removeSuffix("\"").replace("\\n", "\n").replace("\\\"", "\"")
                when (val parseResult = AiResponseParser.parseResponse(cleanResponse, product.janCode)) {
                    is AiParseResult.Success -> {
                        result = parseResult.data
                        break
                    }
                    is AiParseResult.NotFound -> {
                        // AI found nothing, but it's a valid response
                        result = AiResponseData(jan_code = product.janCode, not_found = true)
                        break
                    }
                    is AiParseResult.JanMismatch -> {
                        _uiState.value = _uiState.value.copy(currentStatus = "JAN不一致: ${parseResult.actual}")
                    }
                    is AiParseResult.InvalidFormat -> {
                        // Still polling, maybe it's not complete yet
                    }
                }
            }
            delay(1000)
        }
        
        if (result != null) {
            _uiState.value = _uiState.value.copy(
                lastResult = result,
                showPreview = true,
                currentStatus = "取得成功"
            )
            return true
        } else {
            _uiState.value = _uiState.value.copy(currentStatus = "取得失敗（タイムアウト）")
            return false
        }
    }

    private fun evaluateJs(script: String) {
        webView?.post {
            webView?.evaluateJavascript(script, null)
        }
    }

    private suspend fun evaluateJsSync(script: String): String? {
        return kotlin.coroutines.suspendCoroutine { continuation ->
            webView?.post {
                webView?.evaluateJavascript(script) { result ->
                    continuation.resumeWith(Result.success(result))
                }
            }
        }
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
            
            // Also cache maker if available
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
            
            // Try DOM extraction with fallback
            val extractJs = WebViewJsHelper.getExtractResponseJsWithFallback(responseSelectors, manualResponseSelector)
            val rawResponse = evaluateJsSync(extractJs)
            var cleanResponse = rawResponse?.removePrefix("\"")?.removeSuffix("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"")
            
            var parseResult = cleanResponse?.let { AiResponseParser.parseResponse(it, product.janCode) }
            
            // Fallback to clipboard if not success
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
                val errorMsg = when(parseResult) {
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
