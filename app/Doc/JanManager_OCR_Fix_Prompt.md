# 実装依頼プロンプト — JAN OCR 指なぞり選択の改善

---

## ▼ このプロンプトの使い方

このファイルの内容をそのままコード実装AIへ貼り付けてください。
修正対象は2ファイルのみです。新規ファイルの作成は不要です。

---

## ■ 依頼内容の概要

Android Studio の Kotlin / Jetpack Compose プロジェクト「JanManager」において、
写真OCRでJANコードを指でなぞって取得する機能に2つの修正を行ってください。

### 問題1: 分割バーコードが取得できない
`0 291459 301187` のようにバーコード下の数字がスペースで区切られている場合、
ML Kit OCR は `"0"`, `"291459"`, `"301187"` の3ブロックに分割して認識します。
指でなぞった範囲に `"0"` が含まれないと 12桁にしかならず JAN として認識できません。
→ **`processSelectedTexts()` に先頭0補完ロジックと隣接ブロック連結スキャンを追加**する。

### 問題2: なぞった範囲の視覚フィードバックがない
指でなぞっている最中に「どの範囲を選択したか」がわかりにくい。
→ **なぞっている間、指の軌跡に合わせた矩形（短形）選択ハイライトをリアルタイム表示**する。
指を離した瞬間に確定し、ハイライトが消えてJAN処理が走るようにする。

---

## ■ 修正対象ファイル（2ファイルのみ）

```
app/src/main/java/com/example/janmanager/
├── ui/scan/
│   ├── ScanViewModel.kt       ← 修正1: processSelectedTexts() を改善
│   └── components/
│       └── PhotoOcrView.kt    ← 修正2: なぞり矩形ハイライトを追加 + inflate値変更
```

---

## ■ 修正1: `ScanViewModel.kt`

### 対象関数
`processSelectedTexts(texts: List<String>)` 関数（ファイル内に既存）

### 現在のコード（変更前）

```kotlin
fun processSelectedTexts(texts: List<String>) {
    if (texts.isEmpty()) return
    
    // 1. Join everything raw (preserving potential substitute chars like 'O')
    val rawFullText = texts.joinToString("")
    
    // 2. Try to fix the raw combined text directly
    var jan = com.example.janmanager.util.JanValidator.tryFix(rawFullText)
    
    if (jan == null) {
        // Extract all likely sequences (digits + common mistakes)
        val digits = Regex("\\d+").findAll(rawFullText).map { it.value }.toList()
        val combinedDigits = digits.joinToString("")
        
        // Try fixing the combined digits
        jan = com.example.janmanager.util.JanValidator.tryFix(combinedDigits)
        
        // 3. Fallback: If still not found, try finding 13 or 8 digit patterns in the raw text
        if (jan == null) {
            val patterns = Regex("\\d{7,13}").findAll(rawFullText).map { it.value }.toList()
            for (p in patterns) {
                val f = com.example.janmanager.util.JanValidator.tryFix(p)
                if (f != null) {
                    jan = f
                    break
                }
            }
        }
        
        // 4. Last fallback: max length sequence
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
```

### 変更後のコード（完全置換）

```kotlin
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

        // 4. ★NEW: 12桁の場合、先頭"0"を補完して13桁を試みる（分割バーコード対策）
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
```

### 変更のポイント（ScanViewModel）
- ステップ4（先頭0補完）とステップ5（隣接ブロック連結スキャン）を追加
- 既存のステップ3・6・7は内容を変更せず順番を調整のみ
- `outer@` ラベル付き break を使ってネストされたループから脱出
- インポートの追加は不要（既存の `JanValidator` をそのまま使用）

---

## ■ 修正2: `PhotoOcrView.kt`

### 変更点A: inflate値を 20f → 30f に変更

ファイル内に `inflate(20f * density)` が **2箇所** あります。
両方を `inflate(30f * density)` に変更してください。

```kotlin
// 変更前（2箇所とも）
val inflatedRect = screenRect.inflate(20f * density)

// 変更後（2箇所とも）
val inflatedRect = screenRect.inflate(30f * density)
```

### 変更点B: なぞり中の矩形ハイライト（短形選択UI）を追加

#### B-1: State変数を追加

`PhotoOcrView` コンポーザブル関数の `var containerSize` 宣言の直後（約50行目付近）に
以下の State 変数を追加してください。

