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

    fun parseResponse(rawContent: String, expectedJanCode: String): AiParseResult {
        // 複数JSONブロックが含まれている場合（前回応答の混入）に備え、
        // 全ての { ... } ブロックを抽出して最後のものを使用する
        val jsonString = extractLastJsonBlock(rawContent)
            ?: return AiParseResult.InvalidFormat(rawContent)

        return try {
            val data = jsonConfig.decodeFromString<AiResponseData>(jsonString)

            if (data.not_found) return AiParseResult.NotFound

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
            AiParseResult.Success(normalizedData)
        } catch (e: Exception) {
            Log.e("AiResponseParser", "JSON parse error: ${e.message}\nRaw JSON: $jsonString")
            AiParseResult.InvalidFormat(jsonString)
        }
    }

    /**
     * テキスト中の全JSONブロック({ ... })を抽出し、最後のブロックを返す。
     * 前回の応答が混入していても、最後のブロックが今回の応答であるため正しくパースできる。
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
                            // jan_code フィールドを含むブロックのみ有効とする
                            if (candidate.contains("jan_code")) {
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
