package com.example.janmanager.util

object WebViewJsHelper {

    val GEMINI_INPUT_SELECTORS = listOf(
        "div.ql-editor[contenteditable='true']",
        "div[contenteditable='true'].ProseMirror",
        "rich-textarea div[contenteditable]",
        "div.input-area",
        "textarea"
    )

    val GEMINI_SEND_SELECTORS = listOf(
        "button[aria-label='プロンプトを送信']",
        "button[aria-label='Send prompt']",
        "button.send-button",
        "button.send"
    )

    val GEMINI_RESPONSE_SELECTORS = listOf(
        ".response-content",
        ".model-response-text",
        "div.message-content",
        ".prose"
    )

    val PERPLEXITY_INPUT_SELECTORS = listOf(
        "#ask-input",
        "textarea[placeholder*='質問']",
        "textarea[placeholder*='Ask']",
        "div[contenteditable='true']"
    )

    val PERPLEXITY_SEND_SELECTORS = listOf(
        "button[aria-label='送信']",
        "button[aria-label='Submit']",
        "button.send-button",
        "button[type='submit']"
    )

    val PERPLEXITY_RESPONSE_SELECTORS = listOf(
        ".prose",
        ".message-content",
        "div.answer"
    )

    fun getInjectPromptJsWithFallback(selectors: List<String>, manualSelector: String?, prompt: String): String {
        val escapedPrompt = escapeForJs(prompt)
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val selectorArray = allSelectors.joinToString(",") { "'$it'" }
        
        return """
            (function() {
                var selectors = [$selectorArray];
                var el = null;
                for (var s of selectors) {
                    el = document.querySelector(s);
                    if (el) break;
                }
                if (!el) return false;
                
                el.focus();
                // Check if it's a rich editor or textarea
                if (el.getAttribute('contenteditable') === 'true' || el.tagName === 'DIV') {
                    document.execCommand('selectAll', false, null);
                    document.execCommand('delete', false, null);
                    document.execCommand('insertText', false, '$escapedPrompt');
                } else {
                    var nativeValueSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')
                        || Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                    if (nativeValueSetter && nativeValueSetter.set) {
                        nativeValueSetter.set.call(el, '$escapedPrompt');
                    } else {
                        el.value = '$escapedPrompt';
                    }
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                return true;
            })();
        """.trimIndent()
    }

    fun getClickSendJsWithFallback(selectors: List<String>, manualSelector: String?): String {
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val selectorArray = allSelectors.joinToString(",") { "'$it'" }
        return """
            (function() {
                var selectors = [$selectorArray];
                for (var s of selectors) {
                    var el = document.querySelector(s);
                    if (el) {
                        el.click();
                        return true;
                    }
                }
                return false;
            })();
        """.trimIndent()
    }

    fun getExtractResponseJsWithFallback(selectors: List<String>, manualSelector: String?): String {
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val selectorArray = allSelectors.joinToString(",") { "'$it'" }
        return """
            (function() {
                var selectors = [$selectorArray];
                for (var s of selectors) {
                    var els = document.querySelectorAll(s);
                    if (els.length > 0) {
                        var lastEl = els[els.length - 1];
                        return lastEl.innerText;
                    }
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