```kotlin
// ★NEW: なぞり中の指軌跡から計算した選択矩形（ジェスチャー中のみ非null）
var dragSelectionRect by remember { mutableStateOf<Rect?>(null) }
// ★NEW: なぞり開始点（スクリーン座標）
var dragStartPos by remember { mutableStateOf<Offset?>(null) }
```

#### B-2: ジェスチャー処理の修正

`pointerInput` ブロック内のシングルフィンガー処理部分を以下の通り修正します。

**変更前（シングルフィンガー処理の `else if` ブロック全体）:**

```kotlin
} else if (pointers.size == 1 && (isLocked || !isTransforming)) {
    // Selection mode (Single finger)
    val pointer = pointers[0]
    val touchPos = pointer.position
    val params = imageDrawParams
    
    if (params != null) {
        // Check if this touch segment intersects any detected text block
        detectedTexts.forEachIndexed { index, detected ->
            if (!selectedIndices.contains(index)) {
                val screenRect = mapBitmapRectToScreen(
                    detected.boundingBox, params, scale, offset
                )
                // Lenient Hit-Test: Expand the rect by 20dp for a foolproof "magnetic" selection
                val inflatedRect = screenRect.inflate(20f * density)
                
                val isHit = if (lastTouchPos == null) {
                    inflatedRect.contains(touchPos)
                } else {
                    // Robust Path Sampling: Check 8 points along the segment to ensure no skips
                    var hit = false
                    for (i in 0..8) {
                        val t = i.toFloat() / 8f
                        val sample = lastTouchPos!! + (touchPos - lastTouchPos!!) * t
                        if (inflatedRect.contains(sample)) {
                            hit = true
                            break
                        }
                    }
                    hit
                }
                
                if (isHit) {
                    selectedIndices.add(index)
                }
            }
        }
    }
    lastTouchPos = touchPos
    pointer.consume()
}
```

**変更後（完全置換）:**

```kotlin
} else if (pointers.size == 1 && (isLocked || !isTransforming)) {
    // Selection mode (Single finger)
    val pointer = pointers[0]
    val touchPos = pointer.position
    val params = imageDrawParams

    // ★NEW: ドラッグ開始点を記録し、選択矩形をリアルタイム更新
    if (dragStartPos == null) {
        dragStartPos = touchPos
    }
    dragStartPos?.let { start ->
        dragSelectionRect = Rect(
            left   = minOf(start.x, touchPos.x),
            top    = minOf(start.y, touchPos.y),
            right  = maxOf(start.x, touchPos.x),
            bottom = maxOf(start.y, touchPos.y)
        )
    }

    if (params != null) {
        detectedTexts.forEachIndexed { index, detected ->
            if (!selectedIndices.contains(index)) {
                val screenRect = mapBitmapRectToScreen(
                    detected.boundingBox, params, scale, offset
                )
                // ★CHANGED: inflate を 20f → 30f に拡大（細い"0"ブロックも確実にヒット）
                val inflatedRect = screenRect.inflate(30f * density)

                val isHit = if (lastTouchPos == null) {
                    inflatedRect.contains(touchPos)
                } else {
                    var hit = false
                    for (i in 0..8) {
                        val t = i.toFloat() / 8f
                        val sample = lastTouchPos!! + (touchPos - lastTouchPos!!) * t
                        if (inflatedRect.contains(sample)) {
                            hit = true
                            break
                        }
                    }
                    hit
                }

                if (isHit) {
                    selectedIndices.add(index)
                }
            }
        }
    }
    lastTouchPos = touchPos
    pointer.consume()
}
```

#### B-3: ジェスチャー終了時のリセット

`awaitEachGesture` ブロックの末尾（`selectedIndices.clear()` の直後）に
以下のリセット処理を追加してください。

```kotlin
// 既存コード
selectedIndices.clear()

// ★NEW: ドラッグ選択矩形をリセット
dragSelectionRect = null
dragStartPos = null
```

#### B-4: 選択矩形の描画を追加

ハイライト描画用の Canvas（`// Highlights overlay` コメントがある Canvas コンポーザブル）の中、
`detectedTexts.forEachIndexed` ループの **後（下）** に以下を追加してください。

