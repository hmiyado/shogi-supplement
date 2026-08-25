@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
}

// :kifu が追加する js(IR) ターゲット向け。Kotlin/JS の NodeJsRootPlugin は既定で
// Node.js本体を専用リポジトリから独自にダウンロードしようとし、そのリポジトリを
// rootProjectへ動的追加する。このリポジトリ管理はdependencyResolutionManagementに
// 一元化する方針（repositoriesMode=FAIL_ON_PROJECT_REPOS）を敷いているため、この動的追加自体が
// ビルドエラーになる（"repository ... was added by unknown code"）。ホストに導入済みの
// Node.jsをそのまま使う設定にして、この動的リポジトリ追加自体を起こさせない
// （CI・ローカルどちらもNode.jsが前提のマシンで動かす運用。npm依存の解決も
// kotlin.js.yarn=false によりNode同梱のnpmを使い、Yarnを要求しない）。
// downloadBaseUrlを明示的に空にする: KGPは「downloadBaseUrlに値がある場合のみ、その
// ダウンロード元をrepositories{}へ追加する」実装になっている(EnvSpec.downloadBaseUrlの
// KDoc参照)。download=falseだけではリポジトリ追加自体は止まらない(実測で確認済み)。
//
// NodeJsEnvSpecはproject単位(jsターゲットを持つ各サブプロジェクト自身、ここでは:kifu)に
// 作られる拡張であり、rootProjectには別インスタンスが作られる(NodeJsRootPluginApplierが
// 内部でsingleNodeJsPluginApply経由でrootにも1つ作るが、それは:kifuの実タスクが参照する
// ものとは別物)。そのため全プロジェクトに対して設定する。
allprojects {
    plugins.withType(NodeJsPlugin::class.java) {
        extensions.configure(NodeJsEnvSpec::class.java) {
            download.set(false)
            downloadBaseUrl.set(null as String?)
        }
    }
}

// :analysis/:kifu が追加する wasmJs ターゲット向け（CMP for Web本実装）。wasmJsは上の
// js(IR)用設定（NodeJsPlugin/NodeJsEnvSpec）とは別クラス（WasmNodeJsPlugin/WasmNodeJsEnvSpec・
// WasmYarnPlugin/WasmYarnRootEnvSpec）を使うため、js(IR)側の設定・
// gradle.properties の kotlin.js.yarn=false は効かず、同じ理由で個別に対処が要る。
allprojects {
    plugins.withType(WasmNodeJsPlugin::class.java) {
        extensions.configure(WasmNodeJsEnvSpec::class.java) {
            download.set(false)
            downloadBaseUrl.set(null as String?)
        }
    }
}
rootProject.plugins.withType(WasmYarnPlugin::class.java) {
    rootProject.extensions.configure(WasmYarnRootEnvSpec::class.java) {
        download.set(false)
        downloadBaseUrl.set(null as String?)
    }
}

// production distribution（wasmJsBrowserDistribution）は wasm-opt（binaryen）でのサイズ最適化を
// 既定で挟む。同じ理由でダウンロードを止め、ホストに導入済みの実行体を使う。
// パスはOS依存（ローカルmacOSはbrewの既定パスへ固定・CI等それ以外は
// WASM_OPT_PATH環境変数で明示的に渡す想定）。
allprojects {
    plugins.withType(BinaryenPlugin::class.java) {
        extensions.configure(BinaryenEnvSpec::class.java) {
            download.set(false)
            downloadBaseUrl.set(null as String?)
            command.set(System.getenv("WASM_OPT_PATH") ?: "/opt/homebrew/bin/wasm-opt")
        }
    }
}

// ================================================================================
// モジュール境界の検証（docs/app-architecture.md「composition rootと依存規則」）
//
// 規則を文章で置くだけでは、依存を1行足したときに誰も気付けない。Gradleのproject依存を
// 直接読んで機械的に落とす。source setごとの構成（commonMainApi・iosMainImplementation等）を
// 見るため、「commonMainは実装を知らないがiosMainは組み立てる」という粒度まで検査できる。

