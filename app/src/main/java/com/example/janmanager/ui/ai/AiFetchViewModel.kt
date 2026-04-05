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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

    private var webView: WebView? = null
    private var fetchJob: Job? = null
    private val _proceedSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var manualInputSelector: String? = null
    private var manualSendButtonSelector: String? = null
    private var manualResponseSelector: String? = null

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
            while (_uiState.value.currentIndex < _uiState.value.unfetchedProducts.size
                && _uiState.value.isRunning) {
                val product = _uiState.value.unfetchedProducts[_uiState.value.currentIndex]
                val success = fetchProductInfo(product)
                if (success && _uiState.value.isRunning) {
                    _proceedSignal.first()
                } else if (_uiState.value.isRunning) {
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

        // WebView導通確認
        val targetBaseUrl = if (_uiState.value.aiUrl.orEmpty().contains("perplexity")) "perplexity.ai" else "gemini.google.com"
        var isPageReady = false
        for (wait in 0 until 15) {
            val currentUrl = evaluateJsSync("window.location.href") ?: ""
            if (currentUrl.contains(targetBaseUrl)) {
                isPageReady = true
                break
            }
            _uiState.value = _uiState.value.copy(currentStatus = "ページ遷移待ち... (${wait + 1}/15)")
            delay(1000)
        }
        if (!isPageReady) {
            _uiState.value = _uiState.value.copy(currentStatus = "エラー: ページが読み込まれませんでした。")
            return false
        }

        val prompt = AiPromptBuilder.buildPrompt(product.janCode)
        var lastError = ""

        for (retry in 0 until 3) {
            _uiState.value = _uiState.value.copy(currentStatus = "${product.janCode} プロンプト注入中... (試行 ${retry + 1}/3)")

            val injectJs = WebViewJsHelper.getInjectPromptJsWithFallback(inputSelectors, manualInputSelector, prompt)
            val injectResult = evaluateJsSync(injectJs)
            // 修正点: 'true' 文字列で比較（evaluateJavascriptは値をJSON文字列として返すためクォート付きも考慮）
            val injectSuccess = injectResult == "true" || injectResult == "\"true\""

            if (injectSuccess) {
                delay(1000)
                val sendJs = WebViewJsHelper.getClickSendJsWithFallback(sendSelectors, manualSendButtonSelector)
                val sendResult = evaluateJsSync(sendJs)
                val sendSuccess = sendResult == "true" || sendResult == "\"true\""
                if (sendSuccess) {
                    lastError = ""
                    break
                } else {
                    lastError = "送信ボタンが見つかりません"
                }
            } else {
                lastError = "入力欄への貼り付け失敗"
            }
            delay(2000)
        }

        if (lastError.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(currentStatus = "エラー: $lastError。AIサイトの構造が変わった可能性があります。")
            return false
        }

        delay(2000)

        // レスポンスポーリング（最大30秒）
        var result: AiResponseData? = null
        for (i in 0 until 30) {
            if (!_uiState.value.isRunning) break
            val extractJs = WebViewJsHelper.getExtractResponseJsWithFallback(responseSelectors, manualResponseSelector)
            val rawResponse = evaluateJsSync(extractJs)

            if (!rawResponse.isNullOrBlank() && rawResponse != "null") {
                // evaluateJavascriptは文字列をクォートで囲んで返すため両方に対応
                val cleaned = rawResponse
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim()

                when (val parseResult = AiResponseParser.parseResponse(cleaned, product.janCode)) {
                    is AiParseResult.Success -> {
                        result = parseResult.data
                        break
                    }
                    is AiParseResult.NotFound -> {
                        result = AiResponseData(jan_code = product.janCode, not_found = true)
                        break
                    }
                    is AiParseResult.JanMismatch -> {
                        _uiState.value = _uiState.value.copy(currentStatus = "JAN不一致: ${parseResult.actual}")
                    }
                    is AiParseResult.InvalidFormat -> {
                        // まだ生成中の可能性があるのでポーリング続行
                    }
                }
            }
            delay(1000)
        }

        return if (result != null) {
            _uiState.value = _uiState.value.copy(
                lastResult = result,
                showPreview = true,
                currentStatus = "取得成功"
            )
            true
        } else {
            _uiState.value = _uiState.value.copy(currentStatus = "取得失敗（タイムアウト）")
            false
        }
    }

    /**
     * WebViewがnullの場合に永久ハングしないようタイムアウト付きコルーチンに修正。
     * evaluateJavascriptの必須属性: UIスレッド(post)で実行し、
     * コールバックが返ったらcontinuationを安全にresumeする。
     */
    private suspend fun evaluateJsSync(script: String): String? {
        val wv = webView ?: return null  // nullならすぐ返却（ハング防止）
        return try {
            withTimeout(10_000L) {  // 10秒タイムアウト
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    wv.post {
                        wv.evaluateJavascript(script) { result ->
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(result))
                            }
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            null
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
            val extractJs = WebViewJsHelper.getExtractResponseJsWithFallback(responseSelectors, manualResponseSelector)
            val rawResponse = evaluateJsSync(extractJs)
            val cleanResponse = rawResponse
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                ?.trim()

            var parseResult = cleanResponse?.let { AiResponseParser.parseResponse(it, product.janCode) }

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
