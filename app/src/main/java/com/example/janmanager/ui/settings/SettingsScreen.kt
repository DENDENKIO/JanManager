package com.example.janmanager.ui.settings

import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.ui.ai.components.AiWebViewWrapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val aiSelection by viewModel.aiSelection.collectAsState()
    val pasteMode by viewModel.pasteMode.collectAsState()
    val isItfEnabled by viewModel.isItfEnabled.collectAsState()
    val scanSoundEnabled by viewModel.scanSoundEnabled.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportCsv(context, it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("設定") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // AI Selection
            SettingsSection(title = "AI商品情報取得設定") {
                Text("使用するAI", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = aiSelection == "GEMINI",
                        onClick = { viewModel.setAiSelection("GEMINI") },
                        label = { Text("Gemini") }
                    )
                    FilterChip(
                        selected = aiSelection == "PERPLEXITY",
                        onClick = { viewModel.setAiSelection("PERPLEXITY") },
                        label = { Text("Perplexity") }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("貼り付け方式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pasteMode == "AUTO",
                        onClick = { viewModel.setPasteMode("AUTO") },
                        label = { Text("自動") }
                    )
                    FilterChip(
                        selected = pasteMode == "MANUAL",
                        onClick = { viewModel.setPasteMode("MANUAL") },
                        label = { Text("手動(クリップボード)") }
                    )
                }
            }

            // WebView Login & Selector Detection
            SettingsSection(title = "AIサイトログイン・セレクタ調整") {
                var showWebView by remember { mutableStateOf(false) }
                
                Button(onClick = { showWebView = !showWebView }) {
                    Text(if (showWebView) "WebViewを閉じる" else "WebViewを表示してログイン")
                }
                
                if (showWebView) {
                    val url = if (aiSelection == "PERPLEXITY") "https://www.perplexity.ai/" else "https://gemini.google.com/app?hl=ja"
                    Column(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                        AiWebViewWrapper(
                            url = url,
                            onWebViewCreated = { webViewInstance = it },
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(uiState.detectionStatus, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { viewModel.detectSelectors(webViewInstance) }) {
                                Text("セレクタ自動検出")
                            }
                        }
                    }
                }
                
                OutlinedTextField(
                    value = uiState.inputSelector,
                    onValueChange = { viewModel.updateSelectors(it, uiState.sendButtonSelector, uiState.responseSelector) },
                    label = { Text("入力欄セレクタ") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uiState.sendButtonSelector,
                    onValueChange = { viewModel.updateSelectors(uiState.inputSelector, it, uiState.responseSelector) },
                    label = { Text("送信ボタンセレクタ") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uiState.responseSelector,
                    onValueChange = { viewModel.updateSelectors(uiState.inputSelector, uiState.sendButtonSelector, it) },
                    label = { Text("回答エリアセレクタ") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Scanner Settings
            SettingsSection(title = "スキャン設定") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ITF(14桁)読み取りを許可", modifier = Modifier.weight(1f))
                    Switch(checked = isItfEnabled, onCheckedChange = { viewModel.setItfEnabled(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("スキャン成功音", modifier = Modifier.weight(1f))
                    Switch(checked = scanSoundEnabled, onCheckedChange = { viewModel.setScanSoundEnabled(it) })
                }
            }

            // Data Management
            SettingsSection(title = "データ管理") {
                Button(
                    onClick = { exportLauncher.launch("product_master_${System.currentTimeMillis()}.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("商品マスタをCSVエクスポート")
                }
            }
            
            // App Info
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray)
                Text("JAN Manager v1.0.0", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
