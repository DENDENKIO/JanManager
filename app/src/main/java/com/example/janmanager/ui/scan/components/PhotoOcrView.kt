package com.example.janmanager.ui.scan.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.example.janmanager.ui.scan.DetectedText
import com.example.janmanager.util.JanValidator
import kotlin.math.roundToInt

@Composable
fun PhotoOcrView(
    imageBitmap: ImageBitmap,
    detectedTexts: List<DetectedText>,
    isLocked: Boolean,
    onTextsSelected: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    
    // ★NEW: なぞり中の指軌跡から計算した選択矩形（ジェスチャー中のみ非null）
    var dragSelectionRect by remember { mutableStateOf<Rect?>(null) }
    // ★NEW: なぞり開始点（スクリーン座標）
    var dragStartPos by remember { mutableStateOf<Offset?>(null) }

    val density = LocalDensity.current.density
    
    // Set of indices of currently selected (highlighted) text blocks
    val selectedIndices = remember { mutableStateListOf<Int>() }
    
    // Identify blocks that look like JAN codes (for auto-highlight)
    val janCandidateIndices = remember(detectedTexts) {
        detectedTexts.indices.filter { index ->
            JanValidator.tryFix(detectedTexts[index].text) != null
        }
    }
    
    // Pulse animation for candidates
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    // We need to know the drawing bounds to map coordinates
    val imageDrawParams = remember(imageBitmap, containerSize) {
        if (containerSize == Size.Zero) return@remember null
        
        val bw = imageBitmap.width.toFloat()
        val bh = imageBitmap.height.toFloat()
        val containerW = containerSize.width
        val containerH = containerSize.height
        
        val imageAspectRatio = bw / bh
        val containerAspectRatio = containerW / containerH
        
        val drawW: Float
        val drawH: Float
        if (imageAspectRatio > containerAspectRatio) {
            drawW = containerW
            drawH = containerW / imageAspectRatio
        } else {
            drawH = containerH
            drawW = containerH * imageAspectRatio
        }
        
        val left = (containerW - drawW) / 2f
        val top = (containerH - drawH) / 2f
        
        ImageDrawParams(bw, bh, drawW, drawH, left, top)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it.toSize() }
            .pointerInput(isLocked, detectedTexts, imageDrawParams) {
                if (isLocked) return@pointerInput
                
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var isTransforming = false
                    var lastTouchPos: Offset? = null
                    val startTime = System.currentTimeMillis()
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }
                        
                        if (pointers.isEmpty()) {
                            // On finger up, if it was a quick tap, try 1-tap selection
                            val duration = System.currentTimeMillis() - startTime
                            if (!isTransforming && duration < 200 && lastTouchPos != null) {
                                val params = imageDrawParams
                                if (params != null) {
                                    detectedTexts.forEachIndexed { index, detected ->
                                        val screenRect = mapBitmapRectToScreen(detected.boundingBox, params, scale, offset)
                                        // 1-tap selection: inflate 15dp for easier tap
                                        if (screenRect.inflate(15f * density).contains(lastTouchPos!!)) {
                                            // Smart selection: if it's a candidate, use the fixed version
                                            val fixed = JanValidator.tryFix(detected.text)
                                            if (fixed != null) {
                                                onTextsSelected(listOf(fixed))
                                            } else {
                                                onTextsSelected(listOf(detected.text))
                                            }
                                        }
                                    }
                                }
                            }
                            break
                        }
                        
                        // Transform mode (Multi-finger)
                        if (!isLocked && pointers.size >= 2) {
                            isTransforming = true
                            selectedIndices.clear()
                            lastTouchPos = null
                            
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            
                            val oldScale = scale
                            scale = (scale * zoom).coerceIn(1f, 10f)
                            
                            // Adjust offset to zoom around the centroid
                            offset = centroid - (centroid - offset) * (scale / oldScale) + pan
                            
                            event.changes.forEach { it.consume() }
                        } else if (pointers.size == 1 && (isLocked || !isTransforming)) {
                            // Selection mode (Single finger)
                            val pointer = pointers[0]
                            val touchPos = pointer.position
                            val params = imageDrawParams

                            // ★NEW: ドラッグ開始点を記録し、選択矩形をリアルタイム更新
                            if (dragStartPos == null) {
                                dragStartPos = touchPos
                            }
                            dragStartPos?.let { start ->
                                dragSelectionRect = Rect(
                                    left   = minOf(start.x, touchPos.x),
                                    top    = minOf(start.y, touchPos.y),
                                    right  = maxOf(start.x, touchPos.x),
                                    bottom = maxOf(start.y, touchPos.y)
                                )
                            }

                            if (params != null) {
                                // Check if this touch segment intersects any detected text block
                                detectedTexts.forEachIndexed { index, detected ->
                                    if (!selectedIndices.contains(index)) {
                                        val screenRect = mapBitmapRectToScreen(
                                            detected.boundingBox, params, scale, offset
                                        )
                                        // ★REDUCED: inflate を 30f → 8f に縮小（隣接ブロックの誤爆防止）
                                        val inflatedRect = screenRect.inflate(8f * density)
                                        
                                        val isHit = if (lastTouchPos == null) {
                                            inflatedRect.contains(touchPos)
                                        } else {
                                            // Robust Path Sampling: Check 8 points along the segment to ensure no skips
                                            var hit = false
                                            for (i in 0..8) {
                                                val t = i.toFloat() / 8f
                                                val sample = lastTouchPos!! + (touchPos - lastTouchPos!!) * t
                                                if (inflatedRect.contains(sample)) {
                                                    hit = true
                                                    break
                                                }
                                            }
                                            hit
                                        }
                                        
                                        if (isHit) {
                                            selectedIndices.add(index)
                                        }
                                    }
                                }
                            }
                            lastTouchPos = touchPos
                            pointer.consume()
                        }
                    }
                    
                    // On gesture end (finger up)
                    if (selectedIndices.isNotEmpty()) {
                        // Sort selected blocks by position (Line-by-line, then Left-to-Right)
                        val sortedTexts = selectedIndices
                            .map { detectedTexts[it] }
                            .sortedWith(compareBy({ it.boundingBox.top / 20 }, { it.boundingBox.left }))
                            .map { it.text }
                        onTextsSelected(sortedTexts)
                    }
                    selectedIndices.clear()

                    // ★NEW: ドラッグ選択矩形をリセット
                    dragSelectionRect = null
                    dragStartPos = null
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    transformOrigin = TransformOrigin(0f, 0f)
                )
        ) {
            val params = imageDrawParams ?: return@Canvas
            
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(params.left.roundToInt(), params.top.roundToInt()),
                dstSize = IntSize(params.drawW.roundToInt(), params.drawH.roundToInt())
            )

            // Subtle indicators for all detected text while not dragging? 
            // Better to draw highlights in a separate canvas to not be affected by graphicsLayer scaling 
            // IF we want them to stay crisp, but drawing inside here is easier for alignment.
        }

    }

    // Highlights overlay (Affected by zoom/pan)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
                transformOrigin = TransformOrigin(0f, 0f)
            )
    ) {
        val params = imageDrawParams ?: return@Canvas
        
        detectedTexts.forEachIndexed { index, detected ->
            val rect = mapBitmapRectToDrawArea(detected.boundingBox, params)
            
            val isSelected = selectedIndices.contains(index)
            val isCandidate = janCandidateIndices.contains(index)
            
            if (isCandidate && !isSelected) {
                // Draw pulse effect for JAN candidates
                val inflatedSize = rect.size * pulseScale
                val diffW = inflatedSize.width - rect.width
                val diffH = inflatedSize.height - rect.height
                
                drawRect(
                    color = Color.Cyan.copy(alpha = pulseAlpha * 0.3f),
                    topLeft = Offset(rect.left - diffW / 2, rect.top - diffH / 2),
                    size = inflatedSize
                )
                
                drawRect(
                    color = Color.Cyan.copy(alpha = pulseAlpha),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 2.dp.toPx())
                )
            } else {
                val color = if (isSelected) Color.Cyan.copy(alpha = 0.5f) else Color.Yellow.copy(alpha = 0.2f)
                drawRect(
                    color = color,
                    topLeft = rect.topLeft,
                    size = rect.size
                )
            }
        }

        // ★NEW: なぞり中の選択矩形（短形）をリアルタイム描画
        // scaleとoffsetを考慮した座標に変換して表示
        dragSelectionRect?.let { selRect ->
            // このCanvasはgraphicsLayerでscale/offset適用済みのため、
            // スクリーン座標をgraphicsLayer逆変換して描画座標に変換する
            val drawLeft   = (selRect.left   - offset.x) / scale
            val drawTop    = (selRect.top    - offset.y) / scale
            val drawRight  = (selRect.right  - offset.x) / scale
            val drawBottom = (selRect.bottom - offset.y) / scale

            // 選択範囲の塗りつぶし（半透明の青）
            drawRect(
                color = Color(0x334FC3F7),
                topLeft = Offset(drawLeft, drawTop),
                size = Size(drawRight - drawLeft, drawBottom - drawTop)
            )
            // 選択範囲のボーダー（白の点線風の細い枠）
            drawRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(drawLeft, drawTop),
                size = Size(drawRight - drawLeft, drawBottom - drawTop),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(8f, 4f), 0f
                    )
                )
            )
        }
    }

    // Anchored labels overlay (Floating Text)
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val params = imageDrawParams
        if (params != null) {
            detectedTexts.forEach { detected ->
                if (detected.anchoredProductName != null) {
                    val screenRect = mapBitmapRectToScreen(detected.boundingBox, params, scale, offset)
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        contentColor = Color.Cyan,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .offset { 
                                IntOffset(
                                    (screenRect.left).toInt(),
                                    (screenRect.top - 20 * density).toInt()
                                )
                            }
                    ) {
                        Text(
                            text = detected.anchoredProductName,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private data class ImageDrawParams(
    val bw: Float, val bh: Float,
    val drawW: Float, val drawH: Float,
    val left: Float, val top: Float
)

private fun mapBitmapRectToDrawArea(bitmapRect: android.graphics.Rect, params: ImageDrawParams): Rect {
    val left = params.left + (bitmapRect.left * params.drawW / params.bw)
    val top = params.top + (bitmapRect.top * params.drawH / params.bh)
    val right = params.left + (bitmapRect.right * params.drawW / params.bw)
    val bottom = params.top + (bitmapRect.bottom * params.drawH / params.bh)
    return Rect(left, top, right, bottom)
}

private fun mapBitmapRectToScreen(
    bitmapRect: android.graphics.Rect,
    params: ImageDrawParams,
    scale: Float,
    offset: Offset
): Rect {
    val drawRect = mapBitmapRectToDrawArea(bitmapRect, params)
    return Rect(
        drawRect.left * scale + offset.x,
        drawRect.top * scale + offset.y,
        drawRect.right * scale + offset.x,
        drawRect.bottom * scale + offset.y
    )
}
