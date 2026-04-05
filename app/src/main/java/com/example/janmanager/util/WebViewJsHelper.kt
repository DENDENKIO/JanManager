package com.example.janmanager.util

object WebViewJsHelper {

    // ---------------------------------------------------------------
    // Gemini selectors (2025+)
    // ---------------------------------------------------------------
    val GEMINI_INPUT_SELECTORS = listOf(
        // 現行 Gemini: rich-textarea 内の contenteditable
        "rich-textarea .ql-editor",
        "rich-textarea div[contenteditable='true']",
        // フォールバック
        "div.ql-editor[contenteditable='true']",
        "div[contenteditable='true'].ProseMirror",
        "div[contenteditable='true']",
        "textarea"
    )

    val GEMINI_SEND_SELECTORS = listOf(
        // 現行 Gemini 送信ボタン
        "button[aria-label='プロンプトを送信']",
        "button[aria-label='Send message']",
        "button[aria-label='Send prompt']",
        "button[data-mat-icon-name='send']",
        // jsaction 属性でマッチ
        "button[jsaction*='send']",
        "button[jsaction*='submit']",
        // フォールバック
        "button.send-button",
        "button[type='submit']"
    )

    val GEMINI_RESPONSE_SELECTORS = listOf(
        // 現行 Gemini レスポンスカスタム要素
        "model-response",
        "message-content model-response",
        // フォールバック
        ".model-response-text p",
        ".response-container",
        "div[data-message-author-role='model']",
        ".prose"
    )

    // ---------------------------------------------------------------
    // Perplexity selectors (2025+)
    // ---------------------------------------------------------------
    val PERPLEXITY_INPUT_SELECTORS = listOf(
        // 現行 Perplexity: Lexical contenteditable
        "div[contenteditable='true'][data-lexical-editor='true']",
        // textarea フォールバック
        "textarea[placeholder*='Ask']",
        "textarea[placeholder*='質問']",
        "textarea[placeholder*='Search']",
        "#ask-input",
        "div[contenteditable='true']",
        "textarea"
    )

    val PERPLEXITY_SEND_SELECTORS = listOf(
        // 現行 Perplexity 送信ボタン
        "button[aria-label='Submit']",
        "button[aria-label='送信']",
        // data-testid 系
        "button[data-testid='send-button']",
        // type=button でaria-label含む
        "button[type='button'][aria-label]",
        "button[type='submit']",
        "button.send-button"
    )

    val PERPLEXITY_RESPONSE_SELECTORS = listOf(
        // 現行 Perplexity レスポンス
        "div[data-testid='answer-content'] .prose",
        ".prose",
        "div[class*='prose']",
        // フォールバック
        ".message-content",
        "div[data-message-role='assistant']",
        "div.answer"
    )

    // ---------------------------------------------------------------
    // Prompt Injection
    // Lexical / ProseMirror / Quill / plain textarea すべてに対応
    // ---------------------------------------------------------------
    fun getInjectPromptJsWithFallback(selectors: List<String>, manualSelector: String?, prompt: String): String {
        val escapedPrompt = escapeForJs(prompt)
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val selectorArray = allSelectors.joinToString(",") { "'$it'" }

        return """
(function() {
    var selectors = [$selectorArray];
    var el = null;
    for (var i = 0; i < selectors.length; i++) {
        try {
            var found = document.querySelector(selectors[i]);
            if (found) { el = found; break; }
        } catch(e) {}
    }
    if (!el) return 'false';

    el.focus();

    var text = '$escapedPrompt';
    var isContentEditable = el.getAttribute('contenteditable') === 'true';

    if (isContentEditable) {
        // --- Lexical/ProseMirror/Quill 対応注入 ---
        // 1. 全選択して削除
        var selAll = new KeyboardEvent('keydown', { key: 'a', code: 'KeyA', keyCode: 65, ctrlKey: true, bubbles: true });
        el.dispatchEvent(selAll);

        // 2. execCommand で挿入（Chrome系では動作する）
        try {
            document.execCommand('selectAll', false, null);
            document.execCommand('delete', false, null);
        } catch(e) {}

        // 3. DataTransfer を使って paste イベントで注入（Lexical推奨）
        try {
            var dt = new DataTransfer();
            dt.setData('text/plain', text);
            var pasteEvent = new ClipboardEvent('paste', {
                bubbles: true, cancelable: true, clipboardData: dt
            });
            el.dispatchEvent(pasteEvent);
        } catch(e) {}

        // 4. paste後にinnerTextが空なら直接注入
        if (!el.innerText || el.innerText.trim() === '') {
            el.innerText = text;
        }

        // 5. フレームワーク向けイベント一式
        try {
            el.dispatchEvent(new InputEvent('beforeinput', {
                bubbles: true, cancelable: true,
                inputType: 'insertText', data: text
            }));
        } catch(e) {}
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));

    } else {
        // --- textarea / input 対応注入 ---
        var proto = el.tagName === 'TEXTAREA'
            ? window.HTMLTextAreaElement.prototype
            : window.HTMLInputElement.prototype;
        var setter = Object.getOwnPropertyDescriptor(proto, 'value');
        if (setter && setter.set) {
            setter.set.call(el, text);
        } else {
            el.value = text;
        }
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
    }

    return 'true';
})();
        """.trimIndent()
    }

