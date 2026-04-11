# JanManager OCR バーコードスキャン機能 実装仕様書

> **対象リポジトリ:** https://github.com/DENDENKIO/JanManager  
> **対象ブランチ:** main  
> **パッケージ名:** com.example.janmanager  
> **ドキュメントバージョン:** 1.0 / 2026-04-10  

---

## 1. 概要・目的

JanManager アプリの「連続スキャン（CONTINUOUS）」タブを全面リニューアルする。  
写真から JAN コードを OCR で読み取り、バーコードを即時生成・表示・履歴管理する機能を追加する。  
他の2タブ（CONFIRM / LINKAGE）は **一切変更しない**。

### 1.1 既存構成の前提知識

| 項目 | 内容 |
|---|---|
| 言語 | Kotlin（Jetpack Compose 100%） |
| DI | Hilt（@HiltViewModel / @Inject） |
| DB | Room（version 1 / AppDatabase.kt） |
| パッケージ名 | com.example.janmanager |
| minSdk | 26 |
| compileSdk | 35 |
| バーコード | zxing.core（既存依存） |
| ナビゲーション | Navigation Compose |

---

## 2. 機能仕様

### 2.1 動作フロー

```
① [写真を撮る] または [ギャラリーから選ぶ] をタップ
② 写真が画面の約 65% を占める大画面で表示される
③ 写真はピンチで拡大・縮小、ドラッグでパン移動できる
④ 指でドラッグして数字部分を矩形選択する（青い点線枠が表示される）
⑤ 指を離した瞬間に自動で以下が実行される:
   a. 選択範囲の Bitmap を切り出す（スケール・オフセットを逆算）
   b. ML Kit OCR に渡す
   c. 数字列を抽出（13桁を最優先 → 8桁 → それ以外は最長一致）
   d. JAN コードが取得できたら即バーコード生成（ZXing）
   e. Room DB から商品名を検索
   f. OCR 履歴テーブルに保存
⑥ 画面下部にバーコード画像・JAN コード・商品名（または「未登録」）が表示される
⑦ 写真はそのまま表示され、すぐ次の範囲を囲める（連続取得可能）
⑧ 履歴リストで過去の取得 JAN を一覧確認できる
⑨ 履歴行をタップするとバーコードを再生成・再表示する
⑩ 履歴行を長押しすると削除確認ダイアログが表示される
```

### 2.2 写真エリア仕様

| 項目 | 仕様 |
|---|---|
| 縦サイズ | バーコード未表示時: weight 0.65f / 表示後: weight 0.4f（AnimatedFloat で遷移） |
| 背景色 | 黒（Color.Black） |
| 拡大縮小 | ピンチジェスチャー（rememberTransformableState） |
| パン移動 | ドラッグ（transformable モディファイア） |
| 矩形選択 | 指ドラッグで青い点線矩形オーバーレイを描画 |
| 選択リセット | 次のドラッグ開始時に自動リセット |

### 2.3 OCR 仕様

| 項目 | 仕様 |
|---|---|
| ライブラリ | com.google.mlkit:text-recognition:16.0.1 |
| 入力 | 矩形選択範囲を切り出した Bitmap |
| 抽出ロジック | 正規表現 \d+ で全数字列を抽出 → 13桁優先 → 8桁 → 最長一致 |
| OCR 失敗時 | バーコードエリアに「読み取れませんでした」を表示 |
| 処理スレッド | viewModelScope.launch(Dispatchers.IO) |

### 2.4 バーコード生成仕様

| 項目 | 仕様 |
|---|---|
| ライブラリ | com.google.zxing:core（既存依存を使用） |
| フォーマット | EAN_13（13桁）/ CODE_128（それ以外） |
| サイズ | 幅 800px / 高さ 200px（Bitmap） |
| 表示 | Image(bitmap.asImageBitmap()) で横幅いっぱいに表示 |

### 2.5 商品情報表示仕様