```kotlin
// ★NEW: なぞり中の選択矩形（短形）をリアルタイム描画
// scaleとoffsetを考慮した座標に変換して表示
dragSelectionRect?.let { selRect ->
    // このCanvasはgraphicsLayerでscale/offset適用済みのため、
    // スクリーン座標をgraphicsLayer逆変換して描画座標に変換する
    val drawLeft   = (selRect.left   - offset.x) / scale
    val drawTop    = (selRect.top    - offset.y) / scale
    val drawRight  = (selRect.right  - offset.x) / scale
    val drawBottom = (selRect.bottom - offset.y) / scale

    // 選択範囲の塗りつぶし（半透明の青）
    drawRect(
        color = Color(0x334FC3F7),
        topLeft = Offset(drawLeft, drawTop),
        size = Size(drawRight - drawLeft, drawBottom - drawTop)
    )
    // 選択範囲のボーダー（白の点線風の細い枠）
    drawRect(
        color = Color.White.copy(alpha = 0.85f),
        topLeft = Offset(drawLeft, drawTop),
        size = Size(drawRight - drawLeft, drawBottom - drawTop),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(8f, 4f), 0f
            )
        )
    )
}
```

### 変更のポイント（PhotoOcrView）
- `dragSelectionRect` と `dragStartPos` の2つのState変数で矩形管理
- なぞり中は `touchPos` と `dragStartPos` から min/max で矩形を計算しリアルタイム更新
- graphicsLayer の scale/offset が適用済みのCanvas内で描画するため逆変換が必要
- ジェスチャー終了時（指を離したとき）に両Stateをnullにリセット
- 新規インポートは不要（既存インポートで全て対応可能）

---

## ■ ビルド・動作確認

### ビルド手順
1. Android Studio で `Build > Make Project`（または `Ctrl+F9`）を実行
2. エラーが出た場合は以下を確認してください

### よくあるエラーと対処

| エラー内容 | 原因 | 対処 |
|---|---|---|
| `Unresolved reference: Rect` | `androidx.compose.ui.geometry.Rect` が未インポート | `PhotoOcrView.kt` の import を確認（既存コードに含まれているはず） |
| `Unresolved reference: Size` | 同上 | 同上 |
| `Unresolved reference: dashPathEffect` | `PathEffect` の完全修飾名が必要 | `androidx.compose.ui.graphics.PathEffect.dashPathEffect(...)` を使用（プロンプト内ではすでに完全修飾済み） |
| `Smart cast to 'String' is impossible` | `jan!!` の null 安全 | `jan` を `?: return` で早期 return に変更しても可 |
| `Label @outer not found` | `outer@` ラベルの書き方が間違っている | `outer@ for (i in digits.indices)` の @ の位置を確認 |
| `Variable 'dragSelectionRect' must be initialized` | State初期化忘れ | `by remember { mutableStateOf<Rect?>(null) }` を確認 |

### 動作確認チェックリスト

以下を順番に確認してください。

- [ ] ビルドエラーが0件であること
- [ ] 写真OCR画面で写真を開いた後、指でなぞると青い半透明の矩形が追従して表示される
- [ ] 指を離すと矩形が消えてJAN処理が走る
- [ ] `0 291459 301187` のようなスペース区切りのバーコードで、数字部分全体をなぞると `0291459301187` が取得される
- [ ] `291459 301187`（先頭の`0`を含まずなぞった場合）でも `0291459301187` が取得される（先頭0補完の確認）
- [ ] バーコード取得後、履歴に正しいJANコードが表示される
- [ ] ピンチ拡大・縮小が引き続き正常に動作する（選択矩形機能と競合しないこと）
- [ ] JAN候補ブロック（シアン色パルス表示）のタップ選択が引き続き動作する

---

## ■ 修正ファイルのパス一覧（再掲）

```
修正ファイル1:
app/src/main/java/com/example/janmanager/ui/scan/ScanViewModel.kt
→ processSelectedTexts() 関数を完全置換

修正ファイル2:
app/src/main/java/com/example/janmanager/ui/scan/components/PhotoOcrView.kt
→ A: inflate(20f * density) を inflate(30f * density) に2箇所変更
→ B: State変数2つ追加、ジェスチャー処理修正、描画Canvas修正

新規ファイル: なし
DB Migration: なし
build.gradle変更: なし
AndroidManifest変更: なし
```

