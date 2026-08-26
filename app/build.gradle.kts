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
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig.GOOGLE_OAUTH_CLIENT_ID として参照できるようにする。
        // 値が空文字の場合は「未設定」を意味する（SETUP.md 参照）。
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleOauthClientId\"")
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
    implementation(libs.androidx.navigation.compose)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    // Room のマイグレーションテスト（MigrationTestHelper）
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // WorkManager のテスト用（TestListenableWorkerBuilder）。Robolectric を導入していないため androidTest 側に置く
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}