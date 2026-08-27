@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// docs/kento.html（「棋譜を検討する」ページ）を配信する実行可能wasmJsアプリ。
// :ui のレポート画面（ShogiTheme/ReportScreen等）をそのままブラウザで動かす
// （アプリ本体と見た目・判定ロジックを共有する）。
// 出力ファイル名はプロジェクト名（webApp）から決まる既定値をそのまま使う（webApp.js/webApp.wasm）。

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

// docs/mypage.html（引き継ぎコードでログインし棋譜一覧を見るページ）用の設定。
// anon keyはクライアント埋め込み前提の鍵（モバイルのBuildConfig.SUPABASE_KEYと同じ性質）で、
// 秘密ではないためビルド引数で渡す。未設定時は空文字にし、実行時にエラー表示させる
// （ANALYSIS_BASE_URL方式に倣う）。
val mypageSupabaseUrl: String = providers.environmentVariable("SUPABASE_URL").getOrElse("")
val mypageSupabaseAnonKey: String = providers.environmentVariable("SUPABASE_ANON_KEY").getOrElse("")
val mypageWorkerBaseUrl: String = providers.environmentVariable("WORKER_BASE_URL").getOrElse("")

val generateMyPageConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/myPageConfig/kotlin")
    inputs.property("supabaseUrl", mypageSupabaseUrl)
    inputs.property("supabaseAnonKey", mypageSupabaseAnonKey)
    inputs.property("workerBaseUrl", mypageWorkerBaseUrl)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("dev/miyado/shogisupplement/webApp/mypage/MyPageConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package dev.miyado.shogisupplement.webApp.mypage
            |
            |/** ビルド時に環境変数から生成される（webApp/build.gradle.kts）。 */
            |internal const val SUPABASE_URL = "$mypageSupabaseUrl"
            |internal const val SUPABASE_ANON_KEY = "$mypageSupabaseAnonKey"
            |internal const val WORKER_BASE_URL = "$mypageWorkerBaseUrl"
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
            kotlin.srcDir(generateMyPageConfig)
            dependencies {
                implementation(project(":data:supabase"))
                implementation(project(":application"))
                implementation(libs.supabase.auth)
                implementation(libs.supabase.postgrest)
                implementation(libs.kmp.lifecycle.viewmodel)
            }
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
