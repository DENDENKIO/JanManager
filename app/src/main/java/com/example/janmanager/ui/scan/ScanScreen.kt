package com.example.janmanager.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janmanager.ui.scan.components.ConfirmMode
import com.example.janmanager.ui.scan.components.ContinuousMode
import com.example.janmanager.ui.scan.components.LinkageMode
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: ScanViewModel = hiltViewModel()) {
    val currentTab by viewModel.currentTab.collectAsState()
    val tabs = ScanModeTab.values()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputBuffer by remember { mutableStateOf("") }

    // Always maintain focus on the invisible TextField for HID input
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.hide()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("スキャン") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hidden TextField capturing Bluetooth HID input
            TextField(
                value = inputBuffer,
                onValueChange = { inputBuffer = it },
                modifier = Modifier
                    .size(1.dp)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.key == Key.Enter) {
                            if (inputBuffer.isNotEmpty()) {
                                viewModel.processBarcode(inputBuffer.trim())
                                inputBuffer = "" // Clear buffer
                            }
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true
            )

            Column(modifier = Modifier.fillMaxSize()) {
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

                // Render Content Based on Tab
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
}
