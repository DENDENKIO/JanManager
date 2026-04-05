package com.example.janmanager.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.janmanager.data.local.entity.BarcodeType
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

object BarcodeGenerator {
    /**
     * JANコード/ITFコードの桁数に応じて適切なバーコード（EAN-13, EAN-8, ITF）を生成する
     */
    fun generateBarcodeBitmap(barcode: String, width: Int, height: Int): ImageBitmap? {
        val type = JanCodeUtil.detectCodeType(barcode)
        val format = when (type) {
            BarcodeType.EAN8 -> BarcodeFormat.EAN_8
            BarcodeType.EAN13 -> BarcodeFormat.EAN_13
            BarcodeType.ITF14 -> BarcodeFormat.ITF
        }

        return try {
            val writer = MultiFormatWriter()
            // マージンなどのヒント設定（ITFなどはマージンが重要）
            val bitMatrix = writer.encode(barcode, format, width, height)
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

    @Deprecated("Use generateBarcodeBitmap instead", ReplaceWith("generateBarcodeBitmap(barcode, width, height)"))
    fun generateEan13Bitmap(barcode: String, width: Int, height: Int): ImageBitmap? {
        return generateBarcodeBitmap(barcode, width, height)
    }
}
