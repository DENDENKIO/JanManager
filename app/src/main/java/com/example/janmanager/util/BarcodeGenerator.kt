package com.example.janmanager.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

object BarcodeGenerator {
    fun generateEan13Bitmap(barcode: String, width: Int, height: Int): ImageBitmap? {
        if (barcode.length != 13) return null
        return try {
            val writer = MultiFormatWriter()
             // Margin is added internally by ZXing
            val bitMatrix = writer.encode(barcode, BarcodeFormat.EAN_13, width, height)
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
