package com.example.janmanager.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log

@Serializable
data class AiResponseData(
    val jan_code: String = "",
    val maker_name: String = "",
    val maker_name_kana: String = "",
    val product_name: String = "",
    val product_name_kana: String = "",
    val spec: String = "",
    val not_found: Boolean = false
)

sealed class AiParseResult {
    data class Success(val data: AiResponseData) : AiParseResult()
    data class InvalidFormat(val raw: String) : AiParseResult()
    data class JanMismatch(val expected: String, val actual: String) : AiParseResult()
    object NotFound : AiParseResult()
}

object AiResponseParser {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // not_foundを示すキーワード（大文字小文字不問）
    private val notFoundKeywords = listOf(
        "not_found",
        "not found",
        "見つかりません",
        "情報がありません",
        "商品情報なし",
        "該当なし",
        "could not find",
        "no information",
        "no product",
        "unknown"
    )

    fun parseResponse(rawContent: String, expectedJanCode: String): AiParseResult {
        Log.d("AiResponseParser", "parseResponse: raw length=${rawContent.length}")

        // Step1: 最後のJSONブロックを抽出してパースを試みる
        val jsonString = extractLastJsonBlock(rawContent)
        if (jsonString != null) {
            Log.d("AiResponseParser", "JSON block found: $jsonString")
            try {
                val data = jsonConfig.decodeFromString<AiResponseData>(jsonString)

                // JSON内に not_found:true があれば即スキップ
                if (data.not_found) {
                    Log.i("AiResponseParser", "not_found=true detected in JSON")
                    return AiParseResult.NotFound
                }

                // JANコード一致チェック
                if (data.jan_code != expectedJanCode) {
                    Log.w("AiResponseParser", "JAN Mismatch: expected=$expectedJanCode, actual=${data.jan_code}")
                    return AiParseResult.JanMismatch(expectedJanCode, data.jan_code)
                }

                // 正規化・カナ変換
                val normalizedData = data.copy(
                    maker_name        = Normalizer.normalizeText(data.maker_name),
                    product_name      = Normalizer.normalizeText(data.product_name),
                    spec              = Normalizer.normalizeText(data.spec),
                    maker_name_kana   = Normalizer.toKana(data.maker_name_kana),
                    product_name_kana = Normalizer.toKana(data.product_name_kana)
                )
                return AiParseResult.Success(normalizedData)

            } catch (e: Exception) {
                Log.w("AiResponseParser", "JSON parse error: ${e.message}")
                // JSONパース失敗→ Step2にフォールスルー
            }
        }

        // Step2: JSONパース失敗 or JSONがない場合→ キーワードでnot_foundを検出
        val lowerContent = rawContent.lowercase()
        val isNotFound = notFoundKeywords.any { keyword ->
            lowerContent.contains(keyword.lowercase())
        }
        if (isNotFound) {
            Log.i("AiResponseParser", "not_found detected by keyword in raw text")
            return AiParseResult.NotFound
        }

        Log.e("AiResponseParser", "InvalidFormat: no JSON and no not_found keyword. raw=" +
            rawContent.take(200))
        return AiParseResult.InvalidFormat(rawContent)
    }

    /**
     * テキスト中の全JSONブロック({ ... })を抽出し、最後のブロックを返す。
     * 前回の応答が混入していても、最後のブロックが今回の応答。
     */
    private fun extractLastJsonBlock(text: String): String? {
        var lastJson: String? = null
        var depth = 0
        var startIdx = -1

        for (i in text.indices) {
            when (text[i]) {
                '{' -> {
                    if (depth == 0) startIdx = i
                    depth++
                }
                '}' -> {
                    if (depth > 0) {
                        depth--
                        if (depth == 0 && startIdx >= 0) {
                            val candidate = text.substring(startIdx, i + 1).trim()
                            // not_found または jan_code フィールドを含むブロックのみ有効
                            if (candidate.contains("not_found") || candidate.contains("jan_code")) {
                                lastJson = candidate
                            }
                            startIdx = -1
                        }
                    }
                }
            }
        }
        return lastJson
    }
}
