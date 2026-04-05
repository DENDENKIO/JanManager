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
import com.example.janmanager.util.ClipboardHelper
import com.example.janmanager.util.WebViewJsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // Selectors
    private var inputSelector = "div.ql-editor"  // Geminiデフォルト（Quill Editor）
    private var sendButtonSelector = "button[aria-label='プロンプトを送信']"
    private var responseSelector = ".response-content, .model-response-text"

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
                // Perplexity: Lexical Editor
                // id="ask-input" が最も確実なセレクタ
                inputSelector = "#ask-input"
                // 送信ボタン: aria-label='送信'
                sendButtonSelector = "button[aria-label='送信']"
                // レスポンス: .prose クラス
                responseSelector = ".prose"
            } else {
                // Gemini: Quill Editor (.ql-editor)
                // div[contenteditable='true'] だと .ql-clipboard にもヒットするため .ql-editor で指定
                inputSelector = "div.ql-editor"
                // 送信ボタン: aria-label='プロンプトを送信'
                sendButtonSelector = "button[aria-label='プロンプトを送信']"
                // レスポンス
                responseSelector = ".response-content, .model-response-text"
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
                fetchProductInfo(product)
                
                // Wait for user confirmation or auto-proceed
                // For "automatic cycle", we might wait until a result is parsed and then move to next
                // But the requirement says "Preview update -> Next JAN". 
                // Let's implement a pause for preview.
                while (_uiState.value.showPreview && _uiState.value.isRunning) {
                    delay(500)
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

    private suspend fun fetchProductInfo(product: ProductMaster) {
        _uiState.value = _uiState.value.copy(currentStatus = "${product.janCode} 取得中...")
        
        val prompt = AiPromptBuilder.buildPrompt(product.janCode)
        
        // 1. Inject Prompt
        // Gemini (Quill) / Perplexity (Lexical) 両方 execCommand ベースで注入
        val injectJs = WebViewJsHelper.getInjectPromptJsForRichEditor(inputSelector, prompt)
        evaluateJs(injectJs)
        delay(1000)
        
        // 2. Click Send
        val sendJs = WebViewJsHelper.getClickSendJs(sendButtonSelector)
        evaluateJs(sendJs)
        delay(2000) // Initial wait for generation
        
        // 3. Polling for response
        var result: AiResponseData? = null
        for (i in 0 until 20) { // Max 20 seconds polling
            if (!_uiState.value.isRunning) break
            
            val extractJs = WebViewJsHelper.getExtractResponseJs(responseSelector)
            val rawResponse = evaluateJsSync(extractJs)
            
            if (rawResponse != null && rawResponse != "null" && rawResponse.length > 2) {
                // Remove quotes from evaluateJavascript return
                val cleanResponse = rawResponse.removePrefix("\"").removeSuffix("\"").replace("\\n", "\n").replace("\\\"", "\"")
                val parsed = AiResponseParser.parseResponse(cleanResponse, product.janCode)
                if (parsed != null) {
                    result = parsed
                    break
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
        } else {
            _uiState.value = _uiState.value.copy(currentStatus = "取得失敗（タイムアウト）")
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
        }
    }

    fun onRejectResult() {
        _uiState.value = _uiState.value.copy(showPreview = false, lastResult = null)
    }

    fun copyPromptToClipboard(context: Context) {
        val product = _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex) ?: return
        val prompt = AiPromptBuilder.buildPrompt(product.janCode)
        ClipboardHelper.copyToClipboard(context, prompt)
    }

    fun tryManualCapture(context: Context) {
        viewModelScope.launch {
            val product = _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex) ?: return@launch
            
            // Try DOM extraction first
            val extractJs = WebViewJsHelper.getExtractResponseJs(responseSelector)
            val rawResponse = evaluateJsSync(extractJs)
            var cleanResponse = rawResponse?.removePrefix("\"")?.removeSuffix("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"")
            
            var parsed = cleanResponse?.let { AiResponseParser.parseResponse(it, product.janCode) }
            
            // Fallback to clipboard
            if (parsed == null) {
                val clipboardText = ClipboardHelper.readFromClipboard(context)
                parsed = clipboardText?.let { AiResponseParser.parseResponse(it, product.janCode) }
            }
            
            if (parsed != null) {
                _uiState.value = _uiState.value.copy(
                    lastResult = parsed,
                    showPreview = true,
                    currentStatus = "手動取得成功"
                )
            } else {
                _uiState.value = _uiState.value.copy(currentStatus = "手動取得失敗")
            }
        }
    }
}
