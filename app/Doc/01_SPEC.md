JAN商品管理アプリ 機能仕様書 v5.0 FINAL
1. アプリ概要
BluetoothバーコードリーダーでJANコードを読み取り、商品マスタをデータ上で構築・管理するAndroidアプリ。スキャンは用途別に3モード、商品情報取得はAIチャットのWebViewで別画面一括処理、蓄積データで売り場スキャン→バーコード付き一覧表示→発注機読み取り発注を実現する。

2. スキャン3モード
モード	用途	動作概要
連続取得モード	JANを大量に素早く読み取る	スキャン→即保存→次待ち（確認なし）
確認モード	1件ずつ情報確認+情報取得ボタン	スキャン→情報表示→確認/取得→保存
包装紐づけモード	単品/パック/ケースを紐づけ	複数JAN読み→スロット割当→紐づけ
2-1. 連続取得モード
BT入力常時フォーカス、Enter確定
ITF設定OFFなら14桁は警告音のみでスルー（保存しない、画面表示しない）
13桁/8桁：先頭7桁でMakerCache検索→メーカー名あればセットでDB保存、なければJANのみ保存
重複JANはトースト表示のみ（作業止めない）
画面上部にスキャン数カウンター常時表示
2-2. 確認モード
スキャン→DB検索→商品情報カード表示
既存情報取得済み：全情報＋包装紐づけ情報表示、「商品情報を再取得」ボタン
既存情報未取得：JAN＋メーカー名表示、「商品情報を取得」ボタン
新規：JAN＋メーカー名自動、「保存して商品情報を取得」/「保存のみ」ボタン
情報取得ボタン→WebViewボトムシートで1件取得→確認ダイアログ→DB更新
再取得時は変更差分をハイライト表示、必ず確認ボタンを押させる
2-3. 包装紐づけモード
3スロット表示（単品/パック/ケース）、タップでアクティブ切替
スキャン→アクティブスロットにJAN登録
2スロット以上埋まったら「紐づけ」ボタン有効化→入数入力→保存
2つのみの紐づけ（単品＋ケース等）も可
3. ITF読み取り制御
設定画面トグル、デフォルトOFF
OFF時：14桁受信→短い警告音→完全スルー
ON時：14桁をITFとして受付・保存
4. データ項目
ProductMaster（商品マスタ）
id, jan_code(UNIQUE), maker_jan_prefix, maker_name, maker_name_kana, product_name, product_name_kana, spec, status(ACTIVE/DISCONTINUED/RENEWED), renewed_from_jan, renewed_to_jan, info_source(AI_GEMINI/AI_PERPLEXITY/MANUAL/NONE), info_fetched(boolean), created_at, updated_at

PackageUnit（包装単位）
id, product_id(FK), barcode, barcode_type(EAN13/EAN8/ITF14), package_type(PIECE/PACK/CASE), package_label, quantity_per_unit

MakerCache（メーカーキャッシュ）
maker_jan_prefix(PK), maker_name, maker_name_kana

ScanSession（発注セッション）
id, session_name, created_at, status(OPEN/COMPLETED)

ScanItem（発注明細）
id, session_id(FK), product_id(FK), scanned_barcode, scan_order, scanned_at

ProductGroup（グループマスタ）
id, group_name, tag_color, start_date, end_date, is_active, memo, created_at

ProductGroupItem（グループ明細）
id, group_id(FK), product_id(FK), jan_code, added_at

5. AI商品情報取得（WebView方式）
別画面で一括処理、上下分割（未取得JAN一覧＋WebView）
Gemini(https://gemini.google.com/app?hl=ja) / Perplexity(https://www.perplexity.ai/) を設定で選択
設定画面でWebView経由ログイン→Cookie保存
自動方式：JS Injectionでプロンプト貼付→送信→生成検知→レスポンス抽出→次のJANへ自動進行
手動フォールバック：クリップボードにコピー→手動貼付→「取り込み」ボタンでDOM抽出
セレクタ自動検出＋手動編集可能
誤取り込み防御：JSON正規表現抽出、JAN一致検証、ひらがなバリデーション、プレビュー確認必須
統一プロンプト
Copy以下のJANコードの商品情報をJSON形式で返してください。
各項目のルール:
- maker_name, product_name, spec: 取得した情報そのまま使用
- maker_name_kana, product_name_kana: 全てひらがなで表記
- 半角スペース、全角スペースは除去
- 該当商品が見つからない場合は "not_found": true のみ返却

JANコード: {jan_code}

以下のJSON形式のみで返答してください。JSON以外の文章は不要です。
{"jan_code":"","maker_name":"","maker_name_kana":"","product_name":"","product_name_kana":"","spec":""}
spec＝規格サイズ（容量・重量・入数。例："350ml缶","500g","1.5L","6本パック"）

6. リニューアル品・終売品管理
専用画面は作らない、商品詳細画面内に統合
3パターン：新品側から旧品検索、旧品側から新品指定、確認モードでスキャン紐づけ
相互にrenewed_from_jan/renewed_to_janを設定
終売：ステータス変更→赤バッジ、発注スキャン時警告
7. グループ商品管理
月間ご奉仕品・ポイント付き商品等をタグ＋終了日で管理
グループ作成→売り場で連続スキャン追加→グループ商品一覧
タグ色選択、開始日（任意）＋終了日（必須）
終了日過ぎたら自動is_active=false
他画面でグループタグバッジ表示
8. 発注支援
発注スキャン：セッション管理＋連続スキャン、同一JAN重複はトースト通知
発注一覧：バーコード画像＋商品情報、一覧モード/フォーカスモード
バーコードタップで非表示（トグル動作）、進捗カウンター
フォーカスモード：1商品ずつ大表示、スワイプ移動、画面輝度自動最大化
9. 商品マスタ一覧・検索
JANコード検索（完全一致＋前方一致）
商品名ひらがな検索（部分一致）
メーカー別検索（部分一致）
規格別検索（部分一致）
横断文字検索（OR部分一致）
フィルタ：ステータス、情報取得状態、メーカー別
10. 正規化ルール
maker_name, product_name, spec：AI取得そのまま、前後空白除去のみ
maker_name_kana, product_name_kana：ひらがなのみ
jan_code：半角数字のみ
全カラム：前後空白除去、連続スペース除去
11. 画面一覧（確定版）
#	画面名	ファイル名
1	ホーム	HomeScreen.kt
2	スキャン（3モード）	ScanScreen.kt
3	商品マスタ一覧	ProductListScreen.kt
4	商品詳細	ProductDetailScreen.kt
5	AI商品情報取得	AiFetchScreen.kt
6	発注スキャン	OrderScanScreen.kt
7	発注一覧	OrderListScreen.kt
8	グループ一覧	GroupListScreen.kt
9	グループスキャン追加	GroupScanScreen.kt
10	グループ商品一覧	GroupDetailScreen.kt
11	設定	SettingsScreen.kt