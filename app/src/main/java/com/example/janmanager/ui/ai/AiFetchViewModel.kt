package com.example.janmanager.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.settings.SettingsDataStore
import com.example.janmanager.util.AiPromptBuilder
import com.example.janmanager.util.AiResponseData
import com.example.janmanager.util.AiResponseParser
import com.example.janmanager.util.WebViewJsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FetchState {
    IDLE, PASTING, SENDING, WAITING_RESPONSE, DONE_ITEM, ERROR
}

@HiltViewModel
class AiFetchViewModel @Inject constructor(
    private val repository: ProductRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val unfetchedProducts = repository.getUnfetchedProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiSelection = settingsDataStore.aiSelectionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "GEMINI")

    private val _fetchState = MutableStateFlow(FetchState.IDLE)
    val fetchState = _fetchState.asStateFlow()

    private val _targetProduct = MutableStateFlow<ProductMaster?>(null)
    val targetProduct = _targetProduct.asStateFlow()

    private val _parsedResponse = MutableStateFlow<AiResponseData?>(null)
    val parsedResponse = _parsedResponse.asStateFlow()

    private val _previewQueue = MutableStateFlow<List<ProductMaster>>(emptyList())
    val previewQueue = _previewQueue.asStateFlow()

    // 取得対象JANリスト
    private var bulkTargetList: List<ProductMaster> = emptyList()
    var isBulkActive = false
        private set

    // EvaluateJS Callback Wrapper
    private var evaluateJs: ((String, (String) -> Unit) -> Unit)? = null

    fun setJsEvaluator(evaluator: (String, (String) -> Unit) -> Unit) {
        this.evaluateJs = evaluator
    }

    // セレクタ等 (本来はDataStoreのカスタマイズJSON設定からパースする等)
    private val promptInputSelector = "div[data-placeholder*='プロンプト'], textarea.textarea"
    private val sendButtonSelector = "button[aria-label*='送信'], button.send-button"
    private val responseSelector = ".model-response-text, .prose"

    fun startBulkFetch() {
        if (unfetchedProducts.value.isEmpty()) return
        bulkTargetList = ArrayList(unfetchedProducts.value)
        isBulkActive = true
        _previewQueue.value = emptyList()
        fetchNextInBulk()
    }

    fun stopFetch() {
        isBulkActive = false
        _fetchState.value = FetchState.IDLE
    }
    
    fun startSingleFetch(product: ProductMaster) {
        isBulkActive = false
        _targetProduct.value = product
        executeFetchCycle(product.janCode)
    }

    private fun fetchNextInBulk() {
        if (!isBulkActive || bulkTargetList.isEmpty()) {
            _fetchState.value = FetchState.IDLE
            isBulkActive = false
            return
        }
        val current = bulkTargetList.first()
        bulkTargetList = bulkTargetList.drop(1)
        _targetProduct.value = current
        
        executeFetchCycle(current.janCode)
    }

    private fun executeFetchCycle(janCode: String) {
        viewModelScope.launch {
            _fetchState.value = FetchState.PASTING
            _parsedResponse.value = null
            
            val prompt = AiPromptBuilder.buildPrompt(janCode)
            val pasteJs = WebViewJsHelper.getInjectPromptJs(promptInputSelector, prompt)
            
            suspendJsEvaluation(pasteJs)
            delay(1000) // UI反映待ち

            _fetchState.value = FetchState.SENDING
            val sendJs = WebViewJsHelper.getClickSendJs(sendButtonSelector)
            val sendResult = suspendJsEvaluation(sendJs)
            
            if (sendResult != "true") {
                // 送信失敗フォールバック（手動用）
                _fetchState.value = FetchState.ERROR
                return@launch
            }

            _fetchState.value = FetchState.WAITING_RESPONSE
            pollResponse(janCode)
        }
    }

    private suspend fun pollResponse(janCode: String) {
        val extractJs = WebViewJsHelper.getExtractResponseJs(responseSelector)
        var attempts = 0
        
        while (attempts < 60 && (_fetchState.value == FetchState.WAITING_RESPONSE)) {
            delay(2000) // 2秒置き
            val rawRes = suspendJsEvaluation(extractJs)
            // WebView JS評価結果は"..."のようにダブルクォートで来るため除去するかAiResponseParser内で処理
            val cleanRes = rawRes.removeSurrounding("\"").replace("\\n", "\n").replace("\\\"", "\"")
            
            val parsedResult = AiResponseParser.parseResponse(cleanRes, janCode)
            
            if (parsedResult != null && (parsedResult.jan_code == janCode || parsedResult.not_found)) {
                // 生成完了検知
                _parsedResponse.value = parsedResult
                _fetchState.value = FetchState.DONE_ITEM
                
                // BulkならPreview追記して次へ、Singleなら停止
                val updatedProduct = applyParsedToProduct(_targetProduct.value, parsedResult)
                if (updatedProduct != null) {
                    val q = _previewQueue.value.toMutableList()
                    q.add(0, updatedProduct)
                    _previewQueue.value = q
                }

                if (isBulkActive) {
                    delay(1500) // API制限回避のため少々待機
                    fetchNextInBulk()
                }
                return
            }
            attempts++
        }
        _fetchState.value = FetchState.ERROR
    }

    private fun applyParsedToProduct(origin: ProductMaster?, res: AiResponseData): ProductMaster? {
        if (origin == null) return null
        if (res.not_found) {
            return origin.copy(infoFetched = true, spec = "Not Found")
        }
        return origin.copy(
            makerName = res.maker_name,
            makerNameKana = res.maker_name_kana,
            productName = res.product_name,
            productNameKana = res.product_name_kana,
            spec = res.spec,
            infoFetched = true
        )
    }

    fun savePreviewToDb() {
        viewModelScope.launch {
            _previewQueue.value.forEach { product ->
                if (product.infoFetched) {
                    repository.updateProduct(product.copy(updatedAt = System.currentTimeMillis()))
                }
            }
            _previewQueue.value = emptyList()
        }
    }

    // Helper wrapper to make JS evaluation appear sequential
    private suspend fun suspendJsEvaluation(jsText: String): String = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        if (evaluateJs == null) {
            cont.resume("null", null)
        } else {
            evaluateJs!!.invoke(jsText) { result ->
                if (cont.isActive) cont.resume(result, null)
            }
        }
    }
}
