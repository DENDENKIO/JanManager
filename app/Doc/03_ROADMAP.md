JAN商品管理アプリ 開発ロードマップ v5.0 FINAL
AIへの基本指示（全Phase共通で冒頭に付与）
あなたはAndroidアプリ開発の専門家です。
以下のファイルを必ず読み込んでから作業を開始してください。

【必読ファイル（読み込み順序厳守）】
1. @docs/01_SPEC.md     ← 機能仕様書（何を作るか）
2. @docs/02_TECH.md     ← 技術実装仕様書（どう作るか）
3. @docs/03_ROADMAP.md  ← このロードマップ

【絶対ルール】
- 02_TECH.mdのlibs.versions.tomlに記載されたバージョンを厳守。変更禁止。
- 02_TECH.mdのパッケージ構成のファイル名を厳守。変更禁止。
- org.jetbrains.kotlin.androidプラグインは使用禁止（AGP 9.0 Built-in Kotlin）
- composeOptionsブロックは記述禁止
- kotlinOptionsブロックは記述禁止
- kaptは使用禁止（すべてKSPで処理）
- Navigation Composeのルートは02_TECH.mdのRoutes.ktの型安全定義を使用
- Roomのエンティティ名・カラム名は01_SPEC.mdのデータ項目を厳守
Phase 1：プロジェクト初期設定
読み込みファイル： 02_TECH.md（セクション3,4,5,8）

AIへの指示プロンプト：

@docs/02_TECH.mdを読み込んでください。

Phase 1の作業を開始します。
02_TECH.mdのセクション3「libs.versions.toml」、セクション4「ルートbuild.gradle.kts」、
セクション5「app/build.gradle.kts」、セクション8「AndroidManifest.xml」の内容を
そのまま使用して、Android Studioプロジェクトの初期ファイルを生成してください。

生成するファイル：
- gradle/libs.versions.toml
- build.gradle.kts（ルート）
- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/example/janmanager/JanManagerApp.kt（@HiltAndroidApp）
- app/src/main/java/com/example/janmanager/MainActivity.kt（@AndroidEntryPoint、空のsetContent）
- settings.gradle.kts
- gradle.properties（必要な設定のみ）

02_TECH.mdのセクション9「ビルド前チェックリスト」の全項目を満たすこと。
kotlin-androidプラグインは使用禁止です。AGP 9.0のBuilt-in Kotlinを使用します。
完了条件： ./gradlew buildが成功すること

Phase 2：データ層（Entity・DAO・Database・Converter）
読み込みファイル： 01_SPEC.md（セクション4）、02_TECH.md（セクション6）

AIへの指示プロンプト：

@docs/01_SPEC.mdのセクション4「データ項目」と
@docs/02_TECH.mdのセクション6「パッケージ構成」を読み込んでください。

Phase 2の作業を開始します。
01_SPEC.mdのデータ項目定義に従い、以下のファイルを生成してください。

生成するファイル（ファイルパスはすべて02_TECH.mdセクション6に従う）：
- data/local/entity/Enums.kt（ProductStatus, PackageType, BarcodeType, InfoSource, SessionStatus）
- data/local/entity/ProductMaster.kt
- data/local/entity/PackageUnit.kt
- data/local/entity/MakerCache.kt
- data/local/entity/ScanSession.kt
- data/local/entity/ScanItem.kt
- data/local/entity/ProductGroup.kt
- data/local/entity/ProductGroupItem.kt
- data/local/converter/Converters.kt（全Enum用TypeConverter）
- data/local/dao/ProductMasterDao.kt（CRUD＋JAN検索＋かな検索＋メーカー検索＋規格検索＋ステータスフィルタ＋未取得フィルタ）
- data/local/dao/PackageUnitDao.kt（CRUD＋product_idで検索）
- data/local/dao/MakerCacheDao.kt（CRUD＋prefix検索）
- data/local/dao/ScanSessionDao.kt（CRUD＋ステータスフィルタ）
- data/local/dao/ScanItemDao.kt（CRUD＋session_idで検索＋重複JAN確認）
- data/local/dao/ProductGroupDao.kt（CRUD＋active/終了フィルタ）
- data/local/dao/ProductGroupItemDao.kt（CRUD＋group_idで検索）
- data/local/AppDatabase.kt（全Entity登録、version=1、Converters登録）

