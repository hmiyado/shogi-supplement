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
