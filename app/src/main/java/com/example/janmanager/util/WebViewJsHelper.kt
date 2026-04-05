package com.example.janmanager.util

object WebViewJsHelper {

    val GEMINI_INPUT_SELECTORS = listOf(
        "div.ql-editor[contenteditable='true']",
        "div[contenteditable='true'].ProseMirror",
        "rich-textarea div[contenteditable]",
        "div.input-area",
        "textarea",
        "[aria-label='プロンプトを入力してください']",
        ".ql-editor"
    )

    val GEMINI_SEND_SELECTORS = listOf(
        "button[aria-label='プロンプトを送信']",
        "button[aria-label='Send prompt']",
        "button.send-button",
        "mat-icon[role='button']", // 送信アイコン自体がボタンの場合
        "button.send"
    )

    val GEMINI_RESPONSE_SELECTORS = listOf(
        ".response-content",
        ".model-response-text",
        "div.message-content",
        "message-content",
        ".prose"
    )

    val PERPLEXITY_INPUT_SELECTORS = listOf(
        "#ask-input",
        "div[contenteditable='true'][data-lexical-editor='true']",
        "textarea[placeholder*='質問']",
        "textarea[placeholder*='Ask']",
        "div[contenteditable='true']",
        "textarea"
    )

    val PERPLEXITY_SEND_SELECTORS = listOf(
        "button[aria-label='送信']",
        "button[aria-label='Submit']",
        "button.send-button",
        "button[type='button'] svg[xlink\\:href='#pplx-icon-arrow-right']",
        "button[type='submit']",
        "svg.fa-arrow-up"
    )

    val PERPLEXITY_RESPONSE_SELECTORS = listOf(
        ".prose",
        ".message-content",
        "div.answer",
        ".selection-none"
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
                    try {
                        el = document.querySelector(s);
                        if (el) break;
                    } catch(e) {}
                }
                if (!el) return false;
                
                el.focus();
                
                // Method 1: execCommand (Standard for contenteditable)
                var success = false;
                try {
                    document.execCommand('selectAll', false, null);
                    document.execCommand('delete', false, null);
                    success = document.execCommand('insertText', false, '$escapedPrompt');
                } catch(e) {
                    success = false;
                }
                
                // Method 2: Value setter logic (Standard for textarea/input)
                if (!success || (el.innerText !== '$escapedPrompt' && el.value !== '$escapedPrompt')) {
                    if (el.getAttribute('contenteditable') === 'true' || el.tagName === 'DIV') {
                        el.innerText = '$escapedPrompt';
                    } else {
                        var nativeValueSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')
                            || Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                        if (nativeValueSetter && nativeValueSetter.set) {
                            nativeValueSetter.set.call(el, '$escapedPrompt');
                        } else {
                            el.value = '$escapedPrompt';
                        }
                    }
                }
                
                // Dispatch events to notify the site's framework (React/Vue/Lexical)
                var eventNames = ['beforeinput', 'input', 'change', 'blur', 'keyup'];
                eventNames.forEach(function(name) {
                    var event;
                    try {
                        if (name === 'beforeinput') {
                            event = new InputEvent(name, { bubbles: true, cancelable: true, inputType: 'insertText', data: '$escapedPrompt' });
                        } else {
                            event = new Event(name, { bubbles: true });
                        }
                        el.dispatchEvent(event);
                    } catch(e) {}
                });
                
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