| 状態 | 表示 |
|---|---|
| DB に商品あり | ProductMaster.productName を表示 |
| DB に商品なし | 「未登録」をグレーテキストで表示 |
| OCR 失敗 | 商品情報エリア非表示 |

### 2.6 OCR 履歴仕様

| 項目 | 仕様 |
|---|---|
| 保存タイミング | JAN 取得成功のたびに保存 |
| 表示順 | 新しい順（ORDER BY scannedAt DESC） |
| 表示内容 | JAN コード / 商品名または「未登録」/ 日時（MM/dd HH:mm） |
| タップ | バーコード再生成・再表示 |
| 長押し | 削除確認ダイアログ（「削除」「キャンセル」） |

---

## 3. ファイル一覧と変更内容

### 3.1 変更・作成ファイル全体マップ

| # | ファイルパス（app/src/main 以下） | 種別 | 内容 |
|---|---|---|---|
| 1 | app/build.gradle.kts | **変更** | ML Kit 依存追加 |
| 2 | AndroidManifest.xml | **変更** | 権限・FileProvider 追加 |
| 3 | res/xml/file_provider_paths.xml | **新規** | FileProvider パス定義 |
| 4 | data/local/entity/OcrScanHistory.kt | **新規** | Room Entity |
| 5 | data/local/dao/OcrScanHistoryDao.kt | **新規** | Room DAO |
| 6 | data/local/AppDatabase.kt | **変更** | Entity 追加・version 2・Migration |
| 7 | data/repository/OcrScanHistoryRepository.kt | **新規** | Repository（Hilt @Singleton） |
| 8 | di/AppModule.kt（または DatabaseModule.kt） | **変更** | DAO・Repository の @Provides 追加 |
| 9 | util/BarcodeImageGenerator.kt | **新規** | ZXing バーコード Bitmap 生成 |
| 10 | ui/scan/components/PhotoOcrView.kt | **新規** | 写真大画面・矩形選択・OCR |
| 11 | ui/scan/components/ContinuousMode.kt | **変更** | 全面書き換え |
| 12 | ui/scan/ScanViewModel.kt | **変更** | State・Function 追加のみ |

### 3.2 各ファイルの詳細仕様

---

#### ファイル 1: app/build.gradle.kts（変更）

dependencies ブロックに追加:

```kotlin
implementation("com.google.mlkit:text-recognition:16.0.1")
```

- zxing.core は既存のため追加不要
- text-recognition-japanese は不要（数字のみ Latin で十分）

---

#### ファイル 2: AndroidManifest.xml（変更）

manifest タグ内（uses-permission）に追加:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

application タグ内に追加:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

---

#### ファイル 3: res/xml/file_provider_paths.xml（新規）

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="camera_photos" path="camera/" />
</paths>
```

---

#### ファイル 4: data/local/entity/OcrScanHistory.kt（新規）

```kotlin
package com.example.janmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_scan_history")
data class OcrScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val janCode: String,
    val productName: String,  // DB に商品なければ空文字 ""
    val scannedAt: Long       // System.currentTimeMillis()
)
```

---

#### ファイル 5: data/local/dao/OcrScanHistoryDao.kt（新規）

```kotlin
package com.example.janmanager.data.local.dao

import androidx.room.*
import com.example.janmanager.data.local.entity.OcrScanHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: OcrScanHistory)

    @Query("SELECT * FROM ocr_scan_history ORDER BY scannedAt DESC")
    fun getAll(): Flow<List<OcrScanHistory>>

    @Query("DELETE FROM ocr_scan_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM ocr_scan_history")
    suspend fun deleteAll()
}
```

---

#### ファイル 6: data/local/AppDatabase.kt（変更）

変更箇所:

1. version を 1 → 2 に変更
2. entities に `OcrScanHistory::class` を追加
3. `abstract fun ocrScanHistoryDao(): OcrScanHistoryDao` を追加
4. クラス外またはコンパニオンオブジェクトに Migration を定義:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS ocr_scan_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                janCode TEXT NOT NULL,
                productName TEXT NOT NULL,
                scannedAt INTEGER NOT NULL
            )"""
        )
    }
}
```

