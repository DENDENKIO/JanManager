package com.example.janmanager.util

import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * WebView上のAIチャットサイト(Gemini/Perplexity)に対する
 * プロンプト注入→送信→レスポンス取得の共通ロジック。
 *
 * 使い方:
 *   val interactor = AiWebViewInteractor()
 *   interactor.webView = wv
 *   interactor.configure(inputSelectors, sendSelectors, responseSelectors, manualInput, manualSend, manualResponse)
 *   val result = interactor.executeFullFlow(janCode, targetBaseUrl) { status -> updateUi(status) }
 */
class AiWebViewInteractor {

    var webView: WebView? = null

    private var inputSelectors: List<String> = WebViewJsHelper.GEMINI_INPUT_SELECTORS
    private var sendSelectors: List<String> = WebViewJsHelper.GEMINI_SEND_SELECTORS
    private var responseSelectors: List<String> = WebViewJsHelper.GEMINI_RESPONSE_SELECTORS
    private var manualInputSelector: String? = null
    private var manualSendButtonSelector: String? = null
    private var manualResponseSelector: String? = null

    private var prevResponseCount: Int = 0

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
    // メインフロー: ページ待機 → 入力欄待機 → 注入 → 送信 → レスポンス取得
    // ---------------------------------------------------------------

    data class FetchResult(
        val success: Boolean,
        val data: AiResponseData? = null,
        val errorMessage: String? = null
    )

    /**
     * 1件のJANコードに対するAI取得フルフローを実行する。
     * @param janCode 対象JANコード
     * @param targetBaseUrl "gemini.google.com" or "perplexity.ai"
     * @param onStatus ステータス更新コールバック（UI更新用）
     * @return FetchResult
     */
    suspend fun executeFullFlow(
        janCode: String,
        targetBaseUrl: String,
        onStatus: (String) -> Unit
    ): FetchResult {
        // Phase 1: ページ遷移待ち
        onStatus("$janCode ページ確認中...")
        val pageReady = waitForPage(targetBaseUrl, onStatus)
        if (!pageReady) {
            return FetchResult(false, errorMessage = "ページが読み込まれませんでした。AIサイトにログインしてください。")
        }

        // Phase 2: 入力欄DOM出現待ち
        onStatus("$janCode 入力欄の読み込み待ち...")
        val inputReady = waitForInputElement(onStatus)
        if (!inputReady) {
            return FetchResult(false, errorMessage = "入力欄が見つかりません。AIサイトにログインしているか、セレクタ設定を確認してください。")
        }
        // DOMが出現してもイベントリスナー未登録の可能性があるため少し待つ
        delay(1000)

        // Phase 3: プロンプト注入 → 反映確認 → 送信（リトライ付き）
        val prompt = AiPromptBuilder.buildPrompt(janCode)
        val injectAndSendResult = injectAndSend(janCode, prompt, onStatus)
        if (!injectAndSendResult) {
            return FetchResult(false, errorMessage = "プロンプトの注入または送信に失敗しました。AIサイトの構造が変わった可能性があります。")
        }

        // Phase 4: レスポンスポーリング
        onStatus("$janCode レスポンス待ち...")
        delay(3000)
        val responseData = pollResponse(janCode, onStatus)
        return if (responseData != null) {
            FetchResult(true, data = responseData)
        } else {
            FetchResult(false, errorMessage = "レスポンス取得がタイムアウトしました。")
        }
    }

    // ---------------------------------------------------------------
    // Phase 1: ページ遷移待ち（最大15秒）
    // ---------------------------------------------------------------
    private suspend fun waitForPage(targetBaseUrl: String, onStatus: (String) -> Unit): Boolean {
        for (wait in 0 until 15) {
            val currentUrl = normalizeJsResult(evaluateJsSync("window.location.href")) ?: ""
            if (currentUrl.contains(targetBaseUrl)) return true
            onStatus("ページ遷移待ち... (${wait + 1}/15)")
            delay(1000)
        }
        return false
    }

    // ---------------------------------------------------------------
    // Phase 2: 入力欄DOM出現待ち（最大20秒）
    // ---------------------------------------------------------------
    private suspend fun waitForInputElement(onStatus: (String) -> Unit): Boolean {
        val checkJs = WebViewJsHelper.getCheckInputExistsJs(inputSelectors, manualInputSelector)
        for (wait in 0 until 20) {
            val result = normalizeJsResult(evaluateJsSync(checkJs))
            if (result == "ready") return true
            onStatus("入力欄の読み込み待ち... (${wait + 1}/20)")
            delay(1000)
        }
        return false
    }

