package com.example.janmanager.ui.scan.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.janmanager.data.local.entity.OcrScanHistory
import com.example.janmanager.ui.scan.ScanViewModel
import com.example.janmanager.ui.common.BarcodeView
import com.example.janmanager.util.BarcodeImageGenerator
import com.example.janmanager.ui.scan.components.PhotoOcrView
import com.example.janmanager.ui.scan.components.LiveScanScreen
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContinuousMode(viewModel: ScanViewModel) {
    val context = LocalContext.current
    val ocrScanHistory by viewModel.ocrScanHistory.collectAsState()
    val detectedTextBlocks by viewModel.detectedTextBlocks.collectAsState()
    val isOcrProcessing by viewModel.isOcrProcessing.collectAsState()
    val isLiveMode by viewModel.isLiveMode.collectAsState()
    val photoUri by viewModel.photoUri.collectAsState()
    val photoBitmap by viewModel.photoBitmap.collectAsState()
    val isTransformLocked by viewModel.isTransformLocked.collectAsState()
    
    // Load bitmap when URI changes
    LaunchedEffect(photoUri) {
        photoUri?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    // 1. Decode bounds to get original size
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { 
                        BitmapFactory.decodeStream(it, null, options) 
                    }
                    
                    // 2. Calculate inSampleSize (Max resolution 2048px to prevent OOM)
                    options.inSampleSize = calculateInSampleSize(options, 2048, 2048)
                    options.inJustDecodeBounds = false
                    
                    // 3. Decode actual bitmap
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { 
                        BitmapFactory.decodeStream(it, null, options) 
                    }
                    viewModel.setPhotoBitmap(bitmap)
                } catch (e: Exception) {
                    // Log error properly in a real app
                }
            }
        }
    }
    val ocrError by viewModel.ocrError.collectAsState()

    val scaffoldState = rememberBottomSheetScaffoldState()
    
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadPhoto(uri)
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // We should use a capture strategy here, but for now we rely on the pick/live actions.
            // (If we had a direct camera capture utility, we'd use it here)
        }
    }

    var deletingItem by remember { mutableStateOf<OcrScanHistory?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 120.dp,
        sheetShape = MaterialTheme.shapes.large,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "スキャン履歴",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (ocrScanHistory.isNotEmpty()) {
                        TextButton(
                            onClick = { showDeleteAllConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("すべて削除")
                        }
                    }
                }
                
                HorizontalDivider()

                if (ocrScanHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("履歴がありません", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(ocrScanHistory, key = { it.id }) { history ->
                            HistoryItem(
                                history = history,
                                onTap = { viewModel.showBarcodeFromHistory(history) },
                                onLongPress = { deletingItem = history },
                                onDelete = { viewModel.deleteOcrHistory(history.id) }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            if (isLiveMode) {
                LiveScanScreen(
                    onCodeDetected = { viewModel.processLiveFrame(it) },
                    onClose = { viewModel.toggleLiveMode() }
                )
            } else {
                if (photoUri == null) {
                    // Empty State
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("JANコードをスキャンしましょう", color = Color.LightGray)
                        Spacer(Modifier.height(32.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { viewModel.toggleLiveMode() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Camera, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("ライブ")
                            }
                            Button(
                                onClick = { pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("ギャラリー")
                            }
                        }
                    }
                } else {
                    // Photo Mode
                    photoBitmap?.let { bitmap ->
                        PhotoOcrView(
                            imageBitmap = bitmap.asImageBitmap(),
                            detectedTexts = detectedTextBlocks,
                            isLocked = isTransformLocked,
                            onTextsSelected = { texts ->
                                viewModel.processSelectedTexts(texts)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    if (isOcrProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(48.dp),
                            color = Color.Cyan
                        )
                    }
                }
            }

            // Floating Controls
            if (!isLiveMode) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .statusBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Live Mode Toggle
                    FloatingActionButton(
                        onClick = { viewModel.toggleLiveMode() },
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.Cyan,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Camera, contentDescription = "Live MODE")
                    }

                    if (photoUri != null) {
                        // Lock/Unlock Toggle
                        FloatingActionButton(
                            onClick = { viewModel.toggleTransformLock() },
                            containerColor = (if (isTransformLocked) Color.Cyan else Color.Black).copy(alpha = 0.6f),
                            contentColor = if (isTransformLocked) Color.Black else Color.Cyan,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(if (isTransformLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null)
                        }

                        // Reset Image
                        FloatingActionButton(
                            onClick = { viewModel.resetTransform() },
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    }
                    
                    // Gallery Picker
                    FloatingActionButton(
                        onClick = { pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                    }
                }
            }

            // Error Message
            AnimatedVisibility(
                visible = ocrError,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "読み取りに失敗しました。もう一度なぞってください。",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Dialogs
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("すべて削除") },
            text = { Text("履歴をすべて削除してもよろしいですか？") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllOcrHistory(); showDeleteAllConfirm = false }) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
fun HistoryItem(
    history: OcrScanHistory,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(history.janCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (history.productName.isNotEmpty()) {
                    Text(history.productName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            
            // Large barcode preview for easy scanning
            Box(
                modifier = Modifier
                    .size(120.dp, 40.dp)
                    .background(Color.White, shape = MaterialTheme.shapes.extraSmall)
                    .padding(2.dp)
            ) {
                BarcodeView(barcode = history.janCode)
            }
            
            Spacer(Modifier.width(16.dp))
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.LightGray)
            }
        }
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