**注意:** Room.databaseBuilder に `.addMigrations(MIGRATION_1_2)` を渡している箇所（DI モジュール）も変更すること。

---

#### ファイル 7: data/repository/OcrScanHistoryRepository.kt（新規）

```kotlin
package com.example.janmanager.data.repository

import com.example.janmanager.data.local.dao.OcrScanHistoryDao
import com.example.janmanager.data.local.entity.OcrScanHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrScanHistoryRepository @Inject constructor(
    private val dao: OcrScanHistoryDao
) {
    fun getAll(): Flow<List<OcrScanHistory>> = dao.getAll()
    suspend fun insert(history: OcrScanHistory) = dao.insert(history)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
}
```

---

#### ファイル 8: DI モジュール（変更）

AppModule.kt または DatabaseModule.kt を確認し、以下を追加:

```kotlin
@Provides
@Singleton
fun provideOcrScanHistoryDao(db: AppDatabase): OcrScanHistoryDao = db.ocrScanHistoryDao()
```

また Room.databaseBuilder のチェーンに `.addMigrations(MIGRATION_1_2)` を追加する。

---

#### ファイル 9: util/BarcodeImageGenerator.kt（新規）

```kotlin
package com.example.janmanager.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

object BarcodeImageGenerator {
    fun generate(janCode: String, width: Int = 800, height: Int = 200): Bitmap? {
        return try {
            val cleaned = janCode.filter { it.isDigit() || !janCode.all { c -> c.isDigit() } }
            val format = if (janCode.length == 13 && janCode.all { it.isDigit() })
                BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val bitMatrix = MultiFormatWriter().encode(janCode, format, width, height, hints)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
```

---

#### ファイル 10: ui/scan/components/PhotoOcrView.kt（新規）

責務と実装方針:

```
- 写真（ImageBitmap）を Canvas 上に表示
- rememberTransformableState でピンチ拡大・縮小・パン移動を実装
  scale（初期値 1f）/ offset（初期値 Offset.Zero）を State で保持
- pointerInput(Unit) + detectDragGestures で矩形選択を実装
  onDragStart: selectionStart を記録
  onDrag: selectionEnd を更新しながら矩形を描画（青い点線）
  onDragEnd: 以下を実行
    1. 座標逆算: imageX = (screenX - offset.x) / scale
    2. Bitmap.createBitmap() で範囲を切り出し
    3. 切り出し範囲が画像外に出ないよう coerceIn でクランプ
    4. onRegionSelected(croppedBitmap) コールバックを呼ぶ
    5. selectionStart / selectionEnd をリセット
- transformable と detectDragGestures は別 Modifier に分けて定義する
  （競合を防ぐため pointerInput を先に、transformable を後に書く）
```

関数シグネチャ:

```kotlin
@Composable
fun PhotoOcrView(
    imageBitmap: ImageBitmap,
    onRegionSelected: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
)
```

---

#### ファイル 11: ui/scan/components/ContinuousMode.kt（全面書き換え）

画面構成（上から順）:

