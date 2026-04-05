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
        // Regex to extract JSON block, handling markdown code blocks
        // It looks for { to last }
        val jsonPattern = Regex("\\{(.*)\\}", RegexOption.DOT_MATCHES_ALL)
        val matchResult = jsonPattern.find(rawContent)
        
        val jsonString = matchResult?.value?.trim() ?: return AiParseResult.InvalidFormat(rawContent)
        
        return try {
            val data = jsonConfig.decodeFromString<AiResponseData>(jsonString)
            
            if (data.not_found) return AiParseResult.NotFound
            
            // Validate JAN code match
            if (data.jan_code != expectedJanCode) {
                Log.w("AiResponseParser", "JAN Mismatch: expected $expectedJanCode, actual ${data.jan_code}")
                return AiParseResult.JanMismatch(expectedJanCode, data.jan_code)
            }
            
            // Apply normalization and hiragana validation
            val normalizedData = data.copy(
                maker_name = Normalizer.normalizeText(data.maker_name),
                product_name = Normalizer.normalizeText(data.product_name),
                spec = Normalizer.normalizeText(data.spec),
                maker_name_kana = Normalizer.toKana(data.maker_name_kana),
                product_name_kana = Normalizer.toKana(data.product_name_kana)
            )
            AiParseResult.Success(normalizedData)
        } catch (e: Exception) {
            Log.e("AiResponseParser", "JSON parse error: ${e.message}\nRaw JSON: $jsonString")
            AiParseResult.InvalidFormat(jsonString)
        }
    }
}
