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

    /**
     * 修正: 現在ループで処理中の商品をスナップショットとして保持するフィールド。
     * DB保存後に unfetchedProducts Flow が再発火してリストが縮小しても、
     * このフィールドが指す商品は変わらないためJAN不一致が発生しない。
     */
    private var currentProduct: ProductMaster? = null

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
            // 開始時のリストをスナップショットとして固定する
            // → DB保存による Flow 再発火でリストが縮小してもスナップショットは変わらない
            val productsToFetch = _uiState.value.unfetchedProducts.toList()
            _uiState.value = _uiState.value.copy(isRunning = true, currentIndex = 0)

            for (index in productsToFetch.indices) {
                if (!_uiState.value.isRunning) break

                // 修正: スナップショットから取得し currentProduct に保持
                // onAcceptResult・ copyPromptToClipboard・ tryManualCapture が
                // この値を参照するのでリスト履歴の影響を受けない
                val product = productsToFetch[index]
                currentProduct = product
                _uiState.value = _uiState.value.copy(currentIndex = index)

                val result = interactor.executeFullFlow(product.janCode, targetBaseUrl) { status ->
                    _uiState.value = _uiState.value.copy(currentStatus = status)
                }

                if (result.success && result.data != null) {
                    _uiState.value = _uiState.value.copy(
                        currentStatus = "取得成功: ${product.janCode} → 保存中..."
                    )
                    // 自動保存（プレビュー確認なし）
                    saveResult(product, result.data)
                    _uiState.value = _uiState.value.copy(
                        currentStatus = "保存完了: ${product.janCode}"
                    )
                    // 次の商品へ進む前に少し待機（AIサイトの負荷軽減）
                    delay(2000)
                } else {
                    _uiState.value = _uiState.value.copy(
                        currentStatus = "エラー: ${product.janCode} - ${result.errorMessage ?: "不明"}"
                    )
                    delay(3000)
                }
            }
            currentProduct = null
            _uiState.value = _uiState.value.copy(
                isRunning = false,
                currentStatus = "完了（${productsToFetch.size}件処理）"
            )
        }
    }

    fun stopFetch() {
        currentProduct = null
        _uiState.value = _uiState.value.copy(isRunning = false, currentStatus = "停止中")
        fetchJob?.cancel()
    }

    private suspend fun saveResult(product: ProductMaster, result: AiResponseData) {
        if (result.not_found) return  // 見つからなかった商品はスキップ

        val updatedProduct = product.copy(
            makerName = result.maker_name,
            makerNameKana = result.maker_name_kana,
            productName = result.product_name,
            productNameKana = result.product_name_kana,
            spec = result.spec,
            infoFetched = true,
            infoSource = if (_uiState.value.aiUrl.orEmpty().contains("gemini"))
                InfoSource.AI_GEMINI else InfoSource.AI_PERPLEXITY,
            updatedAt = System.currentTimeMillis()
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
            // 修正: currentProduct スナップショットを優先する
            // → saveResult 後に unfetchedProducts が再発火しても、
            //   すでに product を確定しているのでJAN不一致しない
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
        // 修正: currentProduct スナップショットを優先する
        val product = currentProduct
            ?: _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex)
            ?: return
        val prompt = AiPromptBuilder.buildPrompt(product.janCode)
        ClipboardHelper.copyToClipboard(context, prompt)
    }

    fun tryManualCapture(context: Context) {
        viewModelScope.launch {
            // 修正: currentProduct スナップショットを優先する
            val product = currentProduct
                ?: _uiState.value.unfetchedProducts.getOrNull(_uiState.value.currentIndex)
                ?: return@launch

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
