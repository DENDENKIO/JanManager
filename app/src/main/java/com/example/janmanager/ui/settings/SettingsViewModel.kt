package com.example.janmanager.ui.settings

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.settings.SettingsDataStore
import com.example.janmanager.util.WebViewJsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val aiSelection: String = "GEMINI",
    val pasteMode: String = "AUTO",
    val isItfEnabled: Boolean = false,
    val scanSoundEnabled: Boolean = true,
    val inputSelector: String = "",
    val sendButtonSelector: String = "",
    val responseSelector: String = "",
    val detectionStatus: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val productRepository: ProductRepository
) : ViewModel() {

    val aiSelection = settingsDataStore.aiSelectionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "GEMINI")
    
    val pasteMode = settingsDataStore.pasteModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")

    val isItfEnabled = settingsDataStore.isItfEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanSoundEnabled = settingsDataStore.scanSoundEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = settingsDataStore.selectorConfigFlow.first()
            if (config.isNotEmpty()) {
                val parts = config.split("|")
                if (parts.size == 3) {
                    _uiState.value = _uiState.value.copy(
                        inputSelector = parts[0],
                        sendButtonSelector = parts[1],
                        responseSelector = parts[2]
                    )
                }
            }
        }
    }

    fun setAiSelection(selection: String) {
        viewModelScope.launch { settingsDataStore.setAiSelection(selection) }
    }

    fun setPasteMode(mode: String) {
        viewModelScope.launch { settingsDataStore.setPasteMode(mode) }
    }

    fun setItfEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setItfEnabled(enabled) }
    }

    fun setScanSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setScanSoundEnabled(enabled) }
    }

    fun updateSelectors(input: String, send: String, response: String) {
        _uiState.value = _uiState.value.copy(
            inputSelector = input,
            sendButtonSelector = send,
            responseSelector = response
        )
        viewModelScope.launch {
            settingsDataStore.setSelectorConfig("$input|$send|$response")
        }
    }

    fun detectSelectors(webView: WebView?) {
        if (webView == null) return
        val currentAi = aiSelection.value
        _uiState.value = _uiState.value.copy(detectionStatus = "検出中...")
        
        val inputCandidates = if (currentAi == "PERPLEXITY") WebViewJsHelper.PERPLEXITY_INPUT_SELECTORS else WebViewJsHelper.GEMINI_INPUT_SELECTORS
        val sendCandidates = if (currentAi == "PERPLEXITY") WebViewJsHelper.PERPLEXITY_SEND_SELECTORS else WebViewJsHelper.GEMINI_SEND_SELECTORS
        val respCandidates = if (currentAi == "PERPLEXITY") WebViewJsHelper.PERPLEXITY_RESPONSE_SELECTORS else WebViewJsHelper.GEMINI_RESPONSE_SELECTORS

        val inputJson = inputCandidates.joinToString(",") { s -> "'$s'" }
        val sendJson = sendCandidates.joinToString(",") { s -> "'$s'" }
        val respJson = respCandidates.joinToString(",") { s -> "'$s'" }

        val js = """
            (function() {
                var res = { input: "", send: "", response: "" };
                var inputList = [$inputJson];
                var sendList = [$sendJson];
                var respList = [$respJson];

                for (var s of inputList) {
                    if (document.querySelector(s)) { res.input = s; break; }
                }
                for (var s of sendList) {
                    var el = document.querySelector(s);
                    if (el) { res.send = s; break; }
                }
                for (var s of respList) {
                    if (document.querySelector(s)) { res.response = s; break; }
                }
                
                // Fallback heuristic if nothing found in candidates
                if (!res.input) {
                    var fallback = document.querySelector('div[contenteditable="true"], textarea');
                    if (fallback) res.input = fallback.tagName.toLowerCase() + (fallback.getAttribute('contenteditable') ? '[contenteditable="true"]' : '');
                }
                
                return JSON.stringify(res);
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            try {
                val clean = result.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")
                val json = kotlinx.serialization.json.Json.parseToJsonElement(clean).let { 
                    it as? kotlinx.serialization.json.JsonObject 
                }
                if (json != null) {
                    val input = json["input"]?.toString()?.removeSurrounding("\"") ?: ""
                    val send = json["send"]?.toString()?.removeSurrounding("\"") ?: ""
                    val resp = json["response"]?.toString()?.removeSurrounding("\"") ?: ""
                    updateSelectors(input, send, resp)
                    _uiState.value = _uiState.value.copy(detectionStatus = if (input.isNotEmpty()) "完了" else "一部未検出")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(detectionStatus = "失敗")
            }
        }
    }

    fun exportCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            productRepository.searchProducts("", com.example.janmanager.data.repository.SearchType.JAN).first().let { products ->
                val csv = StringBuilder()
                csv.append("JAN,メーカー名,商品名,規格,ステータス,取得済み\n")
                products.forEach { p ->
                    csv.append("${p.janCode},${p.makerName},${p.productName},${p.spec},${p.status},${p.infoFetched}\n")
                }
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csv.toString().toByteArray())
                }
            }
        }
    }

    fun importCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val lines = reader.readLines()
                    if (lines.isEmpty()) return@launch
                    
                    // Skip header if it looks like one (contains "JAN" or "メーカー")
                    val startIndex = if (lines[0].contains("JAN") || lines[0].contains("メーカー")) 1 else 0
                    
                    for (i in startIndex until lines.size) {
                        val line = lines[i]
                        if (line.isBlank()) continue
                        
                        val cols = line.split(",")
                        if (cols.size >= 4) {
                            val jan = cols[0].trim()
                            val product = ProductMaster(
                                janCode = jan,
                                makerJanPrefix = com.example.janmanager.util.JanCodeUtil.extractMakerPrefix(jan),
                                makerName = cols[1].trim(),
                                makerNameKana = "",
                                productName = cols[2].trim(),
                                productNameKana = "",
                                spec = cols[3].trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                            productRepository.insertProduct(product)
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(detectionStatus = "インポート完了")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(detectionStatus = "インポート失敗")
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            try {
                productRepository.deleteAllProducts()
                _uiState.value = _uiState.value.copy(detectionStatus = "データ削除完了")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(detectionStatus = "削除失敗")
            }
        }
    }
}
