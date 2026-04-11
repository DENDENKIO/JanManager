package com.example.janmanager.ui.scan

import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
fun ScanScreen(
    onNavigateToAiFetch: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val tabs = ScanModeTab.entries.toTypedArray()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputBuffer by remember { mutableStateOf("") }

    // Ensure focus for scanner (always focused if possible, but let keyboard show normally)
    LaunchedEffect(currentTab) {
        delay(300)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("スキャン") }
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
                        onClick = { 
                            viewModel.setTab(tab)
                            inputBuffer = "" // Reset buffer on tab change
                        },
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

            // Input Area (Unified Manual/Scanner Input)
            AnimatedVisibility(
                visible = currentTab != ScanModeTab.CONTINUOUS,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputBuffer,
                        onValueChange = { 
                            val normalized = com.example.janmanager.util.Normalizer.toHalfWidth(it)
                            if (normalized.all { char -> char.isDigit() }) inputBuffer = normalized 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
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
                        label = { Text("JANコード") },
                        placeholder = { Text("スキャンまたは手入力") },
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
                }
            }

            // Content
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    ScanModeTab.CONTINUOUS -> ContinuousMode(viewModel)
                    ScanModeTab.CONFIRM -> ConfirmMode(
                        viewModel = viewModel,
                        onNavigateToAiFetch = onNavigateToAiFetch
                    )
                    ScanModeTab.LINKAGE -> LinkageMode(viewModel)
                }
            }
        }
    }
}
