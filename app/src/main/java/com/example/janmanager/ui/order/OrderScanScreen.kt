package com.example.janmanager.ui.order

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScanScreen(
    sessionId: Long,
    onComplete: () -> Unit,
    viewModel: OrderScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var barcodeInput by remember { mutableStateOf("") }
    var isManualInput by remember { mutableStateOf(false) }

    LaunchedEffect(isManualInput) {
        if (!isManualInput) {
            while (true) {
                delay(1000)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.hide()
                } catch (e: Exception) {}
            }
        }
    }

    // Handle duplicate toast
    LaunchedEffect(uiState.lastScannedJan, uiState.isDuplicate) {
        if (uiState.isDuplicate) {
            Toast.makeText(context, "重複: ${uiState.lastScannedJan} は既に追加されています", Toast.LENGTH_SHORT).show()
        }
    }

    // Discontinued confirmation dialog
    uiState.pendingDiscontinuedProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDiscontinued() },
            title = { Text("⚠️ 終売品確認") },
            text = { 
                Column {
                    Text("この商品は終売として登録されています。発注リストに追加しますか？")
                    Spacer(Modifier.height(8.dp))
                    Text("商品名: ${product.productName}", fontWeight = FontWeight.Bold)
                    Text("JAN: ${product.janCode}")
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmDiscontinued() }) {
                    Text("追加する")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.cancelDiscontinued() }) {
                    Text("キャンセル")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("発注スキャン") },
                actions = {
                    IconButton(onClick = { 
                        isManualInput = !isManualInput 
                        barcodeInput = ""
                    }) {
                        Icon(
                            if (isManualInput) Icons.Default.QrCodeScanner else Icons.Default.Keyboard,
                            contentDescription = "入力モード切替"
                        )
                    }
                    Button(onClick = { 
                        viewModel.completeSession() 
                        onComplete()
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("完了")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Input Area
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isManualInput) {
                    OutlinedTextField(
                        value = barcodeInput,
                        onValueChange = { if (it.all { c -> c.isDigit() }) barcodeInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent {
                                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER && it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    if (barcodeInput.isNotEmpty()) {
                                        viewModel.processBarcode(barcodeInput)
                                        barcodeInput = ""
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                        label = { Text("JANコードを手入力") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (barcodeInput.isNotEmpty()) {
                                viewModel.processBarcode(barcodeInput)
                                barcodeInput = ""
                            }
                        }),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = barcodeInput,
                        onValueChange = { barcodeInput = it },
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0f)
                            .focusRequester(focusRequester)
                            .onKeyEvent {
                                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER && it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    if (barcodeInput.isNotEmpty()) {
                                        viewModel.processBarcode(barcodeInput)
                                        barcodeInput = ""
                                    }
                                    true
                                } else { false }
                            }
                    )
                    Text("Bluetoothスキャナー準備完了", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Session Info
            Text(
                text = "セッション: ${uiState.currentSession?.sessionName ?: "---"}",
                style = MaterialTheme.typography.bodySmall
            )

            // Counter
            Card(
                modifier = Modifier.size(200.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("スキャン数", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = uiState.scanCount.toString(),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Last Scan Result
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isDiscontinued) Color.Red.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (uiState.errorMessage.isNotEmpty()) {
                        Text(uiState.errorMessage, color = Color.Red, fontWeight = FontWeight.Bold)
                    } else if (uiState.lastScannedJan.isNotEmpty()) {
                        Text("最後にスキャンした商品:", style = MaterialTheme.typography.labelSmall)
                        Text(uiState.lastProductName, style = MaterialTheme.typography.titleLarge)
                        Text(uiState.lastScannedJan, style = MaterialTheme.typography.bodyMedium)
                        
                        if (uiState.isDiscontinued) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color.Red, MaterialTheme.shapes.small)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("【終売品警告】", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text("スキャン待ち...", color = Color.Gray)
                    }
                }
            }
        }
    }
}
