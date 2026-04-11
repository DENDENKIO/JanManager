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
import com.example.janmanager.util.JanValidator
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.asExecutor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import kotlin.math.max

private data class DetectedItem(val rect: android.graphics.Rect, val code: String)

@Composable
fun LiveScanScreen(
    onCodeDetected: (List<String>) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    
    // OCR & Barcode clients
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val barcodeScanner = remember { 
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                .build()
        ) 
    }
    
    // State for detected items (box + code)
    var detectedItems by remember { mutableStateOf<List<DetectedItem>>(emptyList()) }
    var previewSize by remember { mutableStateOf<Size?>(null) }
    var rotationDegrees by remember { mutableStateOf(0) }
    
    var lastCapturedCode by remember { mutableStateOf("") }
    var captureTimestamp by remember { mutableStateOf(0L) }
    
    // Define handleDetection inside the scope to access states
    fun handleDetection(items: List<DetectedItem>) {
        detectedItems = items
        if (items.isEmpty()) return
        
        val bestCode = items.first().code
        val now = System.currentTimeMillis()
        
        if (bestCode != lastCapturedCode || now - captureTimestamp > 3000) {
            lastCapturedCode = bestCode
            captureTimestamp = now
            onCodeDetected(listOf(bestCode))
        }
    }

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
                                rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                previewSize = Size(imageProxy.width, imageProxy.height)
                                
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                                    
                                    // Run both Barcode scan (fast) and OCR (fallback)
                                    // For simplicity and to avoid over-complicating results, we prioritize Barcode
                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            if (barcodes.isNotEmpty()) {
                                                val items = barcodes.mapNotNull { b ->
                                                    val rawValue = b.rawValue
                                                    val rect = b.boundingBox
                                                    if (rawValue != null && rect != null) {
                                                        DetectedItem(rect, rawValue)
                                                    } else null
                                                }
                                                handleDetection(items)
                                            } else {
                                                // Fallback to OCR if no barcode bars found
                                                recognizer.process(image)
                                                    .addOnSuccessListener { visionText ->
                                                        val items = visionText.textBlocks.flatMap { block ->
                                                            block.lines.mapNotNull { line ->
                                                                val fixed = JanValidator.tryFix(line.text)
                                                                val rect = line.boundingBox
                                                                if (fixed != null && rect != null) {
                                                                    DetectedItem(rect, fixed)
                                                                } else null
                                                            }
                                                        }
                                                        handleDetection(items)
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
            modifier = Modifier.fillMaxSize(),
            update = { /* Nothing to update */ }
        )

        // Overlay to draw detected boxes and chips
        val density = LocalDensity.current
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = constraints.maxWidth.toFloat()
            val canvasHeight = constraints.maxHeight.toFloat()
            
            val pSize = previewSize ?: return@BoxWithConstraints
            val rotation = rotationDegrees
            
            // Coordinate mapping (Logic: Rotate -> Scale -> Center-Crop)
            val isRotated = rotation == 90 || rotation == 270
            val effWidth = if (isRotated) pSize.height.toFloat() else pSize.width.toFloat()
            val effHeight = if (isRotated) pSize.width.toFloat() else pSize.height.toFloat()
            
            val scale = max(canvasWidth / effWidth, canvasHeight / effHeight)
            val dx = (canvasWidth - effWidth * scale) / 2f
            val dy = (canvasHeight - effHeight * scale) / 2f

            fun mapRect(box: android.graphics.Rect): Offset {
                // Rotated coordinates in portrait
                val xPrime = if (rotation == 90) pSize.height - box.centerY() else box.centerX()
                val yPrime = if (rotation == 90) box.centerX() else box.centerY()
                
                return Offset(
                    xPrime.toFloat() * scale + dx,
                    yPrime.toFloat() * scale + dy
                )
            }

            for (item in detectedItems) {
                val pos = mapRect(item.rect)
                
                Surface(
                    color = Color.Cyan.copy(alpha = 0.8f),
                    contentColor = Color.Black,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pos.x - 50 * density.density).toInt(),
                                (pos.y - 40 * density.density).toInt()
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

            Canvas(modifier = Modifier.fillMaxSize()) {
                detectedItems.forEach { item ->
                    val box = item.rect
                    // Draw rotated and scaled boxes
                    val x1 = (if (rotation == 90) pSize.height - box.bottom else box.left).toFloat() * scale + dx
                    val y1 = (if (rotation == 90) box.left else box.top).toFloat() * scale + dy
                    val x2 = (if (rotation == 90) pSize.height - box.top else box.right).toFloat() * scale + dx
                    val y2 = (if (rotation == 90) box.right else box.bottom).toFloat() * scale + dy

                    drawRect(
                        color = Color.Cyan,
                        topLeft = Offset(x1, y1),
                        size = ComposeSize(x2 - x1, y2 - y1),
                        alpha = 0.6f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // Capture logic moved to separate function
        LaunchedEffect(Unit) {
            // Placeholder for periodic check if needed
        }

        // Close button & Indicator
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
            text = "LIVE SCAN MODE (HYBRID)",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Cyan,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .statusBarsPadding()
        )
    }
}

