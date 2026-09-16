import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// local.properties から OAuth クライアント ID を読み込む。
// local.properties は .gitignore 済みなので、機密値をリポジトリに含めずに済む。
// 未設定でもビルドが通るよう空文字にフォールバックする（アプリ側は空文字を「未設定」として扱う）。
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val googleOauthClientId: String = localProperties.getProperty("GOOGLE_OAUTH_CLIENT_ID") ?: ""

// Discovery バックエンドの接続先。実機は "localhost" が実機自身を指すため使えない。
// 開発機とのLAN内IPをデフォルトにし、local.properties の DISCOVERY_BASE_URL で上書き可能にする。
val discoveryBaseUrl: String =
    localProperties.getProperty("DISCOVERY_BASE_URL") ?: "http://192.168.68.57:8000"

// リリース署名設定。local.properties に RELEASE_STORE_FILE 等が無ければ null のままにし、
// release ビルドは未署名になる（Play へは提出できないが、開発中のビルドは通す）。
val releaseStoreFile: String? = localProperties.getProperty("RELEASE_STORE_FILE")
val releaseStorePassword: String? = localProperties.getProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = localProperties.getProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = localProperties.getProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig: Boolean =
    !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.mikke.discovery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mikke.discovery"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig.GOOGLE_OAUTH_CLIENT_ID として参照できるようにする。
        // 値が空文字の場合は「未設定」を意味する（SETUP.md 参照）。
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleOauthClientId\"")
        // BuildConfig.DISCOVERY_BASE_URL として参照できるようにする。
        buildConfigField("String", "DISCOVERY_BASE_URL", "\"$discoveryBaseUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // BuildConfig クラスの生成を有効化（GOOGLE_OAUTH_CLIENT_ID を公開するため）
        buildConfig = true
    }

    testOptions {
        unitTests {
            // 案件29：Robolectric で AndroidManifest / リソースを読めるようにする
            isIncludeAndroidResources = true
        }
    }

    // MigrationTestHelper は端末上の assets からスキーマ JSON を読むので、
    // 書き出し先の schemas/ を androidTest の assets に含める。
    sourceSets.getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
    }
}

// Room のスキーマ JSON の書き出し先。
// androidx.room の Gradle プラグインを足さずに済むよう KSP の引数で指定している。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // WorkManager
    // work-runtime-ktx transitively pins androidx.concurrent:concurrent-futures(-ktx) to
    // {strictly 1.1.0}, which conflicts with Espresso 3.7.0 / androidx.test:junit 1.2.1's
    // {strictly 1.2.0}. Exclude work's pin and declare our own version below.
    implementation(libs.androidx.work.runtime.ktx) {
        exclude(group = "androidx.concurrent", module = "concurrent-futures")
        exclude(group = "androidx.concurrent", module = "concurrent-futures-ktx")
    }
    implementation(libs.androidx.concurrent.futures.ktx)

    // Google 認可（AuthorizationClient で Calendar スコープの認可コードを取得する）
    implementation(libs.play.services.auth)

    // Google Calendar REST API クライアント
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    // ログ出力はデバッグ時のみ有効化する想定だが、main ソースセットから
    // 参照できるよう implementation で入れる（BuildConfig.DEBUG で切り替えること）
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    // Discovery の NotificationLog で使われる kotlinx.datetime.Instant を app モジュールでも参照する
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    // 案件29：Activity/Intent を JVM 上で検証するため Robolectric を導入
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    // Room のマイグレーションテスト（MigrationTestHelper）
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // WorkManager のテスト用（TestListenableWorkerBuilder / WorkManagerTestInitHelper）
    testImplementation(libs.androidx.work.testing) {
        exclude(group = "androidx.concurrent", module = "concurrent-futures")
        exclude(group = "androidx.concurrent", module = "concurrent-futures-ktx")
    }
    testImplementation(libs.androidx.concurrent.futures.ktx)
    androidTestImplementation(libs.androidx.work.testing) {
        exclude(group = "androidx.concurrent", module = "concurrent-futures")
        exclude(group = "androidx.concurrent", module = "concurrent-futures-ktx")
    }
    androidTestImplementation(libs.androidx.concurrent.futures.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}