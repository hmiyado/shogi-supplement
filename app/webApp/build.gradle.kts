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

// エンジン資産の配信元。ソースに直書きしないのは、CDN移設（CloudFront等）の際に
// コード変更なしでビルドだけで切り替えられるようにするため。
val kentoAssetBaseUrl: String =
    providers.environmentVariable("KENTO_ASSET_BASE_URL").getOrElse("./kento-assets")

val generateKentoAssetConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/kentoAssetConfig/kotlin")
    inputs.property("baseUrl", kentoAssetBaseUrl)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("dev/miyado/shogisupplement/webApp/engine/AssetConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package dev.miyado.shogisupplement.webApp.engine
            |
            |/** ビルド時に環境変数 KENTO_ASSET_BASE_URL から生成される（webApp/build.gradle.kts）。 */
            |internal const val ASSET_BASE_URL = "$kentoAssetBaseUrl"
            |""".trimMargin(),
        )
    }
}

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
        wasmJsMain {
            kotlin.srcDir(generateKentoAssetConfig)
        }
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
