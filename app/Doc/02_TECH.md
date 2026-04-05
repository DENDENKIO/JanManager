JAN商品管理アプリ 技術実装仕様書 v5.0 FINAL
1. バージョン互換性マトリクス（2026年4月5日検証済み）
項目	バージョン	備考
Android Studio	Panda 3 (2025.3.3) Stable	2026/4/2リリース
JDK	17	AGP 9.xの最小/デフォルト
Gradle	9.3.1	AGP 9.1要求の最小版
AGP	9.1.0	2026/3月Stable。Built-in Kotlin有効
Kotlin	2.2.0	AGP 9.0内蔵のKGP 2.2.10と互換。2.3.0は使わない
KSP	2.2.0-2.0.2	Kotlin 2.2.0対応のKSP2
Compose BOM	2026.03.00	2026/3/11リリース、Compose 1.10.x
Room	2.8.4	Stable、minSdk 23、KSP2対応
Dagger/Hilt	2.59.2	AGP 9.0必須。2.57.xはAGP 9非対応
Hilt(androidx)	1.3.0	KSP2対応
Navigation Compose	2.9.7	Nav2最新Stable、型安全ナビ対応
Lifecycle	2.9.0	Compose BOM経由
ZXing core	3.5.3	バーコード画像生成
Kotlin Serialization	2.2.0	Navigation型安全ルートに必要
DataStore	1.1.7	設定永続化
compileSdk	35	
targetSdk	35	Google Play要件
minSdk	26	
2. 既知の地雷と回避策（重要度順）
地雷1：AGP 9.0のBuilt-in Kotlin AGP 9.0以降、org.jetbrains.kotlin.androidプラグインは不要かつ適用禁止。AGPがKotlinを内蔵する。libs.versions.tomlとbuild.gradle.ktsからkotlin-androidプラグインを完全に削除すること。ただしkotlin-composeプラグインとkotlin-serializationプラグインは引き続き必要。

地雷2：Dagger 2.59以降はAGP 9.0必須 Dagger 2.59でAGP 9.0が要件に追加された。2.57.xとAGP 9の組み合わせは非推奨。必ずDagger 2.59.2を使用。

地雷3：Room + AGP 9.0 + KSPの初期化問題 AGP 9.0環境でRoom KSPがデータベース実装クラスを見つけられない報告あり(Reddit)。Room Gradle Pluginを必ず適用し、schemaDirectoryを設定すること。解決しない場合はgradle.propertiesにroom.generateKotlin=trueを明示追加。

地雷4：Navigation Compose型安全ルート Navigation 2.8以降、ルートを@Serializableデータクラスで定義する型安全APIが標準。kotlin-serializationプラグインが必要。旧String方式は非推奨。

地雷5：Accompanist WebView非推奨 AndroidViewで標準android.webkit.WebViewを直接使用。

地雷6：Compose Compiler指定不要 kotlin-composeプラグイン適用のみでOK。旧composeOptionsブロックは書かない。

3. gradle/libs.versions.toml
Copy[versions]
agp = "9.1.0"
kotlin = "2.2.0"
ksp = "2.2.0-2.0.2"
compose-bom = "2026.03.00"
room = "2.8.4"
hilt = "2.59.2"
hilt-navigation-compose = "1.3.0"
navigation = "2.9.7"
lifecycle = "2.9.0"
core-ktx = "1.15.0"
activity-compose = "1.10.0"
datastore = "1.1.7"
zxing-core = "3.5.3"
kotlin-serialization-json = "1.7.3"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing-core" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlin-serialization-json" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
Copy
注意：kotlin-androidプラグインは定義しない。AGP 9.0のBuilt-in Kotlin機能で不要。

4. ルート build.gradle.kts
Copyplugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
5. app/build.gradle.kts
Copyplugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.example.janmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.janmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.serialization.json)
}
Copy
注意：kotlinOptions { jvmTarget }はAGP 9.0のBuilt-in Kotlinでは不要。AGPが自動設定。

