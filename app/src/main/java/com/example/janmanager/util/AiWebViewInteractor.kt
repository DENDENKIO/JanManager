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

    data class FetchResult(
        val success: Boolean,
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

        // 前回のコールバックが残っていたらキャンセルして競合を防ぐ
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

        val inputArr  = allInput.joinToString(",")    { "'${escapeSelectorForJs(it)}'" }
        val sendArr   = allSend.joinToString(",")     { "'${escapeSelectorForJs(it)}'" }
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
            return FetchResult(false, errorMessage = "タイムアウト（90秒）")
        }

        if (rawResponse.isNullOrBlank()) {
            return FetchResult(false, errorMessage = "AIからのレスポンスが空でした")
        }

        onStatus("$janCode パース中...")
        Log.d(TAG, "Raw response length: ${rawResponse.length}")

        return when (val parseResult = AiResponseParser.parseResponse(rawResponse, janCode)) {
            is AiParseResult.Success     -> FetchResult(true, data = parseResult.data)
            is AiParseResult.NotFound    -> FetchResult(true, data = AiResponseData(jan_code = janCode, not_found = true))
            is AiParseResult.JanMismatch -> FetchResult(false, errorMessage = "JAN不一致: expected=$janCode, actual=${parseResult.actual}")
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
    //
    // 修正ポイント:
    //  1. 送信前に既存レスポンス要素数(baseResponseCount)を記録
    //     → 送信後は要素数が増えた（新しい応答が追加された）後のみ監視開始
    //     → 前回の応答を誤って安定判定しなくなる
    //  2. 送信成功後に入力欄を明示的にクリア
    //     → 次回ループで前回プロンプトが混入しなくなる
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
        /* --- Base64 decode --- */
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

        var inputSelectors   = [$inputSelArr];
        var sendSelectors    = [$sendSelArr];
        var responseSelectors = [$responseSelArr];

        /* ==== Phase 1: 入力欄を探す (最大30秒) ==== */
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
                    /* execCommandが効かない場合のフォールバック */
                    if ((el.innerText || '').trim().length > 0) {
                        el.innerText = '';
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                } else {
                    el.value = '';
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
                console.log('[JanManager] Input cleared');
            } catch(e) {
                console.warn('[JanManager] clearInput failed:', e);
            }
        }

        function waitForInput() {
            var el = findInput();
            if (el) {
                console.log('[JanManager] Input element found');
                setTimeout(function(){ doInject(el); }, 500);
                return;
            }
            findTries++;
            if (findTries >= findMax) {
                console.error('[JanManager] Input not found after ' + findMax + ' tries');
                if (typeof JanBridge !== 'undefined') JanBridge.onError('入力欄が見つかりません（30秒タイムアウト）');
                return;
            }
            if (findTries % 10 === 0) {
                console.log('[JanManager] Waiting for input... try ' + findTries + '/' + findMax);
            }
            setTimeout(waitForInput, 300);
        }

        /* ==== Phase 2: プロンプト注入 ==== */
        function doInject(el) {
            console.log('[JanManager] doInject: tag=' + el.tagName + ', contentEditable=' + el.contentEditable);

            /* 注入前に入力欄をクリア（前回プロンプト混入防止） */
            clearInput(el);

            el.focus();
            el.click();

            var isContentEditable = (el.contentEditable === 'true' ||
                el.contentEditable === 'inherit' ||
                el.getAttribute('contenteditable') === 'true');

            /* 200ms待ってからテキスト挿入（クリア後の安定を待つ） */
            setTimeout(function() {
                if (isContentEditable) {
                    console.log('[JanManager] ContentEditable mode');
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
                    document.execCommand('selectAll', false, null);
                    document.execCommand('delete', false, null);
                    var insertOk = document.execCommand('insertText', false, prompt);
                    console.log('[JanManager] execCommand insertText result=' + insertOk);

                    if (!insertOk || (el.innerText || '').trim().length < 10) {
                        console.log('[JanManager] insertText failed, trying innerText fallback');
                        el.innerText = prompt;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                } else {
                    console.log('[JanManager] Textarea/Input mode');
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

                /* 注入確認 */
                setTimeout(function() {
                    var content = el.innerText || el.value || '';
                    console.log('[JanManager] After inject, content length=' + content.trim().length);
                    if (content.trim().length < 10) {
                        console.warn('[JanManager] Content seems empty after inject, retrying with paste event');
                        try {
                            el.focus();
                            var dt = new DataTransfer();
                            dt.setData('text/plain', prompt);
                            el.dispatchEvent(new ClipboardEvent('paste', {
                                bubbles: true, cancelable: true, clipboardData: dt
                            }));
                        } catch(e) { console.error('[JanManager] Paste fallback failed:', e); }
                    }
                    /* 送信前に既存レスポンス数を記録 */
                    var baseResponseCount = countCurrentResponses();
                    console.log('[JanManager] baseResponseCount=' + baseResponseCount);
                    setTimeout(function(){ doSend(el, baseResponseCount); }, 800);
                }, 500);
            }, 200);
        }

        /* ==== Phase 3: 送信 ==== */
        function doSend(inputEl, baseResponseCount) {
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

            if (!sent) {
                var alt = document.querySelector('[data-testid="ask-input-submit"]');
                if (alt && !alt.disabled) {
                    console.log('[JanManager] Send button found by data-testid');
                    alt.click();
                    sent = true;
                }
            }

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

            /* 送信後に入力欄をクリア（次回ループへの混入防止） */
            setTimeout(function() {
                var inputEl2 = findInput();
                if (inputEl2) clearInput(inputEl2);
            }, 1500);

            console.log('[JanManager] Send attempted (sent=' + sent + '), waiting for new response element...');

            /* ==== Phase 4: 新しいレスポンス要素の出現を待つ ==== */
            var waitNewTries = 0;
            var waitNewMax   = 60;  /* 最大30秒待機 */

            var tWait = setInterval(function() {
                waitNewTries++;
                if (waitNewTries > waitNewMax) {
                    clearInterval(tWait);
                    console.error('[JanManager] New response element did not appear');
                    if (typeof JanBridge !== 'undefined') JanBridge.onError('新しいレスポンス要素が出現しませんでした');
                    return;
                }
                var currentCount = countCurrentResponses();
                if (currentCount > baseResponseCount) {
                    clearInterval(tWait);
                    console.log('[JanManager] New response element appeared (count: ' +
                        baseResponseCount + ' -> ' + currentCount + '). Start polling.');
                    /* ==== Phase 5: レスポンス安定判定（新しい要素のみ対象） ==== */
                    doWaitStable(currentCount - 1);
                }
            }, 500);
        }

        /* ==== Phase 5: レスポンス安定判定 ==== */
        function doWaitStable(targetIndex) {
            var lastLen  = 0;
            var stableCnt = 0;
            var pollCount = 0;
            var maxPoll  = 160;  /* 最大80秒 */

            var t = setInterval(function() {
                pollCount++;
                if (pollCount > maxPoll) {
                    clearInterval(t);
                    console.error('[JanManager] Response polling timeout');
                    if (typeof JanBridge !== 'undefined') JanBridge.onError('レスポンスタイムアウト');
                    return;
                }

                for (var i = 0; i < responseSelectors.length; i++) {
                    try {
                        var els = document.querySelectorAll(responseSelectors[i]);
                        /* targetIndex番目の要素を監視（送信後に追加された要素） */
                        if (els.length > targetIndex) {
                            var el  = els[targetIndex];
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
                    console.log('[JanManager] Polling... ' + pollCount + '/' + maxPoll +
                        ', lastLen=' + lastLen + ', stableCnt=' + stableCnt);
                }
            }, 500);
        }

        /* ==== 実行開始 ==== */
        if (typeof JanBridge === 'undefined') {
            console.error('[JanManager] JanBridge is NOT defined!');
        } else {
            console.log('[JanManager] JanBridge is available');
        }

        waitForInput();

    } catch(err) {
        console.error('[JanManager] CRITICAL JS ERROR:', err);
        if (typeof JanBridge !== 'undefined') {
            JanBridge.onError('JS実行時エラー: ' + err.message);
        }
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
