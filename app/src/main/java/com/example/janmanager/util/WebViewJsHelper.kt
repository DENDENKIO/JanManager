package com.example.janmanager.util

object WebViewJsHelper {

    /**
     * contenteditable エディタ（Quill / Lexical など）向けの汎用プロンプト注入。
     * execCommand('insertText') でペーストとして挿入することで、
     * React / Angular 管理のstateが正しく更新され、送信ボタンが有効化される。
     * Gemini（Quill Editor）・Perplexity（Lexical Editor）の両方に対応。
     */
    fun getInjectPromptJsForRichEditor(selector: String, prompt: String): String {
        val escapedPrompt = escapeForJs(prompt)
        return """
            (function() {
                var el = document.querySelector('$selector');
                if (!el) return false;
                el.focus();
                // 既存テキストを全選択して削除
                document.execCommand('selectAll', false, null);
                document.execCommand('delete', false, null);
                // テキストをペーストとして挿入（エディタのstateを更新させる）
                document.execCommand('insertText', false, '$escapedPrompt');
                return true;
            })();
        """.trimIndent()
    }

    /**
     * 通常の textarea / input 向けプロンプト注入（フォールバック用）
     */
    fun getInjectPromptJsForTextarea(selector: String, prompt: String): String {
        val escapedPrompt = escapeForJs(prompt)
        return """
            (function() {
                var el = document.querySelector('$selector');
                if (!el) return false;
                var nativeValueSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')
                    || Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                if (nativeValueSetter && nativeValueSetter.set) {
                    nativeValueSetter.set.call(el, '$escapedPrompt');
                } else {
                    el.value = '$escapedPrompt';
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
            })();
        """.trimIndent()
    }

    fun getClickSendJs(selector: String): String {
        return """
            (function() {
                var el = document.querySelector('$selector');
                if (el) {
                    el.click();
                    return true;
                }
                return false;
            })();
        """.trimIndent()
    }

    fun getExtractResponseJs(selector: String): String {
        return """
            (function() {
                var els = document.querySelectorAll('$selector');
                if (els.length > 0) {
                    var lastEl = els[els.length - 1];
                    return lastEl.innerText;
                }
                return null;
            })();
        """.trimIndent()
    }

    private fun escapeForJs(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
