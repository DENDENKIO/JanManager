package com.example.janmanager.util

import com.example.janmanager.data.local.entity.BarcodeType

object JanCodeUtil {
    fun detectCodeType(barcode: String): BarcodeType {
        return when (barcode.length) {
            13 -> BarcodeType.EAN13
            8 -> BarcodeType.EAN8
            14 -> BarcodeType.ITF14
            else -> BarcodeType.EAN13 // Fallback
        }
    }

    fun extractMakerPrefix(barcode: String): String {
        if (barcode.length != 8 && barcode.length != 13) return ""
        // 通常は7桁か9桁。ここでは先頭7桁をデフォルトプレフィックスとする
        return if (barcode.length >= 7) barcode.substring(0, 7) else ""
    }

    fun itfToJan(itf: String): String {
        if (itf.length != 14) return itf
        // ITF14の先頭1桁（パッケージインジケータ）を除去し、残り12桁のチェックデジットを再計算
        val jan12 = itf.substring(1, 13)
        return jan12 + calculateCheckDigit(jan12)
    }

    fun calculateCheckDigit(codeWithoutCd: String): String {
        if (codeWithoutCd.length != 12 && codeWithoutCd.length != 7) return "0"
        var evenSum = 0
        var oddSum = 0
        val is13 = codeWithoutCd.length == 12

        // EAN13: odd pos (1-indexed) from right is mult by 3, but working left to right:
        // C1 C2 C3 C4 C5 C6 C7 C8 C9 C10 C11 C12
        // C12 (even idx 11) is x3.
        for (i in codeWithoutCd.indices) {
            val digit = codeWithoutCd[i] - '0'
            if (is13) {
                if (i % 2 != 0) evenSum += digit else oddSum += digit
            } else {
                if (i % 2 == 0) evenSum += digit else oddSum += digit
            }
        }
        val total = oddSum + (evenSum * 3)
        val remainder = total % 10
        val cd = if (remainder == 0) 0 else 10 - remainder
        return cd.toString()
    }
}