    // ---------------------------------------------------------------
    // Send Button Click
    // 送信後ボタンがdisabledになるものも考慮してMouseEvent付き
    // ---------------------------------------------------------------
    fun getClickSendJsWithFallback(selectors: List<String>, manualSelector: String?): String {
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val selectorArray = allSelectors.joinToString(",") { "'$it'" }
        return """
(function() {
    var selectors = [$selectorArray];
    for (var i = 0; i < selectors.length; i++) {
        try {
            var el = document.querySelector(selectors[i]);
            if (el && !el.disabled) {
                el.focus();
                el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                el.dispatchEvent(new MouseEvent('mouseup',   { bubbles: true }));
                el.click();
                return 'true';
            }
        } catch(e) {}
    }
    // Enterキー送信フォールバック（textarea等）
    try {
        var active = document.activeElement;
        if (active) {
            active.dispatchEvent(new KeyboardEvent('keydown', {
                key: 'Enter', code: 'Enter', keyCode: 13,
                bubbles: true, cancelable: true
            }));
            return 'true';
        }
    } catch(e) {}
    return 'false';
})();
        """.trimIndent()
    }

    // ---------------------------------------------------------------
    // Response Extraction
    // 最後のメッセージブロックのテキストを取得
    // ---------------------------------------------------------------
    fun getExtractResponseJsWithFallback(selectors: List<String>, manualSelector: String?): String {
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val selectorArray = allSelectors.joinToString(",") { "'$it'" }
        return """
(function() {
    var selectors = [$selectorArray];
    for (var i = 0; i < selectors.length; i++) {
        try {
            var els = document.querySelectorAll(selectors[i]);
            if (els.length > 0) {
                var lastEl = els[els.length - 1];
                var text = lastEl.innerText || lastEl.textContent || '';
                text = text.trim();
                if (text.length > 5) return text;
            }
        } catch(e) {}
    }
    return null;
})();
        """.trimIndent()
    }

    // ---------------------------------------------------------------
    // Selector Auto-Detection (Settings画面用)
    // ---------------------------------------------------------------
    fun getAutoDetectSelectorsJs(): String {
        return """
(function() {
    var result = { input: '', send: '', response: '' };

    // Input
    var inputCandidates = [
        'div[contenteditable="true"][data-lexical-editor="true"]',
        'rich-textarea div[contenteditable="true"]',
        'div[contenteditable="true"].ql-editor',
        'div[contenteditable="true"]',
        'textarea'
    ];
    for (var s of inputCandidates) {
        var el = document.querySelector(s);
        if (el) { result.input = s; break; }
    }

    // Send
    var sendCandidates = [
        'button[aria-label]',
        'button[type="submit"]',
        'button[jsaction]'
    ];
    for (var s of sendCandidates) {
        var btns = document.querySelectorAll(s);
        for (var b of btns) {
            var label = (b.getAttribute('aria-label') || '').toLowerCase();
            if (label.includes('send') || label.includes('submit') ||
                label.includes('送信') || label.includes('プロンプト')) {
                result.send = s + '[aria-label="' + b.getAttribute('aria-label') + '"]';
                break;
            }
        }
        if (result.send) break;
    }

    // Response
    var responseCandidates = [
        'model-response', '.prose', 'div[data-message-author-role="model"]',
        'div[data-testid="answer-content"]', '.message-content'
    ];
    for (var s of responseCandidates) {
        var el = document.querySelector(s);
        if (el) { result.response = s; break; }
    }

    return JSON.stringify(result);
})();
        """.trimIndent()
    }

    // ---------------------------------------------------------------
    // DOM Existence / Content Check / Response Count
    // ---------------------------------------------------------------

    /**
     * 指定セレクタのいずれかがDOMに存在するか確認するJS。
     * 存在すれば 'ready'、なければ 'not_ready' を返す。
     */
    fun getCheckInputExistsJs(selectors: List<String>, manualSelector: String?): String {
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val checks = allSelectors.joinToString(" || ") { "document.querySelector('$it') !== null" }
        return "(function(){ return ($checks) ? 'ready' : 'not_ready'; })();"
    }

    /**
     * 入力欄にテキスト内容があるかチェックするJS。
     * 10文字以上あれば 'has_content'、なければ 'empty' を返す。
     */
    fun getCheckInputHasContentJs(selectors: List<String>, manualSelector: String?): String {
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val selectorArray = allSelectors.joinToString(",") { "'$it'" }
        return """
(function() {
    var selectors = [$selectorArray];
    for (var i = 0; i < selectors.length; i++) {
        try {
            var el = document.querySelector(selectors[i]);
            if (el) {
                var text = el.innerText || el.value || '';
                if (text.trim().length > 10) return 'has_content';
            }
        } catch(e) {}
    }
    return 'empty';
})();
        """.trimIndent()
    }

    /**
     * レスポンス要素の数をカウントするJS。数値文字列を返す。
     */
    fun getCountResponseElementsJs(selectors: List<String>, manualSelector: String?): String {
        val allSelectors = (if (manualSelector.isNullOrEmpty()) emptyList() else listOf(manualSelector)) + selectors
        val selectorArray = allSelectors.joinToString(",") { "'$it'" }
        return """
(function() {
    var selectors = [$selectorArray];
    for (var i = 0; i < selectors.length; i++) {
        try {
            var els = document.querySelectorAll(selectors[i]);
            if (els.length > 0) return '' + els.length;
        } catch(e) {}
    }
    return '0';
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
