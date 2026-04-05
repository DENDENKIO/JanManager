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
    private var flowObserveJob: Job? = null
    private val _proceedSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val interactor = AiWebViewInteractor()
    private var targetBaseUrl: String = "gemini.google.com"

    /**
     * 現在ループで処理中の商品。
     * DB保存後に unfetchedProducts Flow が再発火しても
     * この変数が指す商品は変わらないためJAN不一致が発生しない。
     */
    private var currentProduct: ProductMaster? = null

    init {
        startFlowObserver()
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

    private fun startFlowObserver() {
        flowObserveJob?.cancel()
        flowObserveJob = viewModelScope.launch {
            repository.getUnfetchedProducts().collect { products ->
                _uiState.value = _uiState.value.copy(unfetchedProducts = products)
            }
        }
    }

    private fun stopFlowObserver() {
        flowObserveJob?.cancel()
        flowObserveJob = null
    }

    fun setWebView(wv: WebView) {
        interactor.webView = wv
    }

    fun startAutoFetch() {
        if (_uiState.value.unfetchedProducts.isEmpty()) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            stopFlowObserver()

            val productsToFetch = _uiState.value.unfetchedProducts.toList()
            _uiState.value = _uiState.value.copy(isRunning = true, currentIndex = 0)

            var successCount  = 0
            var notFoundCount = 0
            var errorCount    = 0

            for (index in productsToFetch.indices) {
                if (!_uiState.value.isRunning) break

                val product = productsToFetch[index]
                currentProduct = product
                _uiState.value = _uiState.value.copy(currentIndex = index)

                val result = interactor.executeFullFlow(product.janCode, targetBaseUrl) { status ->
                    _uiState.value = _uiState.value.copy(currentStatus = status)
                }

                when {
                    // 商品が見つからなかった（not_found = true）
                    result.success && result.data?.not_found == true -> {
                        // infoFetched = true にマークして未取得リストから除去
                        // → 同じJANが永遠にリストに残り続ける問題を防ぐ
                        markAsNotFound(product)
                        notFoundCount++
                        _uiState.value = _uiState.value.copy(
                            currentStatus = "スキップ（商品情報なし）: ${product.janCode}"
                        )
                        delay(1000)
                    }
                    // 取得成功
                    result.success && result.data != null -> {
                        _uiState.value = _uiState.value.copy(
                            currentStatus = "取得成功: ${product.janCode} → 保存中..."
                        )
                        saveResult(product, result.data)
                        successCount++
                        _uiState.value = _uiState.value.copy(
                            currentStatus = "保存完了: ${product.janCode}"
                        )
                        delay(2000)
                    }
                    // エラー
                    else -> {
                        errorCount++
                        _uiState.value = _uiState.value.copy(
                            currentStatus = "エラー: ${product.janCode} - ${result.errorMessage ?: "不明"}"
                        )
                        delay(3000)
                    }
                }
            }

            currentProduct = null
            _uiState.value = _uiState.value.copy(
                isRunning = false,
                currentIndex = -1,
                currentStatus = "完了 — 成功:${successCount}件 / 情報なし:${notFoundCount}件 / エラー:${errorCount}件"
            )
            startFlowObserver()
        }
    }

    fun stopFetch() {
        fetchJob?.cancel()
        currentProduct = null
        _uiState.value = _uiState.value.copy(
            isRunning = false,
            currentIndex = -1,
            currentStatus = "停止中"
        )
        startFlowObserver()
    }

    /**
     * not_found 商品を infoFetched=true にマークする。
     * 商品情報は空のままで、未取得リストからは除去される。
     * infoSource = AI_NOT_FOUND で記録する。
     */
    private suspend fun markAsNotFound(product: ProductMaster) {
        val updatedProduct = product.copy(
            infoFetched = true,
            infoSource  = if (_uiState.value.aiUrl.orEmpty().contains("gemini"))
                InfoSource.AI_GEMINI else InfoSource.AI_PERPLEXITY,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateProduct(updatedProduct)
    }

    /**
     * 商品情報が見つかった場合の保存。
     * not_found=true の場合は markAsNotFound で処理するためここでは呢んこ。
     */
    private suspend fun saveResult(product: ProductMaster, result: AiResponseData) {
        val updatedProduct = product.copy(
            makerName        = result.maker_name,
            makerNameKana    = result.maker_name_kana,
            productName      = result.product_name,
            productNameKana  = result.product_name_kana,
            spec             = result.spec,
            infoFetched      = true,
            infoSource       = if (_uiState.value.aiUrl.orEmpty().contains("gemini"))
                InfoSource.AI_GEMINI else InfoSource.AI_PERPLEXITY,
            updatedAt        = System.currentTimeMillis()
        )
        repository.updateProduct(updatedProduct)
        if (result.maker_name.isNotEmpty()) {
            val prefix = product.janCode.take(7)
            repository.cacheMaker(prefix, result.maker_name, result.maker_name_kana)
        }
    }

    fun onAcceptResult() {
        viewModelScope.launch {
            val result = _uiState.value.lastResult ?: return@launch
            val product = currentProduct
                ?: _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex)
                ?: return@launch
            saveResult(product, result)
            _uiState.value = _uiState.value.copy(showPreview = false, lastResult = null)
            _proceedSignal.tryEmit(Unit)
        }
    }

    fun onRejectResult() {
        _uiState.value = _uiState.value.copy(showPreview = false, lastResult = null)
        _proceedSignal.tryEmit(Unit)
    }

    fun copyPromptToClipboard(context: Context) {
        val product = currentProduct
            ?: _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex)
            ?: return
        val prompt = AiPromptBuilder.buildPrompt(product.janCode)
        ClipboardHelper.copyToClipboard(context, prompt)
    }

    fun tryManualCapture(context: Context) {
        viewModelScope.launch {
            val product = currentProduct
                ?: _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex)
                ?: return@launch

            var parseResult = interactor.extractCurrentResponse(product.janCode)

            if (parseResult !is AiParseResult.Success) {
                val clipboardText = ClipboardHelper.readFromClipboard(context)
                val cbResult = clipboardText?.let { AiResponseParser.parseResponse(it, product.janCode) }
                if (cbResult is AiParseResult.Success) {
                    parseResult = cbResult
                }
            }

            if (parseResult is AiParseResult.Success) {
                _uiState.value = _uiState.value.copy(
                    lastResult    = parseResult.data,
                    showPreview   = true,
                    currentStatus = "手動取得成功"
                )
            } else {
                val errorMsg = when (parseResult) {
                    is AiParseResult.JanMismatch  -> "JAN不一致: ${parseResult.actual}"
                    is AiParseResult.InvalidFormat -> "形式エラー"
                    is AiParseResult.NotFound      -> "見つかりません"
                    else                           -> "手動取得失敗"
                }
                _uiState.value = _uiState.value.copy(currentStatus = errorMsg)
            }
        }
    }
}
