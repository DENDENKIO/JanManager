package com.example.janmanager.util

object WebViewJsHelper {
    
    fun getInjectPromptJs(selector: String, prompt: String): String {
        // Escaping the prompt string for JS
        val escapedPrompt = prompt.replace("\n", "\\n").replace("\"", "\\\"").replace("'", "\\'")
        return """
            (function() {
                var el = document.querySelector('$selector');
                if (el) {
                    if (el.tagName.toLowerCase() === 'textarea' || el.tagName.toLowerCase() === 'input') {
                        el.value = '$escapedPrompt';
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    } else if (el.isContentEditable) {
                        el.innerText = '$escapedPrompt';
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                    return true;
                }
                return false;
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
                    // 通常最新のレスポンスは最後尾
                    var lastEl = els[els.length - 1];
                    return lastEl.innerText;
                }
                return null;
            })();
        """.trimIndent()
    }
}