```
[A] ボタン行（Row）
    - [📷 写真を撮る] ボタン
      → CAMERA 権限リクエスト（rememberLauncherForActivityResult(RequestPermission)）
      → 権限付与後に FileProvider Uri を生成して TakePicture を起動
      → 撮影成功時: Uri → Bitmap 変換（BitmapFactory + inSampleSize でリサイズ）
    - [🖼️ ギャラリーから選ぶ] ボタン
      → PickVisualMedia を起動
      → Uri → Bitmap 変換

[B] 写真エリア（Box、黒背景）
    - 写真未選択時: 「写真を選んでください」テキスト（中央）
    - 写真選択後: PhotoOcrView を表示
    - weight: animateFloatAsState で制御
      barcodeImage != null かつ ocrError == false → 0.4f
      それ以外 → 0.65f

[C] バーコード表示エリア（AnimatedVisibility）
    表示条件: currentBarcodeImage != null かつ ocrError == false
    - Image(currentBarcodeImage.asImageBitmap())（横幅いっぱい）
    - JAN コード数字テキスト（中央寄せ、等幅フォント）
    - 商品名テキスト（currentProductName が "" なら「未登録」をグレー表示）

    ocrError == true のとき:
    - 「読み取れませんでした」テキストを表示

[D] OCR 取得履歴（LazyColumn）
    各行の表示:
    - JAN コード（太字）
    - 商品名 または「未登録」（グレー）
    - 日時（MM/dd HH:mm）
    タップ: viewModel.showBarcodeFromHistory(history)
    長押し: deletingItem を State で保持 → ダイアログ表示
    ダイアログ: AlertDialog（「削除しますか？」「削除」「キャンセル」）
               「削除」で viewModel.deleteOcrHistory(id)
```

写真撮影の実装例:

```kotlin
val photoUriState = remember { mutableStateOf<Uri?>(null) }
val context = LocalContext.current

val takePictureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
) { success ->
    if (success) {
        photoUriState.value?.let { uri ->
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                selectedBitmap.value = BitmapFactory.decodeStream(stream, null, options)
            }
        }
    }
}

fun createPhotoUri(ctx: Context): Uri {
    val dir = File(ctx.cacheDir, "camera").also { it.mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}
```

---

#### ファイル 12: ui/scan/ScanViewModel.kt（変更・追加のみ）

**既存コードは一行も削除しない。以下を追加する。**

コンストラクタに追加:
```kotlin
private val ocrScanHistoryRepository: OcrScanHistoryRepository
```

追加する State:
```kotlin
private val _currentOcrJan = MutableStateFlow("")
val currentOcrJan = _currentOcrJan.asStateFlow()

private val _currentBarcodeImage = MutableStateFlow<Bitmap?>(null)
val currentBarcodeImage = _currentBarcodeImage.asStateFlow()

// null = 未取得, "" = 未登録, それ以外 = 商品名
private val _currentProductName = MutableStateFlow<String?>(null)
val currentProductName = _currentProductName.asStateFlow()

private val _ocrError = MutableStateFlow(false)
val ocrError = _ocrError.asStateFlow()

val ocrScanHistory: StateFlow<List<OcrScanHistory>> = ocrScanHistoryRepository
    .getAll()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

追加する Function:
```kotlin
fun processOcrBitmap(bitmap: Bitmap) {
    viewModelScope.launch(Dispatchers.IO) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val digits = Regex("\d+").findAll(visionText.text)
                    .map { it.value }.toList()
                val jan = digits.firstOrNull { it.length == 13 }
                    ?: digits.firstOrNull { it.length == 8 }
                    ?: digits.maxByOrNull { it.length }
                if (jan != null) onOcrJanFound(jan)
                else { _ocrError.value = true }
            }
            .addOnFailureListener { _ocrError.value = true }
    }
}

