import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazziPlugin)
    alias(libs.plugins.aboutlibrariesPlugin)
    alias(libs.plugins.kotlinSerialization)
}

// OSSライセンス一覧の生成設定（AboutLibraries）。
// ビルド時の自動生成は無効化し、`./gradlew :androidApp:exportLibraryDefinitions` で
// src/main/res/raw/aboutlibraries.json を手動生成してコミットする（再現性・オフラインビルド優先）。
aboutLibraries {
    android {
        registerAndroidTasks = false
    }
    export {
        outputFile = file("src/main/res/raw/aboutlibraries.json")
        prettyPrint = true
    }
}

android {
    namespace = "dev.miyado.shogisupplement"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.miyado.shogisupplement"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase の URL と anon key を BuildConfig に注入（local.properties から読み込み）
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(localPropsFile.inputStream())
        }
        buildConfigField("String", "SUPABASE_URL", "\"${localProps["SUPABASE_URL"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProps["SUPABASE_KEY"] ?: ""}\"")
        // Sentry DSN も同じ経路で注入する（公開すると外部からイベントを投げ込まれる
        // リスクがあるため非公開化。未設定時は ShogiApp.initSentry() 側で初期化自体をスキップする）
        buildConfigField("String", "SENTRY_DSN", "\"${localProps["SENTRY_DSN"] ?: ""}\"")
    }

    // リリース署名: app/keystore.properties から読み込み（git管理外）。
    val keystoreProps = Properties()
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        keystoreProps.load(keystorePropsFile.inputStream())
    }
    fun keystoreSecret(key: String): String = keystoreProps[key] as? String ?: ""

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreSecret("storePassword")
                keyAlias = keystoreSecret("keyAlias")
                keyPassword = keystoreSecret("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8縮小は初回リリースでは無効（sqldelight/supabase/sentryのkeepルール検証が未了。
            // APKサイズの主因は評価関数61MBなので縮小の効果も小さい）
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Android 10+ W^X: ネイティブライブラリを圧縮せずAPKに格納し、
    // nativeLibraryDir から直接 exec できるようにする
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // VRT一括実行でのテストJVMのOOM対策（Sentryテストノイズの根本の片割れ）
                it.maxHeapSize = "2g"
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":ui"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.sqldelight.android.driver)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Supabase Auth + Ktor OkHttp engine (Android)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)

    // OSSライセンス一覧画面（AboutLibraries Compose M3）
    implementation(libs.aboutlibraries.compose.m3)

    // クラッシュレポート（Sentry Android SDK）
    implementation(libs.sentry.android)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // VRT: Roborazzi + Robolectric（JVM スクリーンショットテスト）
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.tooling)
    // ComponentActivity をテスト用 manifest に登録（Robolectric の createComposeRule 用）。
    // testImplementation では manifest マージに参加しないため debugImplementation にする
    debugImplementation(libs.compose.ui.test.manifest)

    // Unit tests: coroutines + in-memory SQLite
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqldelight.sqlite.driver)
}
