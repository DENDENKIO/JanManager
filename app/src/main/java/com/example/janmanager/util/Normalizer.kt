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
