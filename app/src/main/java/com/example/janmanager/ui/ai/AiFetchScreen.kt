package com.example.janmanager.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.ui.ai.components.AiResultPreview
import com.example.janmanager.ui.ai.components.AiWebViewWrapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFetchScreen(
    viewModel: AiFetchViewModel = hiltViewModel()
) {
    val unfetched by viewModel.unfetchedProducts.collectAsState()
    val fetchState by viewModel.fetchState.collectAsState()
    val aiSelection by viewModel.aiSelection.collectAsState()
    val targetProduct by viewModel.targetProduct.collectAsState()
    val previewQueue by viewModel.previewQueue.collectAsState()

    var webViewInstance by remember { mutableStateOf<android.webkit.WebView?>(null) }

    // Init js evaluator callback
    viewModel.setJsEvaluator { js, callback ->
        webViewInstance?.evaluateJavascript(js, callback)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI情報取得") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Upper Area: List & Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("未取得件数: ${unfetched.size} 件")
                    Text("状態: ${fetchState.name}")
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fetchState == FetchState.IDLE) {
                        Button(onClick = { viewModel.startBulkFetch() }, enabled = unfetched.isNotEmpty()) {
                            Text("一括取得開始")
                        }
                    } else {
                        Button(onClick = { viewModel.stopFetch() }) {
                            Text("停止")
                        }
                    }
                    if (previewQueue.isNotEmpty()) {
                        Button(onClick = { viewModel.savePreviewToDb() }) {
                            Text("取得済みを保存(${previewQueue.size})")
                        }
                    }
                }

                if (targetProduct != null) {
                    Text("現在処理中: ${targetProduct?.janCode}", style = MaterialTheme.typography.titleMedium)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(previewQueue) { product ->
                        AiResultPreview(product = product)
                    }
                }
            }

            // Lower Area: WebView Browser
            val url = if (aiSelection == "PERPLEXITY") "https://www.perplexity.ai/" else "https://gemini.google.com/"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(8.dp)
            ) {
                AiWebViewWrapper(
                    url = url,
                    onWebViewCreated = { webViewInstance = it }
                )
            }
        }
    }
}
