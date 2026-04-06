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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    // Constant focus for Bluetooth HID scanner
    LaunchedEffect(Unit) {
        viewModel.loadGroup(groupId)
        while (true) {
            delay(500)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {}
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Unified Input Area
            Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                OutlinedTextField(
                    value = barcodeInput,
                    onValueChange = { 
                        val normalized = com.example.janmanager.util.Normalizer.toHalfWidth(it)
                        if (normalized.all { c -> c.isDigit() }) barcodeInput = normalized 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
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
                    label = { Text("JANコード") },
                    placeholder = { Text("スキャンまたは手入力") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (barcodeInput.isNotEmpty()) {
                            viewModel.processBarcode(barcodeInput)
                            barcodeInput = ""
                        }
                    }),
                    singleLine = true
                )
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
                                Badge(containerColor = Color.Red.copy(alpha = 0.8f)) { 
                                    Text("新規", color = Color.White, modifier = Modifier.padding(horizontal = 4.dp)) 
                                }
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