6. パッケージ構成（確定ファイル名）
Copycom.example.janmanager/
├── JanManagerApp.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── entity/
│   │   │   ├── Enums.kt
│   │   │   ├── ProductMaster.kt
│   │   │   ├── PackageUnit.kt
│   │   │   ├── MakerCache.kt
│   │   │   ├── ScanSession.kt
│   │   │   ├── ScanItem.kt
│   │   │   ├── ProductGroup.kt
│   │   │   └── ProductGroupItem.kt
│   │   ├── dao/
│   │   │   ├── ProductMasterDao.kt
│   │   │   ├── PackageUnitDao.kt
│   │   │   ├── MakerCacheDao.kt
│   │   │   ├── ScanSessionDao.kt
│   │   │   ├── ScanItemDao.kt
│   │   │   ├── ProductGroupDao.kt
│   │   │   └── ProductGroupItemDao.kt
│   │   └── converter/
│   │       └── Converters.kt
│   ├── repository/
│   │   ├── ProductRepository.kt
│   │   ├── ScanRepository.kt
│   │   └── GroupRepository.kt
│   └── settings/
│       └── SettingsDataStore.kt
├── di/
│   ├── AppModule.kt
│   └── DatabaseModule.kt
├── ui/
│   ├── navigation/
│   │   ├── NavGraph.kt
│   │   └── Routes.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── scan/
│   │   ├── ScanScreen.kt
│   │   ├── ScanViewModel.kt
│   │   └── components/
│   │       ├── ContinuousMode.kt
│   │       ├── ConfirmMode.kt
│   │       └── LinkageMode.kt
│   ├── product/
│   │   ├── ProductListScreen.kt
│   │   ├── ProductListViewModel.kt
│   │   ├── ProductDetailScreen.kt
│   │   └── ProductDetailViewModel.kt
│   ├── ai/
│   │   ├── AiFetchScreen.kt
│   │   ├── AiFetchViewModel.kt
│   │   └── components/
│   │       ├── AiWebViewWrapper.kt
│   │       └── AiResultPreview.kt
│   ├── order/
│   │   ├── OrderScanScreen.kt
│   │   ├── OrderScanViewModel.kt
│   │   ├── OrderListScreen.kt
│   │   └── OrderListViewModel.kt
│   ├── group/
│   │   ├── GroupListScreen.kt
│   │   ├── GroupListViewModel.kt
│   │   ├── GroupScanScreen.kt
│   │   ├── GroupScanViewModel.kt
│   │   ├── GroupDetailScreen.kt
│   │   └── GroupDetailViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── theme/
│       ├── Theme.kt
│       ├── Color.kt
│       └── Type.kt
├── util/
│   ├── Normalizer.kt
│   ├── JanCodeUtil.kt
│   ├── BarcodeGenerator.kt
│   ├── AiPromptBuilder.kt
│   ├── AiResponseParser.kt
│   ├── WebViewJsHelper.kt
│   └── ClipboardHelper.kt
7. Navigation Routes（型安全）
Copy// ui/navigation/Routes.kt
import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Scan : Route
    @Serializable data object ProductList : Route
    @Serializable data class ProductDetail(val janCode: String) : Route
    @Serializable data object AiFetch : Route
    @Serializable data object OrderScan : Route
    @Serializable data class OrderList(val sessionId: Long) : Route
    @Serializable data object GroupList : Route
    @Serializable data class GroupScan(val groupId: Long) : Route
    @Serializable data class GroupDetail(val groupId: Long) : Route
    @Serializable data object Settings : Route
}
8. AndroidManifest.xml
Copy<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <application
        android:name=".JanManagerApp"
        android:allowBackup="true"
        android:usesCleartextTraffic="false"
        android:theme="@style/Theme.JanManager">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
9. ビルド前チェックリスト
#	項目	確認内容
1	JDK	Settings > Build > Gradle > Gradle JDK = 17
2	Gradle Wrapper	distributionUrl=gradle-9.3.1-bin.zip
3	kotlin-androidプラグイン	存在しないことを確認
4	KSP	先頭がKotlinバージョンと一致(2.2.0-x.x.x)
5	Hilt	2.59.2であること
6	composeOptions	存在しないことを確認
7	kotlinOptions	存在しないことを確認（AGP 9自動）
8	Room schemaDirectory	設定されていること