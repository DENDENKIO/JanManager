package com.example.janmanager.ui.group

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScanScreen(
    groupId: Long,
    onNavigateBack: () -> Unit,
    viewModel: GroupScanViewModel = hiltViewModel()
) {
    val group by viewModel.group.collectAsState()
    val scannedItems by viewModel.scannedItems.collectAsState()
    val lastMessage by viewModel.lastMessage.collectAsState()
    
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var barcodeInput by remember { mutableStateOf("") }
    var isManualInput by remember { mutableStateOf(false) }

    LaunchedEffect(isManualInput) {
        viewModel.loadGroup(groupId)
        if (!isManualInput) {
            while (true) {
                delay(1000)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.hide()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.groupName ?: "グループスキャン") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Input Area
            Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
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
                    Text("Bluetoothスキャナー準備完了", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                }
            }
            
            // Status Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lastMessage.ifEmpty { "バーコードをスキャンしてください" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (lastMessage.startsWith("重複")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Recent Scans List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scannedItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.alreadyInGroup) Color.LightGray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, fontWeight = FontWeight.Bold)
                                Text(item.janCode, style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.isNew) {
                                Badge(containerColor = Color.Red) { Text("新規", color = Color.White) }
                            }
                            if (item.alreadyInGroup) {
                                Text("重複", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
