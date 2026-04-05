package com.example.janmanager.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.ui.ai.components.AiResultPreview
import com.example.janmanager.ui.ai.components.AiWebViewWrapper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFetchScreen(
    viewModel: AiFetchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll to current item
    LaunchedEffect(uiState.currentIndex) {
        if (uiState.currentIndex >= 0 && uiState.currentIndex < uiState.unfetchedProducts.size) {
            listState.animateScrollToItem(uiState.currentIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI商品情報取得") },
                actions = {
                    if (!uiState.isRunning) {
                        IconButton(onClick = { viewModel.startAutoFetch() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "開始", tint = Color.Green)
                        }
                    } else {
                        IconButton(onClick = { viewModel.stopFetch() }) {
                            Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color.Red)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Upper half: List and Status
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Status Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ステータス: ${uiState.currentStatus}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Unfetched List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(uiState.unfetchedProducts) { index, product ->
                        val isCurrent = index == uiState.currentIndex
                        ListItem(
                            headlineContent = { Text(product.janCode) },
                            supportingContent = { Text(product.makerName.ifEmpty { "(メーカー不明)" }) },
                            colors = if (isCurrent) {
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                ListItemDefaults.colors()
                            },
                            trailingContent = {
                                if (isCurrent && uiState.isRunning) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }

                // Manual Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { viewModel.copyPromptToClipboard(context) }) {
                        Text("プロンプトコピー")
                    }
                    Button(onClick = { viewModel.tryManualCapture(context) }) {
                        Text("取り込み")
                    }
                }
            }

            // Lower half: WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Gray)
            ) {
                val currentUrl = uiState.aiUrl
                if (currentUrl != null) {
                    AiWebViewWrapper(
                        url = currentUrl,
                        onWebViewCreated = { viewModel.setWebView(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Overlay Preview Dialog
                if (uiState.showPreview && uiState.lastResult != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AiResultPreview(
                            result = uiState.lastResult!!,
                            onAccept = { viewModel.onAcceptResult() },
                            onReject = { viewModel.onRejectResult() }
                        )
                    }
                }
            }
        }
    }
}