/** 依存してはいけない相手。configPrefixを指定した規則はその接頭辞の構成だけを見る。 */
data class ForbiddenDependency(
    val module: String,
    val forbidden: Regex,
    val reason: String,
    val configPrefix: String? = null,
)

val forbiddenDependencies = listOf(
    ForbiddenDependency(
        module = ":ui",
        configPrefix = "commonMain",
        forbidden = Regex("^:(data|engine):"),
        reason = "commonMainのViewModelは具体実装を知らない（組み立てるのはcomposition rootだけ）",
    ),
    ForbiddenDependency(
        module = ":application",
        forbidden = Regex("^:(data|engine|ui):"),
        reason = "use caseとportは実装へ依存しない（実装がportを実装する向きだけ許す）",
    ),
    ForbiddenDependency(
        module = ":analysis",
        forbidden = Regex("^:(?!kifu$)"),
        reason = "解析domainが依存してよいのは盤面表現だけ",
    ),
    ForbiddenDependency(
        module = ":contracts",
        forbidden = Regex("^:(data|engine|ui):"),
        reason = "通信DTOは実装へ依存しない",
    ),
    ForbiddenDependency(
        module = ":server:worker",
        forbidden = Regex("^:(data:|ui$|engine:(ios|remote)$)"),
        reason = "Workerはクライアント用インフラ（DB・Supabase・暗号）へ依存しない",
    ),
)

/**
 * `api(project(...))` による再公開の許可リスト。ここに無い再公開は失敗させる
 * （公開境界は増やさず、使うモジュールへ直接依存させるため）。
 */
val allowedReExports = mapOf(
    ":analysis" to setOf(":kifu"),
    ":application" to setOf(":analysis"),
    ":contracts" to setOf(":analysis"),
    ":data:database" to setOf(":application"),
    ":data:supabase" to setOf(":application"),
    ":engine:remote" to setOf(":application"),
    ":engine:ios" to setOf(":analysis"),
    ":engine:subprocess" to setOf(":analysis"),
    // :ui はiOSのframeworkがexportする型を持つモジュールをapiで持つ必要がある
    // （export(...)はapi依存であることを要求するため）。
    ":ui" to setOf(":analysis", ":application", ":kifu", ":data:database", ":engine:remote", ":engine:ios"),
)

val checkModuleBoundaries by tasks.registering {
    group = "verification"
    description = "モジュール間のproject依存が設計書の規則に反していないか検査する"

    doLast {
        val violations = mutableListOf<String>()

        // 開発者が dependencies {} に書く構成だけを見る。解決済みclasspathやKGPが内部で
        // 作る構成は同じ依存を別名で再掲するため、二重に数えると規則の意味が変わる。
        // テスト用の構成は対象外（テストはfakeではなく実装を組み立てて確かめてよい）。
        val declarableConfig =
            Regex("^(implementation|api|compileOnly|runtimeOnly)$|^[a-z][A-Za-z0-9]*(Api|Implementation|CompileOnly|RuntimeOnly)$")

        subprojects.forEach { sub ->
            val edges = sub.configurations
                .filter { declarableConfig.matches(it.name) && !it.name.contains("est") }
                .flatMap { config ->
                    config.dependencies
                        .filterIsInstance<ProjectDependency>()
                        .map { config.name to it.path }
                }

            forbiddenDependencies
                .filter { it.module == sub.path }
                .forEach { rule ->
                    edges.filter { (configName, target) ->
                        (rule.configPrefix == null || configName.startsWith(rule.configPrefix)) &&
                            rule.forbidden.containsMatchIn(target)
                    }.forEach { (configName, target) ->
                        violations += "${sub.path} の $configName が $target へ依存している（${rule.reason}）"
                    }
                }

            val allowed = allowedReExports[sub.path].orEmpty()
            edges.filter { (configName, _) -> configName.endsWith("Api") }
                .map { (_, target) -> target }
                .distinct()
                .filterNot { it in allowed }
                .forEach { target ->
                    violations += "${sub.path} が $target を api で再公開している" +
                        "（許可リストに無い。増やすなら build.gradle.kts の allowedReExports へ理由とともに足す）"
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "モジュール境界の規則違反:\n" + violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}
