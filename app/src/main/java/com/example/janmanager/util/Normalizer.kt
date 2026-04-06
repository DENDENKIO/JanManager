package com.example.janmanager.util

object Normalizer {
    /**
     * 前後空白除去、連続スペース除去を適用する
     */
    fun normalizeText(text: String?): String {
        if (text == null) return ""
        return text.trim().replace(Regex("\\s+"), " ")
    }
    
    
    /**
     * 全角の数字やハイフン等を半角に変換する
     */
    fun toHalfWidth(text: String?): String {
        if (text == null) return ""
        val sb = StringBuilder()
        for (c in text) {
            when {
                // 全角数字 (０-９) -> 半角数字 (0-9)
                c in '\uFF10'..'\uFF19' -> sb.append(c - 0xFEE0)
                // 全角アルファベット大文字 (Ａ-Ｚ) -> 半角アルファベット大文字 (A-Z)
                c in '\uFF21'..'\uFF3A' -> sb.append(c - 0xFEE0)
                // 全角アルファベット小文字 (ａ-ｚ) -> 半角アルファベット小文字 (a-z)
                c in '\uFF41'..'\uFF5A' -> sb.append(c - 0xFEE0)
                // 全角スペース -> 半角スペース
                c == '\u3000' -> sb.append(' ')
                // その他はそのまま（必要に応じて追加）
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * 文字列をひらがなのみにする（簡易実装: カタカナをひらがなに変換し、それ以外を除去などの処理）
     */
    fun toKana(text: String?): String {
        if (text == null) return ""
        val normalized = normalizeText(text)
        val sb = java.lang.StringBuilder()
        for (c in normalized) {
            if (c in '\u30A1'..'\u30F6') {
                // カタカナをひらがなに変換
                sb.append(c - 0x60)
            } else if (c in '\u3041'..'\u3096') {
                // ひらがなはそのまま
                sb.append(c)
            }
            // 仕様により「ひらがなのみ」とするが、長音記号などは適宜残すか削除か。ここでは長音残す
            else if (c == 'ー') {
                sb.append(c)
            }
        }
        return sb.toString()
    }
}