ルール：
- Entityの@ColumnInfo nameは01_SPEC.mdのスネークケース名を厳守
- DAOの戻り値はFlow<List<T>>またはsuspend関数
- AppDatabaseはexportSchema=true
完了条件： ビルド成功、Room schemaのJSONがschemas/に出力されること

Phase 3：DI設定＋ユーティリティ＋Repository
読み込みファイル： 02_TECH.md（セクション6）

AIへの指示プロンプト：

Phase 3の作業を開始します。

生成するファイル：
- di/DatabaseModule.kt（AppDatabase、全DAOのprovide）
- di/AppModule.kt（SettingsDataStoreのprovide等）
- data/repository/ProductRepository.kt（ProductMasterDao＋MakerCacheDao＋PackageUnitDaoを注入、JAN登録・検索・更新・リニューアル紐づけ・終売登録のビジネスロジック）
- data/repository/ScanRepository.kt（ScanSessionDao＋ScanItemDaoを注入）
- data/repository/GroupRepository.kt（ProductGroupDao＋ProductGroupItemDaoを注入、自動終了処理含む）
- data/settings/SettingsDataStore.kt（AI選択、貼り付け方式、ITF設定、セレクタ設定、スキャン音設定）
- util/Normalizer.kt（前後空白除去、連続スペース除去）
- util/JanCodeUtil.kt（CodeType判定、メーカープレフィックス抽出、ITFからJAN復元、チェックデジット計算）
- util/BarcodeGenerator.kt（ZXing EAN-13 Bitmap生成、ImageBitmap変換）
- util/AiPromptBuilder.kt（01_SPEC.mdの統一プロンプトを組み立て）
- util/AiResponseParser.kt（JSON正規表現抽出、JAN一致検証、ひらがなバリデーション）
- util/WebViewJsHelper.kt（プロンプト貼付、送信クリック、レスポンス抽出のJS Injection）
- util/ClipboardHelper.kt（クリップボードへのコピー・読み取り）

ルール：
- @Module @InstallIn(SingletonComponent::class)を使用
- RepositoryはSingleton
- 01_SPEC.mdセクション10の正規化ルールをNormalizerに実装
完了条件： ビルド成功

Phase 4：テーマ＋ナビゲーション＋ホーム画面
読み込みファイル： 02_TECH.md（セクション7）、01_SPEC.md（セクション11）

AIへの指示プロンプト：

Phase 4の作業を開始します。

生成するファイル：
- ui/theme/Color.kt
- ui/theme/Type.kt
- ui/theme/Theme.kt（Material3テーマ）
- ui/navigation/Routes.kt（02_TECH.mdセクション7のRoute定義をそのまま使用）
- ui/navigation/NavGraph.kt（全11画面のNavHost定義。Navigation Compose 2.9.7の型安全API使用）
- ui/home/HomeScreen.kt（ダッシュボード：登録商品数、未取得数、終売品数、各画面への導線ボタン）
- ui/home/HomeViewModel.kt
- MainActivity.ktを更新（NavGraph組み込み）

ルール：
- Routes.ktの@Serializableルート定義は02_TECH.mdそのまま使用、変更禁止
- NavHostのstartDestination = Route.Home
- 各画面への遷移はnavController.navigate(Route.Xxx)形式
完了条件： アプリ起動→ホーム画面表示、各画面へのナビゲーション遷移

Phase 5：スキャン3モード画面
読み込みファイル： 01_SPEC.md（セクション2,3）

AIへの指示プロンプト：

Phase 5の作業を開始します。01_SPEC.mdのセクション2「スキャン3モード」とセクション3「ITF読み取り制御」を読み込んでください。