    // ---------------------------------------------------------------
    // Phase 3: プロンプト注入 → 反映確認 → 送信（最大3回リトライ）
    // ---------------------------------------------------------------
    private suspend fun injectAndSend(janCode: String, prompt: String, onStatus: (String) -> Unit): Boolean {
        for (retry in 0 until 3) {
            onStatus("$janCode プロンプト注入中... (試行 ${retry + 1}/3)")

            // 3-1. プロンプト注入
            val injectJs = WebViewJsHelper.getInjectPromptJsWithFallback(inputSelectors, manualInputSelector, prompt)
            val injectResult = normalizeJsResult(evaluateJsSync(injectJs))
            if (injectResult != "true") {
                Log.w(TAG, "Inject failed: result=$injectResult")
                delay(2000)
                continue
            }

            // 3-2. エディタに内容が反映されるまで確認（最大5秒）
            val contentCheckJs = WebViewJsHelper.getCheckInputHasContentJs(inputSelectors, manualInputSelector)
            var contentReady = false
            for (checkWait in 0 until 10) {
                delay(500)
                val contentResult = normalizeJsResult(evaluateJsSync(contentCheckJs))
                if (contentResult == "has_content") {
                    contentReady = true
                    break
                }
            }
            if (!contentReady) {
                Log.w(TAG, "Content not reflected in editor after inject")
                delay(1000)
                continue
            }

            // 3-3. 送信前にレスポンス要素数を記録
            val countJs = WebViewJsHelper.getCountResponseElementsJs(responseSelectors, manualResponseSelector)
            val prevCountStr = normalizeJsResult(evaluateJsSync(countJs))
            prevResponseCount = prevCountStr?.toIntOrNull() ?: 0

            // 3-4. 送信ボタンクリック
            delay(500)
            val sendJs = WebViewJsHelper.getClickSendJsWithFallback(sendSelectors, manualSendButtonSelector)
            val sendResult = normalizeJsResult(evaluateJsSync(sendJs))
            if (sendResult == "true") {
                return true // 成功
            }

            Log.w(TAG, "Send button click failed: result=$sendResult")
            delay(2000)
        }
        return false // 3回リトライしても失敗
    }

    // ---------------------------------------------------------------
    // Phase 4: レスポンスポーリング（最大45秒）
    // ---------------------------------------------------------------
    private suspend fun pollResponse(janCode: String, onStatus: (String) -> Unit): AiResponseData? {
        val countJs = WebViewJsHelper.getCountResponseElementsJs(responseSelectors, manualResponseSelector)
        val extractJs = WebViewJsHelper.getExtractResponseJsWithFallback(responseSelectors, manualResponseSelector)

        for (i in 0 until 45) {
            onStatus("$janCode レスポンス待ち... (${i + 1}/45)")

            // 新しいレスポンス要素が増えたかチェック
            val currentCountStr = normalizeJsResult(evaluateJsSync(countJs))
            val currentCount = currentCountStr?.toIntOrNull() ?: 0
            if (currentCount <= prevResponseCount) {
                delay(1000)
                continue
            }

            // 新レスポンスが出現したので内容を取得
            val rawResponse = normalizeJsResult(evaluateJsSync(extractJs))
            if (!rawResponse.isNullOrBlank()) {
                when (val parseResult = AiResponseParser.parseResponse(rawResponse, janCode)) {
                    is AiParseResult.Success -> return parseResult.data
                    is AiParseResult.NotFound -> return AiResponseData(jan_code = janCode, not_found = true)
                    is AiParseResult.JanMismatch -> {
                        onStatus("JAN不一致: ${parseResult.actual}")
                    }
                    is AiParseResult.InvalidFormat -> {
                        // まだ生成途中の可能性 → ポーリング続行
                    }
                }
            }
            delay(1000)
        }
        return null
    }

    // ---------------------------------------------------------------
    // WebView経由のレスポンス手動取り込み（UI上のボタンから呼ばれる用）
    // ---------------------------------------------------------------
    suspend fun extractCurrentResponse(janCode: String): AiParseResult? {
        val extractJs = WebViewJsHelper.getExtractResponseJsWithFallback(responseSelectors, manualResponseSelector)
        val rawResponse = normalizeJsResult(evaluateJsSync(extractJs))
        if (rawResponse.isNullOrBlank()) return null
        return AiResponseParser.parseResponse(rawResponse, janCode)
    }

    // ---------------------------------------------------------------
    // evaluateJavascript ラッパー（タイムアウト付き）
    // ---------------------------------------------------------------
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
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "evaluateJsSync timed out")
            null
        }
    }

    // ---------------------------------------------------------------
    // evaluateJavascript の返り値を正規化
    // Android WebView は JS文字列を "\"value\"" の形で返すため外側のクォート等を除去
    // ---------------------------------------------------------------
    private fun normalizeJsResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "\"null\"") return null
        return raw
            .trim()
            .removeSurrounding("\"")
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
