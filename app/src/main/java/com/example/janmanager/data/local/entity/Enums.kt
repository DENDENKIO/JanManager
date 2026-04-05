package com.example.janmanager.data.local.entity

enum class ProductStatus {
    ACTIVE,
    DISCONTINUED,
    RENEWED
}

enum class PackageType(val displayName: String) {
    PIECE("単品"),
    PACK("パック"),
    CASE("ケース")
}

enum class BarcodeType {
    EAN13,
    EAN8,
    ITF14
}

enum class InfoSource {
    AI_GEMINI,
    AI_PERPLEXITY,
    MANUAL,
    NONE
}

enum class SessionStatus {
    OPEN,
    COMPLETED
}
