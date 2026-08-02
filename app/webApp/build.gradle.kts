@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// docs/kento.html（「棋譜を検討する」ページ）を配信する実行可能wasmJsアプリ。
// :ui のレポート画面（ShogiTheme/ReportScreen等）をそのままブラウザで動かす
// （アプリ本体と見た目・判定ロジックを共有する。CMP for Web Phase C）。
// 出力ファイル名はプロジェクト名（webApp）から決まる既定値をそのまま使う
// （webApp.js/webApp.wasm。docs/kento/ 配下へ配置する運用は docs/copy-kento-assets.sh 参照）。
kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

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
            implementation(project(":ui"))
            implementation(project(":analysis"))
            implementation(project(":kifu"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
