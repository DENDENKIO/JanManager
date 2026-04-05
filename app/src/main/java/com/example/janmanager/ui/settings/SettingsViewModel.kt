package com.example.janmanager.ui.settings

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.settings.SettingsDataStore
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
        _uiState.value = _uiState.value.copy(detectionStatus = "検出中...")
        
        val js = """
            (function() {
                var res = { input: "", send: "", response: "" };
                // Heuristic for input
                var input = document.querySelector('div[contenteditable="true"], textarea');
                if (input) res.input = input.tagName.toLowerCase() + (input.getAttribute('contenteditable') ? '[contenteditable="true"]' : '');
                
                // Heuristic for send button
                var buttons = document.querySelectorAll('button');
                for (var b of buttons) {
                    if (b.innerText.includes('送信') || b.innerText.includes('Send') || b.getAttribute('aria-label')?.includes('Send')) {
                        res.send = 'button' + (b.getAttribute('aria-label') ? '[aria-label*="' + b.getAttribute('aria-label').split(' ')[0] + '"]' : '');
                        break;
                    }
                }
                
                // Heuristic for response
                var responses = document.querySelectorAll('.message-content, .prose, .model-response-text');
                if (responses.length > 0) res.response = '.' + responses[responses.length-1].className.split(' ')[0];
                
                return JSON.stringify(res);
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            // result is a JSON string
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
                    _uiState.value = _uiState.value.copy(detectionStatus = "完了")
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
}
