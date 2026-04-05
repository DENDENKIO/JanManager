package com.example.janmanager.data.local.entity

enum class ProductStatus {
    ACTIVE,
    DISCONTINUED,
    RENEWED
}

enum class PackageType {
    PIECE,
    PACK,
    CASE
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