生成するファイル：
- ui/scan/ScanScreen.kt（上部タブバーで3モード切替：連続/確認/紐づけ）
- ui/scan/ScanViewModel.kt
- ui/scan/components/ContinuousMode.kt（01_SPEC.md 2-1の仕様通り）
- ui/scan/components/ConfirmMode.kt（01_SPEC.md 2-2の仕様通り、情報取得ボタン含む。WebViewボトムシートは後のPhaseで実装するためボタンのみ配置）
- ui/scan/components/LinkageMode.kt（01_SPEC.md 2-3の仕様通り、3スロットUI）

Bluetooth HID入力の実装：
- TextFieldにfocusRequesterで常時フォーカス
- KeyEvent.KEYCODE_ENTERでバーコード確定
- ソフトキーボードは非表示（LocalSoftwareKeyboardController.current?.hide()）

ITF制御：
- SettingsDataStoreからITF設定読み取り
- OFFの場合、14桁コードはJanCodeUtil.detectCodeType()でITF14判定→スルー
完了条件： 3モード切替動作、BT入力のシミュレーション（実機不要の場合はキーボード入力で代替テスト可）

Phase 6：商品マスタ管理画面
読み込みファイル： 01_SPEC.md（セクション6,9）

AIへの指示プロンプト：

Phase 6の作業を開始します。

生成するファイル：
- ui/product/ProductListScreen.kt（01_SPEC.mdセクション9の全検索機能、フィルタ、グループタグバッジ、ステータスバッジ）
- ui/product/ProductListViewModel.kt
- ui/product/ProductDetailScreen.kt（全情報表示・編集、包装単位管理タブ、リニューアル管理セクション、終売登録ボタン）
- ui/product/ProductDetailViewModel.kt

リニューアル管理（01_SPEC.mdセクション6）：
- 3パターン実装：新品から旧品検索、旧品から新品指定、確認モードスキャン紐づけ
- リニューアルチェーン表示

検索：01_SPEC.mdセクション9の全種類
完了条件： 商品一覧表示、検索・フィルタ動作、詳細画面の表示・編集・リニューアル・終売

Phase 7：AI商品情報取得画面
読み込みファイル： 01_SPEC.md（セクション5）

AIへの指示プロンプト：

Phase 7の作業を開始します。01_SPEC.mdのセクション5「AI商品情報取得」を読み込んでください。

生成するファイル：
- ui/ai/AiFetchScreen.kt（上下分割：上=未取得JAN一覧+取得状況、下=WebView）
- ui/ai/AiFetchViewModel.kt
- ui/ai/components/AiWebViewWrapper.kt（AndroidView+標準WebViewのCompose Wrapper。Cookie保持、JS有効、DOMストレージ有効）
- ui/ai/components/AiResultPreview.kt（取得結果プレビュー＋確認ダイアログ）

WebView実装の注意点：
- Accompanistは使用禁止。AndroidView内で直接android.webkit.WebViewを使用
- evaluateJavascriptの戻り値はJSONエンコード文字列（前後にダブルクォート付き）
- CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)を設定
- UserAgentからWebView識別子"; wv"を除去

一括取得フロー：
1. 一括取得開始→リスト先頭未取得JAN選択
2. WebViewJsHelper.injectPrompt()でプロンプト貼付
3. WebViewJsHelper.clickSend()で送信
4. ポーリングでWebViewJsHelper.extractLatestResponse()を繰り返し呼び、生成完了検知
5. AiResponseParser.parseResponse()でJSON抽出・バリデーション
6. プレビュー更新→次のJANへ
7. 全件完了 or 停止ボタンで中断
8. 「取得済みを保存」で一括DB反映

手動フォールバック：
- ClipboardHelper.ToClipboard()でプロンプトコピー
- 「取り込み」ボタンでDOM抽出試行
- 失敗時はクリップボード読み取りフォールバック