private fun onOcrJanFound(jan: String) {
    viewModelScope.launch {
        _currentOcrJan.value = jan
        _ocrError.value = false
        _currentBarcodeImage.value = BarcodeImageGenerator.generate(jan)
        val product = productRepository.getProductByJan(jan)
        _currentProductName.value = product?.productName ?: ""
        ocrScanHistoryRepository.insert(
            OcrScanHistory(
                janCode = jan,
                productName = product?.productName ?: "",
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
    viewModelScope.launch { ocrScanHistoryRepository.deleteById(id) }
}
```

---

## 4. よくあるエラーと対処法

| エラー | 原因 | 対処 |
|---|---|---|
| Room schema has changed | Migration 未定義または version 変更漏れ | AppDatabase version を 2 にして MIGRATION_1_2 を追加し、DI モジュールで .addMigrations(MIGRATION_1_2) を呼ぶ |
| Cannot find symbol OcrScanHistoryDao | DI モジュールへの登録漏れ | AppModule 等に provideOcrScanHistoryDao() を追加する |
| FileProvider not found | Manifest への FileProvider 定義漏れ | AndroidManifest.xml の application タグ内に provider タグを追加する |
| EAN_13 contents should only contain digits | 13桁以外または非数字文字 | generate() の冒頭で length==13 かつ all isDigit() かどうかチェックしてから EAN_13 を使う |
| OutOfMemoryError | 大きい写真をそのまま Bitmap 化 | BitmapFactory.Options の inSampleSize を 2 以上に設定してリサイズ読み込みする |
| SecurityException（カメラ） | 実行時権限リクエスト未実装 | rememberLauncherForActivityResult(RequestPermission) でカメラ権限リクエストを Composable 内に実装する |
| ドラッグと transformable が競合 | ジェスチャーの優先度衝突 | pointerInput（dragGestures）を Modifier の前に、transformable を後に配置する |
| Unresolved reference: InputImage | ML Kit インポート漏れ | import com.google.mlkit.vision.common.InputImage を追加 |
| Unresolved reference: TextRecognition | ML Kit インポート漏れ | import com.google.mlkit.vision.text.TextRecognition を追加 |

---

## 5. ロードマップ

### Phase 1: 基盤整備（ビルドが通ること最優先）

```
Step 1: app/build.gradle.kts に ML Kit 依存を追加 → Gradle sync
Step 2: OcrScanHistory Entity + OcrScanHistoryDao を作成
Step 3: AppDatabase.kt を変更（version 2 / Entity 追加 / Migration 追加 / DAO 追加）
Step 4: DI モジュールを変更（OcrScanHistoryDao の Provide / addMigrations 追加）
Step 5: OcrScanHistoryRepository を作成して Hilt 登録
Step 6: ビルド実行 → エラー確認・修正
```

### Phase 2: ユーティリティ実装

```
Step 7: BarcodeImageGenerator.kt を作成
Step 8: AndroidManifest.xml を変更（権限 / FileProvider）
Step 9: file_provider_paths.xml を作成
Step 10: ビルド実行 → エラー確認・修正
```

### Phase 3: ViewModel 拡張

```
Step 11: ScanViewModel.kt に State を追加
Step 12: ScanViewModel.kt に Function を追加（ML Kit OCR 処理含む）
Step 13: ビルド実行 → エラー確認・修正
```

### Phase 4: UI 実装

```
Step 14: PhotoOcrView.kt を作成（ピンチ拡大・矩形選択・Bitmap 切り出し）
Step 15: ContinuousMode.kt を全面書き換え
Step 16: ビルド実行 → エラー確認・修正
Step 17: 動作確認（下記チェックリスト）
```

### 動作確認チェックリスト

```
✅ 写真撮影 → 矩形選択 → 指を離す → 即バーコード表示
✅ ギャラリー選択 → 矩形選択 → 即バーコード表示
✅ 同じ写真で連続して複数回選択できる
✅ 商品が DB にある場合は商品名が表示される
✅ 商品が DB にない場合は「未登録」が表示される
✅ OCR 失敗時は「読み取れませんでした」が表示される
✅ 履歴リストに新しい順で保存される
✅ 履歴タップでバーコードが再表示される
✅ 履歴長押しで削除確認ダイアログが出る
✅ 他タブ（CONFIRM / LINKAGE）が正常動作している
```

---

## 6. 実装 AI 向けプロンプト

以下をコード実装専用 AI にそのままコピーして使用すること。

---

```
あなたは Android Kotlin（Jetpack Compose）の実装専門 AI です。
以下の仕様に従って JanManager アプリの「連続スキャン」タブに
OCR バーコード生成機能を実装してください。

## リポジトリ
- GitHub: https://github.com/DENDENKIO/JanManager
- ブランチ: main
- パッケージ名: com.example.janmanager
- 言語: Kotlin / Jetpack Compose / Hilt / Room

## 実装ルール
1. 実装前に必ず対象ファイルの既存コードを確認すること
2. CONFIRM タブ・LINKAGE タブのコードは一切変更しないこと
3. ScanViewModel の既存 State・Function は削除せず追加のみ行うこと
4. 各 Phase 完了後に必ずビルドを実行しエラーがないことを確認すること
5. エラーが出た場合は原因を特定・修正してから次の Step に進むこと

## Phase 1: 基盤整備
Step 1: app/build.gradle.kts に追加
        implementation("com.google.mlkit:text-recognition:16.0.1")
        → Gradle sync を実行してエラーがないことを確認

Step 2: 以下を新規作成
        - data/local/entity/OcrScanHistory.kt
          （id/janCode/productName/scannedAt の Room Entity）
        - data/local/dao/OcrScanHistoryDao.kt
          （insert/getAll/deleteById/deleteAll の DAO）

Step 3: data/local/AppDatabase.kt を変更
        - version 1 → 2
        - entities に OcrScanHistory::class を追加
        - MIGRATION_1_2 を定義（ocr_scan_history テーブル作成の SQL）
        - abstract fun ocrScanHistoryDao(): OcrScanHistoryDao を追加

Step 4: Hilt DI モジュール（AppModule.kt または DatabaseModule.kt）を変更
        - OcrScanHistoryDao の @Provides を追加
        - Room.databaseBuilder のチェーンに .addMigrations(MIGRATION_1_2) を追加

Step 5: data/repository/OcrScanHistoryRepository.kt を新規作成
        - @Singleton / @Inject constructor で Hilt に登録
        - getAll() / insert() / deleteById() を実装

Step 6: ビルド実行 → エラーがあれば修正してから次へ

## Phase 2: ユーティリティ
Step 7: util/BarcodeImageGenerator.kt を新規作成
        - ZXing MultiFormatWriter を使用（既存依存）
        - 13桁かつ全数字なら EAN_13、それ以外は CODE_128
        - 幅 800px / 高さ 200px の Bitmap を返す
        - 例外は try-catch して null を返す

Step 8: AndroidManifest.xml を変更
        - CAMERA / READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE(maxSdkVersion=32) 権限追加
        - FileProvider を application タグ内に追加
          authorities: "${applicationId}.fileprovider"
          resource: @xml/file_provider_paths

Step 9: res/xml/file_provider_paths.xml を新規作成
        <cache-path name="camera_photos" path="camera/" />

Step 10: ビルド実行 → エラーがあれば修正してから次へ

## Phase 3: ViewModel 拡張
Step 11: ui/scan/ScanViewModel.kt に以下の State を追加（既存は削除しない）
         - OcrScanHistoryRepository を @Inject constructor に追加
         - _currentOcrJan: MutableStateFlow<String>("")
         - _currentBarcodeImage: MutableStateFlow<Bitmap?>(null)
         - _currentProductName: MutableStateFlow<String?>(null)
         - _ocrError: MutableStateFlow<Boolean>(false)
         - ocrScanHistory: StateFlow<List<OcrScanHistory>>（Repository の Flow を stateIn）

Step 12: ScanViewModel.kt に以下の Function を追加
         - processOcrBitmap(bitmap: Bitmap)
           ML Kit TextRecognition で OCR 実行
           正規表現 \d+ で数字列を全抽出
           13桁優先 → 8桁 → 最長一致で JAN を決定
           成功: onOcrJanFound(jan) を呼ぶ
           失敗: _ocrError.value = true

         - onOcrJanFound(jan: String) [private]
           _currentOcrJan, _ocrError を更新
           BarcodeImageGenerator.generate(jan) で Bitmap 生成
           productRepository.getProductByJan(jan) で商品名取得
           OcrScanHistory を保存

         - showBarcodeFromHistory(history: OcrScanHistory)
           履歴からバーコードを再生成して State を更新

         - deleteOcrHistory(id: Int)
           ocrScanHistoryRepository.deleteById(id) を呼ぶ

Step 13: ビルド実行 → エラーがあれば修正してから次へ

## Phase 4: UI 実装
Step 14: ui/scan/components/PhotoOcrView.kt を新規作成
         シグネチャ:
           @Composable
           fun PhotoOcrView(
               imageBitmap: ImageBitmap,
               onRegionSelected: (Bitmap) -> Unit,
               modifier: Modifier = Modifier
           )
         実装:
         - rememberTransformableState で scale / offset を管理
         - Modifier.transformable で ピンチ拡大・縮小・パン移動
         - Modifier.pointerInput + detectDragGestures で矩形選択
           onDragStart: selectionStart を記録
           onDrag: selectionEnd を更新・矩形描画（青い点線）
           onDragEnd:
             座標逆算（imageX = (screenX - offset.x) / scale）
             Bitmap.createBitmap() で範囲切り出し（coerceIn でクランプ）
             onRegionSelected(croppedBitmap) を呼ぶ
             矩形をリセット
         - 注意: pointerInput を先に、transformable を後に定義して競合を避ける

Step 15: ui/scan/components/ContinuousMode.kt を全面書き換え
         構成:
         [A] ボタン行
             - [📷 写真を撮る]: CAMERA 権限リクエスト → FileProvider Uri 生成 → TakePicture
             - [🖼️ ギャラリーから選ぶ]: PickVisualMedia（ImageOnly）
             いずれも Uri → Bitmap 変換（inSampleSize=2 でリサイズ）

         [B] 写真エリア（黒背景 Box）
             - 写真未選択: 「写真を選んでください」テキスト
             - 写真選択後: PhotoOcrView
             - weight: animateFloatAsState
               barcodeImage != null かつ ocrError==false → 0.4f
               それ以外 → 0.65f
             - PhotoOcrView の onRegionSelected で viewModel.processOcrBitmap() を呼ぶ

         [C] バーコード表示エリア（AnimatedVisibility）
             表示条件: currentBarcodeImage != null かつ ocrError == false
             - Image(bitmap.asImageBitmap()) 横幅いっぱい
             - JAN コードテキスト（中央・等幅フォント）
             - 商品名 or「未登録」（グレー）
             ocrError == true: 「読み取れませんでした」テキスト

         [D] 履歴 LazyColumn
             - 各行: JAN / 商品名または「未登録」/ 日時（MM/dd HH:mm）
             - タップ: viewModel.showBarcodeFromHistory(history)
             - 長押し: AlertDialog で削除確認
               「削除」: viewModel.deleteOcrHistory(id)

Step 16: ビルド実行 → エラーがあれば修正

Step 17: 以下の動作をすべて確認
         ✅ 写真撮影 → 矩形選択 → 指を離す → 即バーコード表示
         ✅ ギャラリー選択 → 矩形選択 → 即バーコード表示
         ✅ 同じ写真で連続して複数回選択できる
         ✅ 商品が DB にある場合は商品名が表示される
         ✅ 商品が DB にない場合は「未登録」が表示される
         ✅ OCR 失敗時は「読み取れませんでした」が表示される
         ✅ 履歴リストに新しい順で保存される
         ✅ 履歴タップでバーコードが再表示される
         ✅ 履歴長押しで削除確認ダイアログが出る
         ✅ 他タブ（CONFIRM / LINKAGE）が正常動作している

## 注意事項
- Room version 変更時は必ず Migration を定義すること（しないと起動時クラッシュ）
- BarcodeImageGenerator は 13桁かつ全数字のみ EAN_13 を使うこと
- PhotoOcrView の座標変換は scale と offset を必ず逆算すること
- 写真読み込みは OOM 防止のため BitmapFactory.Options で inSampleSize=2 以上を使うこと
- カメラ権限は Composable 内で実行時リクエストを実装すること
```

---

*本ドキュメントは JanManager リポジトリの OCR バーコード機能実装のための完全仕様書です。*
