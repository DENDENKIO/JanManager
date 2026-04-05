package com.example.janmanager.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

object AiResponseParser {
    
    private val jsonConfig = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    fun parseResponse(rawContent: String, expectedJanCode: String): AiResponseData? {
        // Regex to extract JSON block
        val jsonPattern = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL)
        val matchResult = jsonPattern.find(rawContent)
        
        val jsonString = matchResult?.value?.trim() ?: return null
        
        return try {
            val data = jsonConfig.decodeFromString<AiResponseData>(jsonString)
            
            // Validate JAN code match
            if (data.not_found) return data
            if (data.jan_code != expectedJanCode) return null
            
            // Apply normalization and hiragana validation
            data.copy(
                maker_name = Normalizer.normalizeText(data.maker_name),
                product_name = Normalizer.normalizeText(data.product_name),
                spec = Normalizer.normalizeText(data.spec),
                maker_name_kana = Normalizer.toKana(data.maker_name_kana),
                product_name_kana = Normalizer.toKana(data.product_name_kana)
            )
        } catch (e: Exception) {
            null
        }
    }
}
