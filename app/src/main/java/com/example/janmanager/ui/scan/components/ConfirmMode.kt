package com.example.janmanager.ui.scan.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.janmanager.ui.ai.components.AiResultPreview
import com.example.janmanager.ui.ai.components.AiWebViewWrapper
import com.example.janmanager.ui.scan.ScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmMode(
    viewModel: ScanViewModel,
    onNavigateToAiFetch: () -> Unit
) {
    val product by viewModel.confirmProduct.collectAsState()
    val lastBarcode by viewModel.lastConfirmBarcode.collectAsState()
    
    val showAiSheet by viewModel.showAiSheet.collectAsState()
    val aiUrl by viewModel.aiUrl.collectAsState()
    val aiStatus by viewModel.aiFetchStatus.collectAsState()
    val aiResult by viewModel.aiResultPreview.collectAsState()
    
    val productTags by viewModel.confirmProductGroups.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("確認モード", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("スキャンされたJAN: $lastBarcode", style = MaterialTheme.typography.titleMedium)
                
                if (product != null) {
                    Text("商品名: ${product?.productName}", modifier = Modifier.padding(top = 8.dp))
                    Text("メーカー: ${product?.makerName}")
                    Text("規格: ${product?.spec}")
                    
                    if (productTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            productTags.forEach { colorInt ->
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(colorInt), MaterialTheme.shapes.extraSmall)
                                )
                            }
                        }
                    }

                    if (product?.infoFetched == true) {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("取得済み") },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (product != null) {
                        SuggestionChip(
                            onClick = { },
                            label = { Text("AI未取得", color = MaterialTheme.colorScheme.onTertiaryContainer) },
                            modifier = Modifier.padding(top = 8.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        )
                    }
                } else if (lastBarcode.isNotEmpty()) {
                    Text("商品未登録", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        Button(
            onClick = { viewModel.openAiFetchSheet() },
            modifier = Modifier.fillMaxWidth(),
            enabled = lastBarcode.isNotEmpty()
        ) {
            Text(if (product?.infoFetched == true) "商品情報を再取得" else "AI情報取得")
        }

        OutlinedButton(
            onClick = { onNavigateToAiFetch() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("未取得商品の一括AI取得画面へ")
        }
    }

    if (showAiSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAiFetchSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header / Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI情報取得: $lastBarcode", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(aiStatus, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.startBatchAiFetch() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                            Text("一括")
                        }
                        Button(onClick = { viewModel.startSingleAiFetch() }) {
                            Text("実行")
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    val currentUrl = aiUrl
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
                    
                    if (aiResult != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AiResultPreview(
                                result = aiResult!!,
                                onAccept = { viewModel.acceptAiResult() },
                                onReject = { viewModel.closeAiFetchSheet() }
                            )
                        }
                    }
                }
            }
        }
    }
}
