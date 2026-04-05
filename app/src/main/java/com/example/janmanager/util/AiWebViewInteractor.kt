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
    // Bridge登録（UIスレッド保証）
    // ---------------------------------------------------------------
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
                val cb = pendingResultCallback
                pendingResultCallback = null
                cb?.invoke(text)
            }
        }

        @JavascriptInterface
        fun onError(message: String?) {
            Log.w(TAG, "onError called: $message")
            mainHandler.post {
                val cb = pendingResultCallback
                pendingResultCallback = null
                cb?.invoke(null)
            }
        }
    }

    /**
     * isNotFound=true の場合は success=falseで返す。
     * これにより ViewModel の when 分岐で明確に判定できる。
     */
    data class FetchResult(
        val success: Boolean,
        val isNotFound: Boolean = false,
        val data: AiResponseData? = null,
        val errorMessage: String? = null
    )

    // ---------------------------------------------------------------
    // メインフロー
    // ---------------------------------------------------------------
    suspend fun executeFullFlow(
        janCode: String,
        targetBaseUrl: String,
        onStatus: (String) -> Unit
    ): FetchResult {
        val wv = webView
        if (wv == null) {
            Log.e(TAG, "webView is null")
            return FetchResult(false, errorMessage = "WebViewが初期化されていません")
        }

        pendingResultCallback = null

        onStatus("$janCode 実行中...")

        val prompt = AiPromptBuilder.buildPrompt(janCode)
        Log.d(TAG, "Prompt generated for $janCode (${prompt.length} chars)")

        val promptB64 = Base64.encodeToString(
            prompt.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        val allInput = buildSelectorList(inputSelectors, manualInputSelector)
        val allSend = buildSelectorList(sendSelectors, manualSendButtonSelector)
        val allResponse = buildSelectorList(responseSelectors, manualResponseSelector)

        val inputArr    = allInput.joinToString(",")    { "'${escapeSelectorForJs(it)}'" }
        val sendArr     = allSend.joinToString(",")     { "'${escapeSelectorForJs(it)}'" }
        val responseArr = allResponse.joinToString(",") { "'${escapeSelectorForJs(it)}'" }

        val js = buildAllInOneJs(promptB64, inputArr, sendArr, responseArr)
        Log.d(TAG, "JS script length: ${js.length}")

        val rawResponse: String? = try {
            withTimeout(90_000L) {
                suspendCancellableCoroutine { continuation ->
                    pendingResultCallback = { result ->
                        if (continuation.isActive) continuation.resume(result)
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
            return FetchResult(false, errorMessage = "タイムアウト（90秒）")
        }

        if (rawResponse.isNullOrBlank()) {
            return FetchResult(false, errorMessage = "AIからのレスポンスが空でした")
        }

        onStatus("$janCode パース中...")
        Log.d(TAG, "Raw response length: ${rawResponse.length}")

        return when (val parseResult = AiResponseParser.parseResponse(rawResponse, janCode)) {
            is AiParseResult.Success       -> FetchResult(success = true, data = parseResult.data)
            is AiParseResult.NotFound      -> {
                Log.i(TAG, "NotFound -> isNotFound=true")
                FetchResult(success = false, isNotFound = true)
            }
            is AiParseResult.JanMismatch   -> FetchResult(false, errorMessage = "JAN不一致: expected=$janCode, actual=${parseResult.actual}")
            is AiParseResult.InvalidFormat -> FetchResult(false, errorMessage = "レスポンスの形式が不正です")
        }
    }

    // ---------------------------------------------------------------
    // 手動取り込み
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
    // 統合JS
    // ---------------------------------------------------------------
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
        var b64 = '$promptB64';
        var prompt;
        try {
            var bytes = Uint8Array.from(atob(b64), function(c){ return c.charCodeAt(0); });
            prompt = new TextDecoder('utf-8').decode(bytes);
        } catch(e) {
            console.error('[JanManager] Base64 decode failed:', e);
            if (typeof JanBridge !== 'undefined') JanBridge.onError('Base64デコード失敗: ' + e.message);
            return;
        }
        console.log('[JanManager] Prompt decoded, length=' + prompt.length);

        var inputSelectors    = [$inputSelArr];
        var sendSelectors     = [$sendSelArr];
        var responseSelectors = [$responseSelArr];

        var findTries = 0;
        var findMax   = 100;

        function findInput() {
            var byId = document.getElementById('ask-input');
            if (byId) return byId;
            for (var i = 0; i < inputSelectors.length; i++) {
                try {
                    var el = document.querySelector(inputSelectors[i]);
                    if (el) return el;
                } catch(e) {}
            }
            return null;
        }

        function countCurrentResponses() {
            for (var i = 0; i < responseSelectors.length; i++) {
                try {
                    var els = document.querySelectorAll(responseSelectors[i]);
                    if (els.length > 0) return els.length;
                } catch(e) {}
            }
            return 0;
        }

        function clearInput(el) {
            try {
                var isContentEditable = (el.contentEditable === 'true' ||
                    el.contentEditable === 'inherit' ||
                    el.getAttribute('contenteditable') === 'true');
                if (isContentEditable) {
                    el.focus();
                    document.execCommand('selectAll', false, null);
                    document.execCommand('delete', false, null);
                    if ((el.innerText || '').trim().length > 0) {
                        el.innerText = '';
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                } else {
                    el.value = '';
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
            } catch(e) {}
        }

        function waitForInput() {
            var el = findInput();
            if (el) {
                setTimeout(function(){ doInject(el); }, 500);
                return;
            }
            findTries++;
            if (findTries >= findMax) {
                if (typeof JanBridge !== 'undefined') JanBridge.onError('入力欄が見つかりません');
                return;
            }
            setTimeout(waitForInput, 300);
        }

        function doInject(el) {
            clearInput(el);
            el.focus();
            el.click();
            var isContentEditable = (el.contentEditable === 'true' ||
                el.contentEditable === 'inherit' ||
                el.getAttribute('contenteditable') === 'true');
            setTimeout(function() {
                if (isContentEditable) {
                    document.execCommand('selectAll', false, null);
                    document.execCommand('delete', false, null);
                    var insertOk = document.execCommand('insertText', false, prompt);
                    if (!insertOk || (el.innerText || '').trim().length < 10) {
                        el.innerText = prompt;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                } else {
                    var nativeSetter = Object.getOwnPropertyDescriptor(
                        el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value'
                    );
                    if (nativeSetter && nativeSetter.set) {
                        nativeSetter.set.call(el, prompt);
                    } else {
                        el.value = prompt;
                    }
                    el.dispatchEvent(new Event('input',  { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                setTimeout(function() {
                    var baseResponseCount = countCurrentResponses();
                    setTimeout(function(){ doSend(el, baseResponseCount); }, 800);
                }, 500);
            }, 200);
        }

        function doSend(inputEl, baseResponseCount) {
            var sent = false;
            for (var i = 0; i < sendSelectors.length; i++) {
                try {
                    var btn = document.querySelector(sendSelectors[i]);
                    if (btn && !btn.disabled) { btn.click(); sent = true; break; }
                } catch(e) {}
            }
            if (!sent) {
                var alt = document.querySelector('[data-testid="ask-input-submit"]');
                if (alt && !alt.disabled) { alt.click(); sent = true; }
            }
            if (!sent) {
                try {
                    inputEl.focus();
                    inputEl.dispatchEvent(new KeyboardEvent('keydown',  { key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true }));
                    inputEl.dispatchEvent(new KeyboardEvent('keypress', { key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true }));
                    inputEl.dispatchEvent(new KeyboardEvent('keyup',    { key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true }));
                } catch(e) {}
            }
            setTimeout(function() { var el2 = findInput(); if (el2) clearInput(el2); }, 1500);

            var waitNewTries = 0;
            var waitNewMax   = 60;
            var tWait = setInterval(function() {
                waitNewTries++;
                if (waitNewTries > waitNewMax) {
                    clearInterval(tWait);
                    if (typeof JanBridge !== 'undefined') JanBridge.onError('新しいレスポンス要素が出現しませんでした');
                    return;
                }
                var currentCount = countCurrentResponses();
                if (currentCount > baseResponseCount) {
                    clearInterval(tWait);
                    doWaitStable(currentCount - 1);
                }
            }, 500);
        }

        function doWaitStable(targetIndex) {
            var lastLen   = 0;
            var stableCnt = 0;
            var pollCount = 0;
            var maxPoll   = 160;
            var t = setInterval(function() {
                pollCount++;
                if (pollCount > maxPoll) {
                    clearInterval(t);
                    if (typeof JanBridge !== 'undefined') JanBridge.onError('レスポンスタイムアウト');
                    return;
                }
                for (var i = 0; i < responseSelectors.length; i++) {
                    try {
                        var els = document.querySelectorAll(responseSelectors[i]);
                        if (els.length > targetIndex) {
                            var el   = els[targetIndex];
                            var text = (el.innerText || el.textContent || '').trim();
                            var currentLen = text.length;
                            if (currentLen > 40 && currentLen === lastLen) {
                                stableCnt++;
                            } else {
                                stableCnt = 0;
                            }
                            lastLen = currentLen;
                            if (stableCnt >= 6) {
                                clearInterval(t);
                                if (typeof JanBridge !== 'undefined') JanBridge.onResult(text);
                                return;
                            }
                            break;
                        }
                    } catch(e) {}
                }
            }, 500);
        }

        if (typeof JanBridge === 'undefined') {
            console.error('[JanManager] JanBridge is NOT defined!');
        }
        waitForInput();

    } catch(err) {
        console.error('[JanManager] CRITICAL JS ERROR:', err);
        if (typeof JanBridge !== 'undefined') JanBridge.onError('JS実行時エラー: ' + err.message);
    }
})();
        """.trimIndent()
    }

    // ---------------------------------------------------------------
    // ヘルパー
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
                            if (continuation.isActive) continuation.resumeWith(Result.success(result))
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
