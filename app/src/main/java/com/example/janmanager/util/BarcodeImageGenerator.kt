package com.example.janmanager.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

object BarcodeImageGenerator {
    /**
     * Generates a barcode bitmap from a JAN code.
     * Uses EAN-13 for 13-digit numeric codes with valid checksum, 
     * EAN-8 for 8-digit numeric codes, and falls back to CODE-128 otherwise.
     */
    fun generate(janCode: String, width: Int = 1000, height: Int = 300): Bitmap? {
        if (janCode.isEmpty()) return null
        
        return try {
            val format = when {
                janCode.length == 13 && janCode.all { it.isDigit() } && isValidEan13(janCode) -> BarcodeFormat.EAN_13
                janCode.length == 8 && janCode.all { it.isDigit() } -> BarcodeFormat.EAN_8
                else -> BarcodeFormat.CODE_128
            }
            
            val hints = mapOf(
                EncodeHintType.MARGIN to 15,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            
            val bitMatrix = MultiFormatWriter().encode(janCode, format, width, height, hints)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            // Final fallback to CODE_128 if EAN format failed for unexpected reasons
            try {
                val bitMatrix = MultiFormatWriter().encode(janCode, BarcodeFormat.CODE_128, width, height, mapOf(EncodeHintType.MARGIN to 4))
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
                bmp
            } catch (e2: Exception) {
                e2.printStackTrace()
                null
            }
        }
    }

    private fun isValidEan13(code: String): Boolean {
        if (code.length != 13) return false
        return try {
            val digits = code.map { it.toString().toInt() }
            val sum = digits.take(12).mapIndexed { index, i ->
                if (index % 2 == 0) i else i * 3
            }.sum()
            val checkDigit = (10 - (sum % 10)) % 10
            checkDigit == digits[12]
        } catch (e: Exception) {
            false
        }
    }
}
