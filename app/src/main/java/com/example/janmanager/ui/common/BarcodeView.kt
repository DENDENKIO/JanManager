package com.example.janmanager.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.janmanager.util.BarcodeImageGenerator

@Composable
fun BarcodeView(barcode: String, modifier: Modifier = Modifier) {
    val bitmap = remember(barcode) {
        BarcodeImageGenerator.generate(barcode)
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = barcode,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
