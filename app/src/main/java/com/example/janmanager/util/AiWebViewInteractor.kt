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

/**
 * GikobunAI実証済み方式によるAIチャット自動操作.
 *
 * 核心:
 *  1回のevaluateJavascriptで「要素待ち→注入→送信→レスポンス安定判定→コールバック」を
 *  すべてJS側のsetTimeout/setIntervalで完結させる.
 *  結果は AndroidBridge.onResult() JavascriptInterface でKotlin側に返す.
 */
class AiWebViewInteractor {

    var webView: WebView? = null
        set(value) {
            field = value
            value?.let { registerBridge(it) }
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    // セレクタ設定
    private var inputSelectors: List<String> = WebViewJsHelper.GEMINI_INPUT_SELECTORS
    private var sendSelectors: List<String> = WebViewJsHelper.GEMINI_SEND_SELECTORS
    private var responseSelectors: List<String> = WebViewJsHelper.GEMINI_RESPONSE_SELECTORS
    private var manualInputSelector: String? = null
    private var manualSendButtonSelector: String? = null
    private var manualResponseSelector: String? = null

    // コールバック管理（1件ずつ処理するので1つあれば十分）
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
    // AndroidBridge: JS → Kotlin コールバック
    // ---------------------------------------------------------------
    private fun registerBridge(wv: WebView) {
        try { wv.removeJavascriptInterface("JanBridge") } catch (_: Exception) {}
        wv.addJavascriptInterface(BridgeInterface(), "JanBridge")
    }

    inner class BridgeInterface {
        @JavascriptInterface
        fun onResult(text: String?) {
            mainHandler.post {
                pendingResultCallback?.invoke(text)
                pendingResultCallback = null
            }
        }

        @JavascriptInterface
        fun onError(message: String?) {
            mainHandler.post {
                Log.w(TAG, "JS onError: $message")
                pendingResultCallback?.invoke(null)
                pendingResultCallback = null
            }
        }

        @JavascriptInterface
        fun onStatus(status: String?) {
            // 将来のステータス表示用（現在は未使用）
            Log.d(TAG, "JS status: $status")
        }
    }

    // ---------------------------------------------------------------
    // データクラス
    // ---------------------------------------------------------------
    data class FetchResult(
        val success: Boolean,
        val data: AiResponseData? = null,
        val errorMessage: String? = null
    )

    // ---------------------------------------------------------------
    // メインフロー（GikobunAI方式: 1回のJS実行で全完結）
    // ---------------------------------------------------------------
    suspend fun executeFullFlow(
        janCode: String,
        targetBaseUrl: String,
        onStatus: (String) -> Unit
    ): FetchResult {
        val wv = webView ?: return FetchResult(false, errorMessage = "WebViewが初期化されていません")

        onStatus("$janCode 実行中...")

        val prompt = AiPromptBuilder.buildPrompt(janCode)
        val promptB64 = Base64.encodeToString(
            prompt.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        // 入力セレクタ配列
        val allInputSelectors = buildSelectorList(inputSelectors, manualInputSelector)
        val allSendSelectors = buildSelectorList(sendSelectors, manualSendButtonSelector)
        val allResponseSelectors = buildSelectorList(responseSelectors, manualResponseSelector)

        val inputSelArr = allInputSelectors.joinToString(",") { "'$it'" }
        val sendSelArr = allSendSelectors.joinToString(",") { "'$it'" }
        val responseSelArr = allResponseSelectors.joinToString(",") { "'$it'" }

        // GikobunAI方式の統合JS
        val js = buildAllInOneJs(promptB64, inputSelArr, sendSelArr, responseSelArr)

        // JSを実行し、AndroidBridge.onResult() コールバックを待つ
        val rawResponse: String? = try {
            withTimeout(90_000L) { // 90秒タイムアウト
                suspendCancellableCoroutine { continuation ->
                    pendingResultCallback = { result ->
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                    mainHandler.post {
                        wv.evaluateJavascript(js, null)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            pendingResultCallback = null
            return FetchResult(false, errorMessage = "タイムアウト（90秒）")
        }

        if (rawResponse.isNullOrBlank()) {
            return FetchResult(false, errorMessage = "AIからのレスポンスが空でした")
        }

        onStatus("$janCode パース中...")

        // レスポンスをパース
        return when (val parseResult = AiResponseParser.parseResponse(rawResponse, janCode)) {
            is AiParseResult.Success -> FetchResult(true, data = parseResult.data)
            is AiParseResult.NotFound -> FetchResult(true, data = AiResponseData(jan_code = janCode, not_found = true))
            is AiParseResult.JanMismatch -> FetchResult(false, errorMessage = "JAN不一致: expected=$janCode, actual=${parseResult.actual}")
            is AiParseResult.InvalidFormat -> FetchResult(false, errorMessage = "レスポンスの形式が不正です")
        }
    }

    // ---------------------------------------------------------------
    // 手動取り込み（既存レスポンスの取得用）
    // ---------------------------------------------------------------
    suspend fun extractCurrentResponse(janCode: String): AiParseResult? {
        val wv = webView ?: return null
        val allResponseSelectors = buildSelectorList(responseSelectors, manualResponseSelector)
        val responseSelArr = allResponseSelectors.joinToString(",") { "'$it'" }

        val extractJs = """
(function() {
    var selectors = [$responseSelArr];
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
    // GikobunAI方式: 統合JS生成
    // 「要素待ち→execCommand注入→送信→レスポンス安定判定→コールバック」を
    // 1つのJS内で完結させる.
    // ---------------------------------------------------------------
    private fun buildAllInOneJs(
        promptB64: String,
        inputSelArr: String,
        sendSelArr: String,
        responseSelArr: String
    ): String {
        return """
(function() {
    var b64 = '$promptB64';
    var bytes = Uint8Array.from(atob(b64), function(c){ return c.charCodeAt(0); });
    var prompt = new TextDecoder('utf-8').decode(bytes);

    var inputSelectors = [$inputSelArr];
    var sendSelectors = [$sendSelArr];
    var responseSelectors = [$responseSelArr];

    /* ---- Phase 1: 入力欄を探す（最大20秒 = 300ms × 66回） ---- */
    var findTries = 0;
    var findMax = 66;
    function findInput() {
        for (var i = 0; i < inputSelectors.length; i++) {
            try {
                var el = document.querySelector(inputSelectors[i]);
                if (el) return el;
            } catch(e) {}
        }
        return null;
    }

    function waitForInput() {
        var el = findInput();
        if (el) {
            doInject(el);
            return;
        }
        findTries++;
        if (findTries >= findMax) {
            JanBridge.onError('入力欄が見つかりません');
            return;
        }
        setTimeout(waitForInput, 300);
    }

    /* ---- Phase 2: プロンプト注入 (execCommand方式 = GikobunAI実証済み) ---- */
    function doInject(el) {
        el.focus();

        /* contenteditable の場合: カーソルを中の<p>等に設置 */
        var p = el.querySelector('p');
        if (p) {
            var range = document.createRange();
            range.selectNodeContents(p);
            range.collapse(false);
            var sel = window.getSelection();
            sel.removeAllRanges();
            sel.addRange(range);
        }

        /* 全選択→削除→挿入 (Chrome WebViewで最も安定する方法) */
        document.execCommand('selectAll', false, null);
        document.execCommand('delete', false, null);
        document.execCommand('insertText', false, prompt);

        /* Phase 3: 送信（800ms後） */
        setTimeout(function() { doSend(el); }, 800);
    }

    /* ---- Phase 3: 送信ボタン押下 ---- */
    function doSend(inputEl) {
        var sent = false;
        for (var i = 0; i < sendSelectors.length; i++) {
            try {
                var btn = document.querySelector(sendSelectors[i]);
                if (btn && !btn.disabled) {
                    btn.click();
                    sent = true;
                    break;
                }
            } catch(e) {}
        }
        /* ボタンが見つからなければEnterキー送信 */
        if (!sent) {
            try {
                inputEl.dispatchEvent(new KeyboardEvent('keydown', {
                    key: 'Enter', code: 'Enter', keyCode: 13, which: 13,
                    bubbles: true, cancelable: true
                }));
            } catch(e) {}
        }

        /* Phase 4: レスポンス安定判定（GikobunAI方式: setIntervalポーリング） */
        var lastLen = 0;
        var stableCnt = 0;
        var pollCount = 0;
        var maxPoll = 160; /* 500ms × 160 = 80秒 */
        var t = setInterval(function() {
            pollCount++;
            if (pollCount > maxPoll) {
                clearInterval(t);
                JanBridge.onError('レスポンスタイムアウト');
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
                        /* 6回連続安定 (= 3秒間変化なし) で完了とみなす */
                        if (stableCnt >= 6) {
                            clearInterval(t);
                            JanBridge.onResult(text);
                            return;
                        }
                        break; /* 最初にヒットしたセレクタだけ使う */
                    }
                } catch(e) {}
            }
        }, 500);
    }

    /* 実行開始 */
    waitForInput();
})();
        """.trimIndent()
    }

    // ---------------------------------------------------------------
    // ヘルパー
    // ---------------------------------------------------------------
    private fun buildSelectorList(selectors: List<String>, manual: String?): List<String> {
        return (if (manual.isNullOrEmpty()) emptyList() else listOf(manual)) + selectors
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
