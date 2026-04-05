package com.example.janmanager.util

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class AiWebViewInteractor {

    var webView: WebView? = null
        set(value) {
            field = value
            if (value != null) registerBridge(value)
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var inputSelectors: List<String> = WebViewJsHelper.GEMINI_INPUT_SELECTORS
    private var sendSelectors: List<String> = WebViewJsHelper.GEMINI_SEND_SELECTORS
    private var responseSelectors: List<String> = WebViewJsHelper.GEMINI_RESPONSE_SELECTORS
    private var manualInputSelector: String? = null
    private var manualSendButtonSelector: String? = null
    private var manualResponseSelector: String? = null

    private var pendingResultCallback: ((String?) -> Unit)? = null

    fun configure(
        inputSel: List<String>,
        sendSel: List<String>,
        responseSel: List<String>,
        manualInput: String?,
        manualSend: String?,
        manualResponse: String?
    ) {
        inputSelectors = inputSel
        sendSelectors = sendSel
        responseSelectors = responseSel
        manualInputSelector = manualInput
        manualSendButtonSelector = manualSend
        manualResponseSelector = manualResponse
    }

    // ---------------------------------------------------------------
    // Bridge逋ｻ骭ｲ・・I繧ｹ繝ｬ繝・ラ菫晁ｨｼ・・    // ---------------------------------------------------------------
    private fun registerBridge(wv: WebView) {
        val action = Runnable {
            try {
                wv.removeJavascriptInterface("JanBridge")
            } catch (_: Exception) {}
            wv.addJavascriptInterface(BridgeInterface(), "JanBridge")
            Log.d(TAG, "JanBridge registered")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    inner class BridgeInterface {
        @JavascriptInterface
        fun onResult(text: String?) {
            Log.d(TAG, "onResult called, length=${text?.length ?: 0}")
            mainHandler.post {
                pendingResultCallback?.invoke(text)
                pendingResultCallback = null
            }
        }

        @JavascriptInterface
        fun onError(message: String?) {
            Log.w(TAG, "onError called: $message")
            mainHandler.post {
                pendingResultCallback?.invoke(null)
                pendingResultCallback = null
            }
        }
    }

    data class FetchResult(
        val success: Boolean,
        val data: AiResponseData? = null,
        val errorMessage: String? = null
    )

    // ---------------------------------------------------------------
    // 繝｡繧､繝ｳ繝輔Ο繝ｼ
    // ---------------------------------------------------------------
    suspend fun executeFullFlow(
        janCode: String,
        targetBaseUrl: String,
        onStatus: (String) -> Unit
    ): FetchResult {
        val wv = webView
        if (wv == null) {
            Log.e(TAG, "webView is null")
            return FetchResult(false, errorMessage = "WebView縺悟・譛溷喧縺輔ｌ縺ｦ縺・∪縺帙ｓ")
        }

        onStatus("$janCode 螳溯｡御ｸｭ...")

        val prompt = AiPromptBuilder.buildPrompt(janCode)
        Log.d(TAG, "Prompt generated for $janCode (${prompt.length} chars)")

        val promptB64 = Base64.encodeToString(
            prompt.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        val allInput = buildSelectorList(inputSelectors, manualInputSelector)
        val allSend = buildSelectorList(sendSelectors, manualSendButtonSelector)
        val allResponse = buildSelectorList(responseSelectors, manualResponseSelector)

        val inputArr = allInput.joinToString(",") { "'${escapeSelectorForJs(it)}'" }
        val sendArr = allSend.joinToString(",") { "'${escapeSelectorForJs(it)}'" }
        val responseArr = allResponse.joinToString(",") { "'${escapeSelectorForJs(it)}'" }

        val js = buildAllInOneJs(promptB64, inputArr, sendArr, responseArr)
        Log.d(TAG, "JS script length: ${js.length}")

        val rawResponse: String? = try {
            withTimeout(90_000L) {
                suspendCancellableCoroutine { continuation ->
                    pendingResultCallback = { result ->
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                    mainHandler.post {
                        Log.d(TAG, "Executing JS on WebView...")
                        wv.evaluateJavascript(js, null)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout waiting for JS callback")
            pendingResultCallback = null
            return FetchResult(false, errorMessage = "繧ｿ繧､繝繧｢繧ｦ繝茨ｼ・0遘抵ｼ・)
        }

        if (rawResponse.isNullOrBlank()) {
            return FetchResult(false, errorMessage = "AI縺九ｉ縺ｮ繝ｬ繧ｹ繝昴Φ繧ｹ縺檎ｩｺ縺ｧ縺励◆")
        }

        onStatus("$janCode 繝代・繧ｹ荳ｭ...")
        Log.d(TAG, "Raw response length: ${rawResponse.length}")

        return when (val parseResult = AiResponseParser.parseResponse(rawResponse, janCode)) {
            is AiParseResult.Success -> FetchResult(true, data = parseResult.data)
            is AiParseResult.NotFound -> FetchResult(true, data = AiResponseData(jan_code = janCode, not_found = true))
            is AiParseResult.JanMismatch -> FetchResult(false, errorMessage = "JAN荳堺ｸ閾ｴ: expected=$janCode, actual=${parseResult.actual}")
            is AiParseResult.InvalidFormat -> FetchResult(false, errorMessage = "繝ｬ繧ｹ繝昴Φ繧ｹ縺ｮ蠖｢蠑上′荳肴ｭ｣縺ｧ縺・)
        }
    }

    // ---------------------------------------------------------------
    // 謇句虚蜿悶ｊ霎ｼ縺ｿ
    // ---------------------------------------------------------------
    suspend fun extractCurrentResponse(janCode: String): AiParseResult? {
        val wv = webView ?: return null
        val allResponse = buildSelectorList(responseSelectors, manualResponseSelector)
        val responseArr = allResponse.joinToString(",") { "'${escapeSelectorForJs(it)}'" }

        val extractJs = """
(function() {
    var selectors = [$responseArr];
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

        val rawResponse = evaluateJsSync(extractJs)
        val cleaned = normalizeJsResult(rawResponse)
        if (cleaned.isNullOrBlank()) return null
        return AiResponseParser.parseResponse(cleaned, janCode)
    }

    // ---------------------------------------------------------------
    // 邨ｱ蜷・S・・ikobunAI譁ｹ蠑上ｒ螳悟・遘ｻ讀・+ 繝・ヰ繝・げ繝ｭ繧ｰ莉倥″・・    // ---------------------------------------------------------------
    private fun buildAllInOneJs(
        promptB64: String,
        inputSelArr: String,
        sendSelArr: String,
        responseSelArr: String
    ): String {
        return """
(function() {
    console.log('[JanManager] === AI Auto-fetch JS execution started ===');
    
    try {
        /* --- Base64 decode --- */
        var b64 = '$promptB64';
        var prompt;
        try {
            var bytes = Uint8Array.from(atob(b64), function(c){ return c.charCodeAt(0); });
            prompt = new TextDecoder('utf-8').decode(bytes);
        } catch(e) {
            console.error('[JanManager] Base64 decode failed:', e);
            if (typeof JanBridge !== 'undefined') JanBridge.onError('Base64繝・さ繝ｼ繝牙､ｱ謨・ ' + e.message);
            return;
        }
        console.log('[JanManager] Prompt decoded, length=' + prompt.length);

        var inputSelectors = [$inputSelArr];
        var sendSelectors = [$sendSelArr];
        var responseSelectors = [$responseSelArr];

        /* ==== Phase 1: 蜈･蜉帶ｬ・ｒ謗｢縺・(譛螟ｧ30遘・ ==== */
        var findTries = 0;
        var findMax = 100;

        function findInput() {
            /* 縺ｾ縺・id="ask-input" 繧堤峩謗･隧ｦ縺・(Perplexity) */
            var byId = document.getElementById('ask-input');
            if (byId) {
                console.log('[JanManager] Found input by #ask-input');
                return byId;
            }
            /* 繧ｻ繝ｬ繧ｯ繧ｿ繝ｪ繧ｹ繝医°繧画爾縺・*/
            for (var i = 0; i < inputSelectors.length; i++) {
                try {
                    var el = document.querySelector(inputSelectors[i]);
                    if (el) {
                        console.log('[JanManager] Found input by selector: ' + inputSelectors[i]);
                        return el;
                    }
                } catch(e) {}
            }
            return null;
        }

        function waitForInput() {
            var el = findInput();
            if (el) {
                console.log('[JanManager] Input element found, proceeding to inject');
                setTimeout(function(){ doInject(el); }, 500);
                return;
            }
            findTries++;
            if (findTries >= findMax) {
                console.error('[JanManager] Input not found after ' + findMax + ' tries');
                if (typeof JanBridge !== 'undefined') JanBridge.onError('蜈･蜉帶ｬ・′隕九▽縺九ｊ縺ｾ縺帙ｓ・・0遘偵ち繧､繝繧｢繧ｦ繝茨ｼ・);
                return;
            }
            if (findTries % 10 === 0) {
                console.log('[JanManager] Waiting for input... try ' + findTries + '/' + findMax);
            }
            setTimeout(waitForInput, 300);
        }

        /* ==== Phase 2: 繝励Ο繝ｳ繝励ヨ豕ｨ蜈･ ==== */
        function doInject(el) {
            console.log('[JanManager] doInject: tag=' + el.tagName + ', contentEditable=' + el.contentEditable + ', id=' + el.id);

            /* 繝輔か繝ｼ繧ｫ繧ｹ繧堤｢ｺ螳溘↓蠖薙※繧・*/
            el.focus();
            el.click();

            var isContentEditable = (el.contentEditable === 'true' || el.contentEditable === 'inherit'
                || el.getAttribute('contenteditable') === 'true');

            if (isContentEditable) {
                console.log('[JanManager] ContentEditable mode');

                /* 繧ｫ繝ｼ繧ｽ繝ｫ繧剃ｸｭ縺ｮ譛蛻昴・蟄占ｦ∫ｴ縺ｫ險ｭ鄂ｮ */
                var child = el.querySelector('p') || el.querySelector('span') || el.firstChild;
                if (child) {
                    try {
                        var range = document.createRange();
                        range.selectNodeContents(child);
                        range.collapse(false);
                        var sel = window.getSelection();
                        sel.removeAllRanges();
                        sel.addRange(range);
                    } catch(e) {
                        console.warn('[JanManager] Range setup failed:', e);
                    }
                }

                /* execCommand譁ｹ蠑・(GikobunAI螳溯ｨｼ貂医∩) */
                document.execCommand('selectAll', false, null);
                document.execCommand('delete', false, null);
                var insertOk = document.execCommand('insertText', false, prompt);
                console.log('[JanManager] execCommand insertText result=' + insertOk);

                /* insertText螟ｱ謨玲凾縺ｮ繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ */
                if (!insertOk || (el.innerText || '').trim().length < 10) {
                    console.log('[JanManager] insertText failed, trying innerText fallback');
                    el.innerText = prompt;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
            } else {
                console.log('[JanManager] Textarea/Input mode');
                /* textarea / input */
                var nativeSetter = Object.getOwnPropertyDescriptor(
                    el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value'
                );
                if (nativeSetter && nativeSetter.set) {
                    nativeSetter.set.call(el, prompt);
                } else {
                    el.value = prompt;
                }
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
            }

            /* 豕ｨ蜈･遒ｺ隱・*/
            setTimeout(function() {
                var content = el.innerText || el.value || '';
                console.log('[JanManager] After inject, content length=' + content.trim().length);
                if (content.trim().length < 10) {
                    console.warn('[JanManager] Content seems empty after inject, retrying with paste event');
                    /* 譛邨よ焔谿ｵ: DataTransfer paste */
                    try {
                        el.focus();
                        var dt = new DataTransfer();
                        dt.setData('text/plain', prompt);
                        el.dispatchEvent(new ClipboardEvent('paste', {
                            bubbles: true, cancelable: true, clipboardData: dt
                        }));
                    } catch(e) { console.error('[JanManager] Paste fallback failed:', e); }
                }
                /* Phase 3: 騾∽ｿ｡ (縺輔ｉ縺ｫ800ms蠕・ｩ・ */
                setTimeout(function(){ doSend(el); }, 800);
            }, 500);
        }

        /* ==== Phase 3: 騾∽ｿ｡ ==== */
        function doSend(inputEl) {
            console.log('[JanManager] doSend: looking for send button');
            var sent = false;

            for (var i = 0; i < sendSelectors.length; i++) {
                try {
                    var btn = document.querySelector(sendSelectors[i]);
                    if (btn && !btn.disabled) {
                        console.log('[JanManager] Send button found: ' + sendSelectors[i]);
                        btn.click();
                        sent = true;
                        break;
                    }
                } catch(e) {}
            }

            /* Perplexity: data-testid 邉ｻ繧りｩｦ縺・*/
            if (!sent) {
                var alt = document.querySelector('[data-testid="ask-input-submit"]');
                if (alt && !alt.disabled) {
                    console.log('[JanManager] Send button found by data-testid');
                    alt.click();
                    sent = true;
                }
            }

            /* Enter繧ｭ繝ｼ繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ */
            if (!sent) {
                console.log('[JanManager] No send button found, trying Enter key');
                try {
                    inputEl.focus();
                    inputEl.dispatchEvent(new KeyboardEvent('keydown', {
                        key: 'Enter', code: 'Enter', keyCode: 13, which: 13,
                        bubbles: true, cancelable: true
                    }));
                    inputEl.dispatchEvent(new KeyboardEvent('keypress', {
                        key: 'Enter', code: 'Enter', keyCode: 13, which: 13,
                        bubbles: true, cancelable: true
                    }));
                    inputEl.dispatchEvent(new KeyboardEvent('keyup', {
                        key: 'Enter', code: 'Enter', keyCode: 13, which: 13,
                        bubbles: true, cancelable: true
                    }));
                } catch(e) {}
            }

            console.log('[JanManager] Send attempted (sent=' + sent + '), starting response polling');

            /* ==== Phase 4: 繝ｬ繧ｹ繝昴Φ繧ｹ螳牙ｮ壼愛螳・==== */
            var lastLen = 0;
            var stableCnt = 0;
            var pollCount = 0;
            var maxPoll = 160;

            var t = setInterval(function() {
                pollCount++;
                if (pollCount > maxPoll) {
                    console.error('[JanManager] Response polling timeout');
                    if (typeof JanBridge !== 'undefined') JanBridge.onError('繝ｬ繧ｹ繝昴Φ繧ｹ繧ｿ繧､繝繧｢繧ｦ繝・);
                    clearInterval(t);
                    return;
                }

                for (var i = 0; i < responseSelectors.length; i++) {
                    try {
                        var els = document.querySelectorAll(responseSelectors[i]);
                        if (els.length > 0) {
                            var lastEl = els[els.length - 1];
                            var text = (lastEl.innerText || lastEl.textContent || '').trim();
                            var currentLen = text.length;

                            if (currentLen > 40 && currentLen === lastLen) {
                                stableCnt++;
                            } else {
                                stableCnt = 0;
                            }
                            lastLen = currentLen;

                            if (stableCnt >= 6) {
                                clearInterval(t);
                                console.log('[JanManager] Response stable, length=' + currentLen);
                                if (typeof JanBridge !== 'undefined') {
                                    JanBridge.onResult(text);
                                } else {
                                    console.error('[JanManager] JanBridge is not available!');
                                }
                                return;
                            }
                            break;
                        }
                    } catch(e) {}
                }

                if (pollCount % 20 === 0) {
                    console.log('[JanManager] Polling response... ' + pollCount + '/' + maxPoll + ', lastLen=' + lastLen);
                }
            }, 500);
        }

        /* ==== 螳溯｡碁幕蟋・==== */
        /* Bridge蟄伜惠繝√ぉ繝・け */
        if (typeof JanBridge === 'undefined') {
            console.error('[JanManager] JanBridge is NOT defined! addJavascriptInterface may have failed.');
        } else {
            console.log('[JanManager] JanBridge is available');
        }

        waitForInput();
        
    } catch(err) {
        console.error('[JanManager] CRITICAL JS ERROR:', err);
        if (typeof JanBridge !== 'undefined') {
            JanBridge.onError('JS螳溯｡梧凾繧ｨ繝ｩ繝ｼ: ' + err.message);
        }
    }
})();
        """.trimIndent()
    }

    // ---------------------------------------------------------------
    // 繝倥Ν繝代・
    // ---------------------------------------------------------------
    private fun buildSelectorList(selectors: List<String>, manual: String?): List<String> {
        return (if (manual.isNullOrEmpty()) emptyList() else listOf(manual)) + selectors
    }

    private fun escapeSelectorForJs(selector: String): String {
        return selector.replace("\\", "\\\\").replace("'", "\\'")
    }

    private suspend fun evaluateJsSync(script: String): String? {
        val wv = webView ?: return null
        return try {
            withTimeout(10_000L) {
                suspendCancellableCoroutine { continuation ->
                    wv.post {
                        wv.evaluateJavascript(script) { result ->
                            if (continuation.isActive) {
                                continuation.resumeWith(Result.success(result))
                            }
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) { null }
    }

    private fun normalizeJsResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "\"null\"") return null
        return raw.trim().removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
            .trim()
    }

    companion object {
        private const val TAG = "AiWebViewInteractor"
    }
}
