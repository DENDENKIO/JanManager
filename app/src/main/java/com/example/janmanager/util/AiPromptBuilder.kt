package com.example.janmanager.util

object AiPromptBuilder {
    fun buildPrompt(janCode: String): String {
        return """
            以下のJANコードの商品情報をJSON形式で返してください。
            各項目のルール:
            - maker_name, product_name, spec: 取得した情報そのまま使用
            - maker_name_kana, product_name_kana: 全てひらがなで表記
            - 半角スペース、全角スペースは除去
            - 該当商品が見つからない場合は {"not_found": true} のみ返却

            JANコード: $janCode

            以下のJSON形式のみで返答してください。JSON以外の文章は不要です。
            商品が見つかった場合: {"jan_code":"","maker_name":"","maker_name_kana":"","product_name":"","product_name_kana":"","spec":""}
            商品が見つからなかった場合: {"not_found": true}
            spec＝規格サイズ（容量・重量・入数。例："350ml缶","500g","1.5L","6本パック"）
        """.trimIndent()
    }
}
