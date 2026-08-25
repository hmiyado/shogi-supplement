@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.ui"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
        androidResources {
            enable = true
        }
    }

    // iOS: ComposeUIViewController のエントリを提供する umbrella framework。
    // iosApp 側は SharedUi のみをリンクすればよい構成にする（frameworkを二重にリンクしない）。
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedUi"
            isStatic = true
            // api依存でもexportに明記しないとObjCヘッダに型が載らないため、
            // Swift側が直接参照する切り出しモジュールも列挙する。
            export(project(":kifu"))
            export(project(":analysis"))
            export(project(":application"))
            // Swiftが直接参照する: KentoAssetCachePolicy・KentoAssetManifest（:engine:remote）、
            // WasmStudyBridge・WasmAnalysisBridge・KentoSiteOverride（:engine:ios）、
            // DatabaseFactory（:data:database）。
            export(project(":engine:remote"))
            export(project(":engine:ios"))
            export(project(":data:database"))
        }
    }

    // CMP for Web（Kotlin/Wasm）本実装向け。:analysis/:kifu と同じ理由で
    // browser側のtestTaskは無効化する（ヘッドレスブラウザ未整備・:ui:allTestsの対象は
    // testAndroidHostTest/iosSimulatorArm64Testのまま変えない）。
    wasmJs {
        browser {
            binaries.executable()
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // 画面ソース（ViewModel・Composable）が参照するのは判定ロジック・UI消費型
            // （:analysis）とuse case・port（:application）、盤面表現（:kifu）のみ。
            // DBドライバ・Supabase・エンジン実行系のような具体実装は commonMain に置かない
            // （wasmJsでコンパイルできなくなるうえ、ViewModelが実装を知ることになるため）。
            // それらは android/ios のプラットフォームソースセット側にのみ持たせる。
            api(project(":analysis"))
            api(project(":application"))
            api(project(":kifu"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            // commonMainのViewModel基盤（AccountViewModel/DrillViewModel等）用。
            // Android では androidx.lifecycle への typealias のため実体は同一。
            implementation(libs.kmp.lifecycle.viewmodel)
            // OSSライセンス画面（LicenseInfoScreen）の一覧描画（LibrariesContainer）。
            // Android/iOS共通の commonMain 実装が Libs 型を扱うため :ui 側に置く
            // （データの読み込み手段はプラットフォーム側の責務のまま）。
            implementation(libs.aboutlibraries.compose.m3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // ReportBackHandler（BackHandler の expect/actual）のAndroid実装用。
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            // Why not implementation: SharedUi framework の export(...) は
            // このターゲットのapi依存であることを要求する（implementationではexport不可）。
            api(project(":data:database"))
            api(project(":engine:remote"))
            api(project(":engine:ios"))
            implementation(project(":data:supabase"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "dev.miyado.shogisupplement.ui.generated.resources"
    generateResClass = auto
}
