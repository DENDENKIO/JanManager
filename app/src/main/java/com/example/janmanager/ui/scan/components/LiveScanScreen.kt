package com.example.janmanager.ui.scan.components

import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.example.janmanager.util.JanValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize

import kotlinx.coroutines.launch

private data class DetectedItem(val rect: android.graphics.Rect, val code: String)

@Composable
fun LiveScanScreen(
    onCodeDetected: (List<String>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Dispatchers.Default.asExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    
    // State for detected items (box + code)
    var detectedItems by remember { mutableStateOf<List<DetectedItem>>(emptyList()) }
    var previewSize by remember { mutableStateOf<Size?>(null) }
    
    val scope = rememberCoroutineScope()
    var lastCapturedCode by remember { mutableStateOf("") }
    var captureTimestamp by remember { mutableLongStateOf(0L) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }
                    
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(Size(1280, 720))
                        .build()
                        .apply {
                            setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                            recognizer.process(image)
                                                .addOnSuccessListener { visionText ->
                                                    val items = mutableListOf<DetectedItem>()
                                                    
                                                    previewSize = Size(imageProxy.width, imageProxy.height)

                                                    for (block in visionText.textBlocks) {
                                                        for (line in block.lines) {
                                                            val lineText = line.text
                                                            val fixed = JanValidator.tryFix(lineText)
                                                            if (fixed != null) {
                                                                line.boundingBox?.let { 
                                                                    items.add(DetectedItem(it, fixed)) 
                                                                }
                                                            }
                                                        }
                                                    }
                                                    
                                                    detectedItems = items
                                                    
                                                    // Auto-capture logic
                                                    if (items.isNotEmpty()) {
                                                        val bestCode = items.first().code
                                                        val now = System.currentTimeMillis()
                                                        if (bestCode != lastCapturedCode || now - captureTimestamp > 3000) {
                                                            lastCapturedCode = bestCode
                                                            captureTimestamp = now
                                                            onCodeDetected(listOf(bestCode))
                                                        }
                                                    }
                                                }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("LiveScan", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay to draw detected boxes and chips
        val density = LocalDensity.current
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val canvasWidthPx = constraints.maxWidth.toFloat()
            val canvasHeightPx = constraints.maxHeight.toFloat()
            
            val pSize = previewSize
            if (pSize != null) {
                for (item in detectedItems) {
                    val box = item.rect
                    
                    // Simple linear mapping for chip positioning
                    val chipX = (box.centerX().toFloat() * canvasWidthPx / pSize.width.toFloat())
                    val chipY = (box.top.toFloat() * canvasHeightPx / pSize.height.toFloat())
                    
                    Surface(
                        color = Color.Cyan.copy(alpha = 0.8f),
                        contentColor = Color.Black,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (chipX - 50 * density.density).toInt(),
                                    (chipY - 40 * density.density).toInt()
                                )
                            }
                    ) {
                        Text(
                            text = item.code,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val pSizeInner = previewSize ?: return@Canvas
                
                detectedItems.forEach { item ->
                    val box = item.rect
                    drawRect(
                        color = Color.Cyan,
                        topLeft = Offset(
                            box.left.toFloat() * size.width / pSizeInner.width.toFloat(), 
                            box.top.toFloat() * size.height / pSizeInner.height.toFloat()
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            box.width().toFloat() * size.width / pSizeInner.width.toFloat(),
                            box.height().toFloat() * size.height / pSizeInner.height.toFloat()
                        ),
                        alpha = 0.6f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        
        Text(
            text = "LIVE SCAN MODE",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Cyan,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .statusBarsPadding()
        )
    }
}