確認モードの情報取得ボタン（Phase 5のConfirmMode.ktを更新）：
- BottomSheetでWebView展開→1件取得→確認ダイアログ→DB更新
完了条件： WebView表示、ログイン状態保持、自動貼付/送信/抽出のサイクル動作

Phase 8：グループ商品管理画面
読み込みファイル： 01_SPEC.md（セクション7）

AIへの指示プロンプト：

Phase 8の作業を開始します。

生成するファイル：
- ui/group/GroupListScreen.kt（有効グループ＋終了済みグループ一覧、新規作成ボタン）
- ui/group/GroupListViewModel.kt
- ui/group/GroupScanScreen.kt（グループへの連続スキャン追加、連続取得モードと同様の動作）
- ui/group/GroupScanViewModel.kt
- ui/group/GroupDetailScreen.kt（グループ内商品一覧）
- ui/group/GroupDetailViewModel.kt

自動終了：アプリ起動時にGroupRepositoryのdeactivateExpiredGroups()を呼び出し
タグバッジ：ProductListScreenとConfirmModeに有効グループのタグ色バッジを表示
完了条件： グループ作成・スキャン追加・一覧表示・自動終了

Phase 9：発注支援画面
読み込みファイル： 01_SPEC.md（セクション8）

AIへの指示プロンプト：

Phase 9の作業を開始します。

生成するファイル：
- ui/order/OrderScanScreen.kt（セッション作成、連続スキャン、同一JAN重複トースト、終売品警告）
- ui/order/OrderScanViewModel.kt
- ui/order/OrderListScreen.kt（一覧モード/フォーカスモード切替）
- ui/order/OrderListViewModel.kt

バーコード画像：BarcodeGenerator.generateEan13Bitmap()で生成
バーコードタップ非表示：01_SPEC.mdセクション8の仕様通り、トグル動作、進捗カウンター
フォーカスモード：1画面1商品、スワイプ移動、画面輝度自動最大化

画面輝度最大化の実装：
val window = (context as Activity).window
val layoutParams = window.attributes
layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
window.attributes = layoutParams
※DisposableEffectで元に戻す
完了条件： 発注スキャン→一覧表示→バーコード表示→タップ非表示→フォーカスモード

Phase 10：設定画面＋仕上げ
読み込みファイル： 01_SPEC.md全体、02_TECH.md全体

AIへの指示プロンプト：

Phase 10の作業を開始します。最終仕上げです。

生成/更新するファイル：
- ui/settings/SettingsScreen.kt（AI選択、WebViewログイン、セレクタ自動検出＋手動編集、貼り付け方式、ITF設定、スキャン音、フォーカスモード、データ管理）
- ui/settings/SettingsViewModel.kt
- HomeScreen.kt更新（ダッシュボード数値表示の実装）

セレクタ自動検出：
- Gemini/Perplexityのページをロード
- ヒューリスティックでinput/button/responseのCSSセレクタを検出
- 結果を手動編集可能なテキストフィールドに表示
- 保存でDataStoreに永続化

CSVエクスポート：
- ProductMasterの全データをCSV出力
- Intent.ACTION_CREATE_DOCUMENTでファイル保存先選択

全体テスト・調整：
- 全画面のナビゲーション遷移確認
- ProGuardルール追加（02_TECH.mdのルール参照）
- エッジケース処理（空文字、長文、特殊文字）
完了条件： 全機能動作、リリースビルド成功

開発完了チェックリスト
#	確認項目
1	全11画面が正常遷移する
2	3スキャンモードが正常動作する
3	ITF OFF時に14桁がスルーされる
4	MakerCacheによるメーカー名自動表示が動作する
5	AI情報取得（自動＋手動フォールバック）が動作する
6	確認モードの情報取得ボタンが動作する
7	リニューアル紐づけ3パターンが動作する
8	終売品登録・警告表示が動作する
9	グループ作成・スキャン追加・自動終了が動作する
10	発注一覧のバーコードタップ非表示が動作する
11	フォーカスモードのスワイプ・輝度最大化が動作する
12	リリースビルド（minify有効）が成功する