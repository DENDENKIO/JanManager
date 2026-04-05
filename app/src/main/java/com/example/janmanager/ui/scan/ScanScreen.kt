package com.example.janmanager.ui.scan

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.ui.scan.components.ConfirmMode
import com.example.janmanager.ui.scan.components.ContinuousMode
import com.example.janmanager.ui.scan.components.LinkageMode
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: ScanViewModel = hiltViewModel()) {
    val currentTab by viewModel.currentTab.collectAsState()
    val tabs = ScanModeTab.entries.toTypedArray()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputBuffer by remember { mutableStateOf("") }
    var isManualInput by remember { mutableStateOf(false) }

    // Bluetooth HID input maintenance
    LaunchedEffect(isManualInput, currentTab) {
        if (!isManualInput) {
            while (true) {
                delay(1000)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.hide()
                } catch (e: Exception) {
                    // Ignore focus errors
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("スキャン") },
                actions = {
                    IconButton(onClick = { 
                        isManualInput = !isManualInput 
                        inputBuffer = ""
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
            TabRow(selectedTabIndex = currentTab.ordinal) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = currentTab.ordinal == index,
                        onClick = { viewModel.setTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    ScanModeTab.CONTINUOUS -> "連続"
                                    ScanModeTab.CONFIRM -> "確認"
                                    ScanModeTab.LINKAGE -> "紐づけ"
                                }
                            )
                        }
                    )
                }
            }

            // Input Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isManualInput) {
                    OutlinedTextField(
                        value = inputBuffer,
                        onValueChange = { if (it.all { char -> char.isDigit() }) inputBuffer = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent {
                                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER && it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    if (inputBuffer.isNotEmpty()) {
                                        viewModel.processBarcode(inputBuffer)
                                        inputBuffer = ""
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                        label = { Text("JANコードを手入力") },
                        placeholder = { Text("13桁または8桁") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputBuffer.isNotEmpty()) {
                                    viewModel.processBarcode(inputBuffer)
                                    inputBuffer = ""
                                }
                            }
                        ),
                        singleLine = true
                    )
                } else {
                    // Hidden field for Bluetooth HID (Minimal size but not zero, and transparent)
                    OutlinedTextField(
                        value = inputBuffer,
                        onValueChange = { inputBuffer = it },
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0f)
                            .focusRequester(focusRequester)
                            .onKeyEvent { event ->
                                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    if (inputBuffer.isNotEmpty()) {
                                        viewModel.processBarcode(inputBuffer.trim())
                                        inputBuffer = ""
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                    Text(
                        "Bluetoothスキャナー準備完了",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Content
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    ScanModeTab.CONTINUOUS -> ContinuousMode(viewModel)
                    ScanModeTab.CONFIRM -> ConfirmMode(viewModel)
                    ScanModeTab.LINKAGE -> LinkageMode(viewModel)
                }
            }
        }
    }
}
