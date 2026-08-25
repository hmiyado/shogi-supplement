import java.util.Properties
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.roborazziPlugin)
    alias(libs.plugins.aboutlibrariesPlugin)
    alias(libs.plugins.kotlinSerialization)
}

tasks.matching { it.name.startsWith("prepareLibraryDefinitions") }.configureEach {
    enabled = false
}

// Why not Androidビルド時生成: Android/iOSで同じ確定済みJSONを使い、
// 依存更新とアプリビルドを分離する。
aboutLibraries {
    export {
        outputFile = file("src/main/res/raw/aboutlibraries.json")
        prettyPrint = true
    }
}

android {
    namespace = "dev.miyado.shogisupplement"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.miyado.shogisupplement"
        minSdk = 29
        targetSdk = 37
        // Why not moduleごとに定義: :sharedの生成定数と同じgradle.properties値を使い、
        // 強制アップデート判定との食い違いを防ぐ。
        versionCode = providers.gradleProperty("shogisupplement.versionCode").get().toInt()
        versionName = providers.gradleProperty("shogisupplement.versionName").get()

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
        // サーバー解析（Cloud Run）のベースURL。Play版は端末解析のままなので本番導線では
        // 使わず、debugビルドの疎通確認（DebugServerAnalysisReceiver）だけが参照する。
        // 未設定なら空文字になり、受信側が「未設定」として弾く。
        buildConfigField("String", "ANALYSIS_BASE_URL", "\"${localProps["ANALYSIS_BASE_URL"] ?: ""}\"")
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

    sourceSets {
        getByName("debug") {
            // DebugServerAnalysisReceiver が解析対象に使う棋譜。リポジトリのサンプルを
            // そのまま参照する（複製すると原本との差異に気づけないため）。debugのみ。
            assets.directories.add(rootProject.file("data/kifu_samples").path)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.toolchain.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.toolchain.get())
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
                // Why not アプリ本体もJava 21へ上げる: AboutLibraries 15のテスト用成果物だけが
                // Java 21を要求するため、アプリのJava 17互換性は維持する。
                it.javaLauncher.set(
                    javaToolchains.launcherFor {
                        languageVersion.set(JavaLanguageVersion.of(21))
                    },
                )
                // VRT一括実行でのテストJVMのOOM対策（Sentryテストノイズの根本の片割れ）。
                // Why not ヒープ増量だけ: 描画分がクラスをまたいで積み上がるため、
                // goldenが増えるたびに上限へ張り付く。一定クラスごとにJVMを作り直す。
                it.maxHeapSize = "4g"
                it.setForkEvery(5L)
            }
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(project(":data:database"))
    implementation(project(":data:supabase"))
    implementation(project(":engine:remote"))
    implementation(project(":analysis"))
    implementation(project(":application"))
    implementation(project(":kifu"))
    implementation(project(":engine:subprocess"))
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
