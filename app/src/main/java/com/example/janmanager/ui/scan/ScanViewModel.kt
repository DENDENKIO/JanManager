package com.example.janmanager.ui.scan

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.janmanager.data.local.entity.BarcodeType
import com.example.janmanager.data.local.entity.InfoSource
import com.example.janmanager.data.local.entity.ProductMaster
import com.example.janmanager.data.repository.GroupRepository
import com.example.janmanager.data.repository.ProductRepository
import com.example.janmanager.data.settings.SettingsDataStore
import com.example.janmanager.util.AiPromptBuilder
import com.example.janmanager.util.AiResponseData
import com.example.janmanager.util.AiResponseParser
import com.example.janmanager.util.AiParseResult
import com.example.janmanager.util.AiWebViewInteractor
import com.example.janmanager.util.JanCodeUtil
import com.example.janmanager.util.SoundHelper
import com.example.janmanager.util.WebViewJsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.janmanager.data.local.entity.OcrScanHistory
import com.example.janmanager.data.repository.OcrScanHistoryRepository
import com.example.janmanager.util.BarcodeImageGenerator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import javax.inject.Inject

enum class ScanModeTab {
    CONTINUOUS, CONFIRM, LINKAGE
}

data class RecentScan(
    val jan: String,
    val productName: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LinkageSlot {
    PIECE, PACK, CASE
}

data class DetectedText(
    val text: String,
    val boundingBox: android.graphics.Rect,
    val anchoredProductName: String? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val groupRepository: GroupRepository,
    private val ocrScanHistoryRepository: OcrScanHistoryRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val isItfEnabled = settingsDataStore.isItfEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanSoundEnabled = settingsDataStore.scanSoundEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentTab = MutableStateFlow(ScanModeTab.CONTINUOUS)
    val currentTab = _currentTab.asStateFlow()

    // Continuous Mode State
    private val _isLiveMode = MutableStateFlow(false)
    val isLiveMode = _isLiveMode.asStateFlow()
    
    private val _recentlyScanned = MutableStateFlow<List<RecentScan>>(emptyList())
    val recentlyScanned = _recentlyScanned.asStateFlow()

    // Confirm Mode State
    private val _confirmProduct = MutableStateFlow<ProductMaster?>(null)
    val confirmProduct = _confirmProduct.asStateFlow()
    private val _lastConfirmBarcode = MutableStateFlow("")
    val lastConfirmBarcode = _lastConfirmBarcode.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val confirmProductGroups: StateFlow<List<Int>> = _lastConfirmBarcode
        .flatMapLatest { jan ->
            if (jan.isEmpty()) return@flatMapLatest flowOf(emptyList<Int>())
            groupRepository.getActiveGroups().flatMapLatest { activeGroups ->
                if (activeGroups.isEmpty()) return@flatMapLatest flowOf(emptyList<Int>())
                val flows = activeGroups.map { group ->
                    groupRepository.getGroupItems(group.id).map { items -> 
                        if (items.any { it.janCode == jan }) group.tagColor else null
                    }
                }
                combine(flows) { colors -> colors.filterNotNull() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Fetch in Confirm Mode
    private val _showAiSheet = MutableStateFlow(false)
    val showAiSheet = _showAiSheet.asStateFlow()
    
    fun toggleLiveMode() {
        _isLiveMode.value = !_isLiveMode.value
    }
    
    private val _aiFetchStatus = MutableStateFlow("")
    val aiFetchStatus = _aiFetchStatus.asStateFlow()
    
    private val _aiResultPreview = MutableStateFlow<AiResponseData?>(null)
    val aiResultPreview = _aiResultPreview.asStateFlow()

    private val _aiUrl = MutableStateFlow<String?>(null)
    val aiUrl = _aiUrl.asStateFlow()

    private val interactor = AiWebViewInteractor()
    private var targetBaseUrl: String = "gemini.google.com"

    // Linkage Mode State (Packaging Units)
    private val _activeLinkageSlot = MutableStateFlow(LinkageSlot.PIECE)
    val activeLinkageSlot = _activeLinkageSlot.asStateFlow()
    
    private val _linkagePieceJan = MutableStateFlow("")
    val linkagePieceJan = _linkagePieceJan.asStateFlow()
    
    private val _linkagePackJan = MutableStateFlow("")
    val linkagePackJan = _linkagePackJan.asStateFlow()

    private val _linkageCaseJan = MutableStateFlow("")
    val linkageCaseJan = _linkageCaseJan.asStateFlow()

    private val _linkagePackQty = MutableStateFlow(6)
    val linkagePackQty = _linkagePackQty.asStateFlow()

    private val _linkageCaseQty = MutableStateFlow(12)
    val linkageCaseQty = _linkageCaseQty.asStateFlow()

    // OCR Mode State
    private val _currentOcrJan = MutableStateFlow("")
    val currentOcrJan = _currentOcrJan.asStateFlow()

    private val _currentBarcodeImage = MutableStateFlow<Bitmap?>(null)
    val currentBarcodeImage = _currentBarcodeImage.asStateFlow()

    // Photo Mode States
    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri = _photoUri.asStateFlow()

    private val _photoBitmap = MutableStateFlow<Bitmap?>(null)
    val photoBitmap = _photoBitmap.asStateFlow()

    private val _currentScale = MutableStateFlow(1f)
    val currentScale = _currentScale.asStateFlow()

    private val _currentOffset = MutableStateFlow(Offset.Zero)
    val currentOffset = _currentOffset.asStateFlow()

    private val _isTransformLocked = MutableStateFlow(false)
    val isTransformLocked = _isTransformLocked.asStateFlow()

    // null = 未取得, "" = 未登録, それ以外 = 商品名
    private val _currentProductName = MutableStateFlow<String?>(null)
    val currentProductName = _currentProductName.asStateFlow()

    private val _ocrError = MutableStateFlow(false)
    val ocrError = _ocrError.asStateFlow()

    private val _detectedTextBlocks = MutableStateFlow<List<DetectedText>>(emptyList())
    val detectedTextBlocks = _detectedTextBlocks.asStateFlow()

    private val _isOcrProcessing = MutableStateFlow(false)
    val isOcrProcessing = _isOcrProcessing.asStateFlow()

    val ocrScanHistory: StateFlow<List<OcrScanHistory>> = ocrScanHistoryRepository
        .getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val aiSelection = settingsDataStore.aiSelectionFlow.first()
            val inputSel: List<String>
            val sendSel: List<String>
            val responseSel: List<String>

            if (aiSelection == "PERPLEXITY") {
                _aiUrl.value = "https://www.perplexity.ai/"
                targetBaseUrl = "perplexity.ai"
                inputSel = WebViewJsHelper.PERPLEXITY_INPUT_SELECTORS
                sendSel = WebViewJsHelper.PERPLEXITY_SEND_SELECTORS
                responseSel = WebViewJsHelper.PERPLEXITY_RESPONSE_SELECTORS
            } else {
                _aiUrl.value = "https://gemini.google.com/app?hl=ja"
                targetBaseUrl = "gemini.google.com"
                inputSel = WebViewJsHelper.GEMINI_INPUT_SELECTORS
                sendSel = WebViewJsHelper.GEMINI_SEND_SELECTORS
                responseSel = WebViewJsHelper.GEMINI_RESPONSE_SELECTORS
            }

            var manualInput: String? = null
            var manualSend: String? = null
            var manualResponse: String? = null
            val config = settingsDataStore.selectorConfigFlow.first()
            if (config.isNotEmpty()) {
                val parts = config.split("|")
                if (parts.size == 3) {
                    manualInput = parts[0].ifEmpty { null }
                    manualSend = parts[1].ifEmpty { null }
                    manualResponse = parts[2].ifEmpty { null }
                }
            }

            interactor.configure(inputSel, sendSel, responseSel, manualInput, manualSend, manualResponse)
        }
    }

    fun setTab(tab: ScanModeTab) {
        _currentTab.value = tab
    }
    fun processBarcode(barcode: String) {
        val normalizedInput = com.example.janmanager.util.Normalizer.toHalfWidth(barcode).trim()
        val type = JanCodeUtil.detectCodeType(normalizedInput)
        
        // ITF Control
        if (!isItfEnabled.value && type == BarcodeType.ITF14) {
            return // Ignore ITF if disabled
        }

        // Convert ITF to JAN if necessary or keep it based on modes
        val normalizedBarcode = if (type == BarcodeType.ITF14) JanCodeUtil.itfToJan(normalizedInput) else normalizedInput

        if (scanSoundEnabled.value) {
            SoundHelper.playSuccessBeep()
        }

        when (_currentTab.value) {
            ScanModeTab.CONTINUOUS -> {
                viewModelScope.launch {
                    val product = productRepository.getProductByJan(normalizedBarcode)
                    val name = product?.productName ?: "未登録"
                    val list = _recentlyScanned.value.toMutableList()
                    list.add(0, RecentScan(normalizedBarcode, name))
                    _recentlyScanned.value = list.take(50) // Keep last 50
                }
            }
            ScanModeTab.CONFIRM -> {
                _lastConfirmBarcode.value = normalizedBarcode
                viewModelScope.launch {
                    _confirmProduct.value = productRepository.getProductByJan(normalizedBarcode)
                }
            }
            ScanModeTab.LINKAGE -> {
                when (_activeLinkageSlot.value) {
                    LinkageSlot.PIECE -> {
                        _linkagePieceJan.value = normalizedBarcode
                        _activeLinkageSlot.value = LinkageSlot.PACK
                    }
                    LinkageSlot.PACK -> {
                        _linkagePackJan.value = normalizedBarcode
                        _activeLinkageSlot.value = LinkageSlot.CASE
                    }
                    LinkageSlot.CASE -> {
                        _linkageCaseJan.value = normalizedBarcode
                    }
                }
            }
        }
    }
    
    fun setLinkageSlot(slot: LinkageSlot) {
        _activeLinkageSlot.value = slot
    }

    fun clearLinkageSlot(slot: LinkageSlot) {
        when (slot) {
            LinkageSlot.PIECE -> _linkagePieceJan.value = ""
            LinkageSlot.PACK -> _linkagePackJan.value = ""
            LinkageSlot.CASE -> _linkageCaseJan.value = ""
        }
    }

    fun setPackQty(qty: Int) { _linkagePackQty.value = qty }
    fun setCaseQty(qty: Int) { _linkageCaseQty.value = qty }

    fun executePackageLinkage() {
        viewModelScope.launch {
            val pieceJan = _linkagePieceJan.value
            if (pieceJan.isEmpty()) return@launch

            val product = productRepository.getProductByJan(pieceJan) ?: return@launch

            // Pack Unit
            if (_linkagePackJan.value.isNotEmpty()) {
                val packUnit = com.example.janmanager.data.local.entity.PackageUnit(
                    productId = product.id,
                    barcode = _linkagePackJan.value,
                    barcodeType = JanCodeUtil.detectCodeType(_linkagePackJan.value),
                    packageType = com.example.janmanager.data.local.entity.PackageType.PACK,
                    packageLabel = "パック",
                    quantityPerUnit = _linkagePackQty.value
                )
                productRepository.addPackageUnit(packUnit)
            }

            // Case Unit
            if (_linkageCaseJan.value.isNotEmpty()) {
                val caseUnit = com.example.janmanager.data.local.entity.PackageUnit(
                    productId = product.id,
                    barcode = _linkageCaseJan.value,
                    barcodeType = JanCodeUtil.detectCodeType(_linkageCaseJan.value),
                    packageType = com.example.janmanager.data.local.entity.PackageType.CASE,
                    packageLabel = "ケース",
                    quantityPerUnit = _linkageCaseQty.value
                )
                productRepository.addPackageUnit(caseUnit)
            }
            
            // clear
            _linkagePieceJan.value = ""
            _linkagePackJan.value = ""
            _linkageCaseJan.value = ""
            _activeLinkageSlot.value = LinkageSlot.PIECE
        }
    }

    // AI Fetch Functions
    fun setWebView(wv: WebView) {
        interactor.webView = wv
    }

    fun openAiFetchSheet() {
        _showAiSheet.value = true
        _aiFetchStatus.value = "待機中"
        _aiResultPreview.value = null
    }

    fun closeAiFetchSheet() {
        _showAiSheet.value = false
    }

    fun startSingleAiFetch() {
        val janCode = _lastConfirmBarcode.value
        if (janCode.isEmpty()) return
        viewModelScope.launch {
            _aiFetchStatus.value = "実行中..."
            val result = interactor.executeFullFlow(janCode, targetBaseUrl) { status ->
                _aiFetchStatus.value = status
            }
            if (result.success && result.data != null) {
                _aiResultPreview.value = result.data
                _aiFetchStatus.value = "取得成功"
            } else {
                _aiFetchStatus.value = "エラー: ${result.errorMessage ?: "取得失敗"}"
            }
        }
    }

    fun startBatchAiFetch() {
        viewModelScope.launch {
            val unfetched = productRepository.getUnfetchedProducts().first()
            if (unfetched.isEmpty()) {
                _aiFetchStatus.value = "未取得商品なし"
                return@launch
            }

            _aiFetchStatus.value = "一括開始: ${unfetched.size}件"
            unfetched.forEachIndexed { index, product ->
                _lastConfirmBarcode.value = product.janCode
                _aiFetchStatus.value = "一括取得 (${index + 1}/${unfetched.size}): ${product.janCode}"

                val result = interactor.executeFullFlow(product.janCode, targetBaseUrl) { status ->
                    _aiFetchStatus.value = "(${index + 1}/${unfetched.size}) $status"
                }

                if (result.success && result.data != null) {
                    _aiResultPreview.value = result.data
                    _aiFetchStatus.value = "(${index + 1}/${unfetched.size}) 取得成功: ${product.janCode}"
                    // ユーザーが結果を確認する時間を取る
                    // TODO: 将来的には承認待ちフローに変更することを推奨
                    delay(3000)
                } else {
                    _aiFetchStatus.value = "(${index + 1}/${unfetched.size}) 失敗: ${result.errorMessage ?: "不明"}"
                    delay(2000)
                }
            }
            _aiFetchStatus.value = "一括取得完了"
        }
    }

    fun acceptAiResult() {
        val result = _aiResultPreview.value ?: return
        viewModelScope.launch {
            val janCode = _lastConfirmBarcode.value
            val existing = productRepository.getProductByJan(janCode)
            
            val prefix = JanCodeUtil.extractMakerPrefix(janCode)
            
            val product = if (existing != null) {
                existing.copy(
                    makerName = result.maker_name,
                    makerNameKana = result.maker_name_kana,
                    productName = result.product_name,
                    productNameKana = result.product_name_kana,
                    spec = result.spec,
                    infoFetched = true,
                    infoSource = if (_aiUrl.value.orEmpty().contains("gemini")) InfoSource.AI_GEMINI else InfoSource.AI_PERPLEXITY,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                ProductMaster(
                    janCode = janCode,
                    makerJanPrefix = prefix,
                    makerName = result.maker_name,
                    makerNameKana = result.maker_name_kana,
                    productName = result.product_name,
                    productNameKana = result.product_name_kana,
                    spec = result.spec,
                    infoFetched = true,
                    infoSource = if (_aiUrl.value.orEmpty().contains("gemini")) InfoSource.AI_GEMINI else InfoSource.AI_PERPLEXITY
                )
            }
            
            if (existing != null) {
                productRepository.updateProduct(product)
            } else {
                productRepository.insertProduct(product)
            }
            
            _confirmProduct.value = product
            _showAiSheet.value = false
        }
    }

    // Photo Mode Functions
    fun setPhotoBitmap(bitmap: Bitmap?) {
        _photoBitmap.value = bitmap
        if (bitmap != null) {
            analyzeFullImage(bitmap)
        }
    }

    fun loadPhoto(uri: Uri) {
        _photoUri.value = uri
    }

    fun toggleTransformLock() {
        _isTransformLocked.value = !_isTransformLocked.value
    }

    fun resetTransform() {
        _currentScale.value = 1f
        _currentOffset.value = Offset.Zero
    }

    fun updateTransform(scale: Float, offset: Offset) {
        _currentScale.value = scale
        _currentOffset.value = offset
    }

    // OCR Functions

    /**
     * Performs a full scan of the bitmap to find all text elements.
     */
    fun analyzeFullImage(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _isOcrProcessing.value = true
            _detectedTextBlocks.value = emptyList()
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = mutableListOf<DetectedText>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                element.boundingBox?.let { rect ->
                                    blocks.add(DetectedText(element.text, rect))
                                }
                            }
                        }
                    }
                    _detectedTextBlocks.value = blocks
                    _isOcrProcessing.value = false
                }
                .addOnFailureListener {
                    _isOcrProcessing.value = false
                    _ocrError.value = true
                }
        }
    }

    /**
     * Processes specifically selected texts to find a JAN code.
     */
    fun processSelectedTexts(texts: List<String>) {
        if (texts.isEmpty()) return

        // 1. 生テキストをそのまま連結（"O"などの誤認識文字も保持）
        val rawFullText = texts.joinToString("")

        // 2. 連結テキストを直接tryFixに渡す
        var jan = com.example.janmanager.util.JanValidator.tryFix(rawFullText)

        if (jan == null) {
            val digits = Regex("\\d+").findAll(rawFullText).map { it.value }.toList()
            val combinedDigits = digits.joinToString("")

            // 3. 全数字を連結してtryFix
            jan = com.example.janmanager.util.JanValidator.tryFix(combinedDigits)

            // 4. ★NEW: 12桁の場合、先頭"0"を補完して13桁を試める（分割バーコード対策）
            //    例: "291459" + "301187" = "291459301187"(12桁) → "0291459301187"(13桁)
            if (jan == null && combinedDigits.length == 12) {
                jan = com.example.janmanager.util.JanValidator.tryFix("0$combinedDigits")
            }

            // 5. ★NEW: 隣接ブロック連結スキャン（最大5ブロック組み合わせ）
            //    OCRが "0", "291459", "301187" の3ブロックに分割した場合でも連結して検出できる
            if (jan == null) {
                outer@ for (i in digits.indices) {
                    var concat = ""
                    for (j in i until minOf(i + 5, digits.size)) {
                        concat += digits[j]
                        // 連結したものをそのままtryFix
                        val f = com.example.janmanager.util.JanValidator.tryFix(concat)
                        if (f != null) { jan = f; break@outer }
                        // 12桁なら先頭0補完も試みる
                        if (concat.length == 12) {
                            val f2 = com.example.janmanager.util.JanValidator.tryFix("0$concat")
                            if (f2 != null) { jan = f2; break@outer }
                        }
                        if (concat.length > 14) break
                    }
                }
            }

            // 6. Fallback: rawFullText から 7〜13桁パターンを探す（既存ロジック）
            if (jan == null) {
                val patterns = Regex("\\d{7,13}").findAll(rawFullText).map { it.value }.toList()
                for (p in patterns) {
                    val f = com.example.janmanager.util.JanValidator.tryFix(p)
                    if (f != null) { jan = f; break }
                }
            }

            // 7. Last fallback: 桁数優先で最長の数字列を返す（既存ロジック）
            if (jan == null) {
                jan = digits.firstOrNull { it.length == 13 }
                    ?: if (combinedDigits.length == 13) combinedDigits else null
                    ?: digits.firstOrNull { it.length == 8 }
                    ?: if (combinedDigits.length == 8) combinedDigits else null
                    ?: digits.maxByOrNull { it.length }
            }
        }

        if (!jan.isNullOrEmpty()) {
            onOcrJanFound(jan!!)
        } else {
            _ocrError.value = true
        }
    }

    /**
     * Processes live frames from the camera.
     * Uses strict validation to avoid noise in history.
     */
    fun processLiveFrame(texts: List<String>) {
        if (texts.isEmpty()) return
        
        for (text in texts) {
            val cleaned = text.filter { it.isDigit() || it.isLetter() }
            val fixed = com.example.janmanager.util.JanValidator.tryFix(cleaned)
            
            if (fixed != null && (fixed.length == 13 || fixed.length == 8)) {
                // To avoid rapid duplicates in live mode, check if we just scanned this
                val last = _recentlyScanned.value.firstOrNull()
                if (last?.jan != fixed) {
                    onOcrJanFound(fixed)
                }
            }
        }
    }

    fun processOcrBitmap(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _isOcrProcessing.value = true
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val allLines = visionText.textBlocks.flatMap { it.lines }
                    val detectedBlocks = mutableListOf<DetectedText>()
                    
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val rawText = line.text
                            val fixed = com.example.janmanager.util.JanValidator.tryFix(rawText)
                            
                            if (fixed != null) {
                                // This is a JAN candidate!
                                // Spatial Anchoring: Look for product names in the same block or nearby
                                val nearbyText = block.lines
                                    .filter { it != line }
                                    .joinToString(" ") { it.text }
                                    .take(100)
                                
                                // Anchor to DB if possible
                                viewModelScope.launch {
                                    val dbProduct = productRepository.getProductByJan(fixed)
                                    val anchoredName = dbProduct?.productName
                                    
                                    synchronized(detectedBlocks) {
                                        detectedBlocks.add(
                                            DetectedText(
                                                text = fixed,
                                                boundingBox = line.boundingBox ?: android.graphics.Rect(),
                                                anchoredProductName = anchoredName
                                            )
                                        )
                                        _detectedTextBlocks.value = detectedBlocks.toList()
                                    }
                                }
                            } else {
                                // Not a JAN, but keep for visual feedback
                                synchronized(detectedBlocks) {
                                    detectedBlocks.add(
                                        DetectedText(
                                            text = rawText,
                                            boundingBox = line.boundingBox ?: android.graphics.Rect()
                                        )
                                    )
                                    _detectedTextBlocks.value = detectedBlocks.toList()
                                }
                            }
                        }
                    }
                    _isOcrProcessing.value = false
                }
                .addOnFailureListener {
                    _isOcrProcessing.value = false
                    _ocrError.value = true
                }
        }
    }

    private fun onOcrJanFound(jan: String) {
        viewModelScope.launch {
            _currentOcrJan.value = jan
            _ocrError.value = false
            _currentBarcodeImage.value = BarcodeImageGenerator.generate(jan)
            
            val product = productRepository.getProductByJan(jan)
            val productName = product?.productName ?: ""
            _currentProductName.value = productName
            
            ocrScanHistoryRepository.insert(
                OcrScanHistory(
                    janCode = jan,
                    productName = productName,
                    scannedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun showBarcodeFromHistory(history: OcrScanHistory) {
        _currentOcrJan.value = history.janCode
        _currentBarcodeImage.value = BarcodeImageGenerator.generate(history.janCode)
        _currentProductName.value = history.productName
        _ocrError.value = false
    }

    fun deleteOcrHistory(id: Int) {
        viewModelScope.launch {
            ocrScanHistoryRepository.deleteById(id)
        }
    }

    fun clearAllOcrHistory() {
        viewModelScope.launch {
            ocrScanHistoryRepository.deleteAll()
        }
    }
}
