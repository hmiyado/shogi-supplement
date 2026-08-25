@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.gradle.api.tasks.PathSensitivity

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.analysis"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    // :kifu と同じ理由（NodeJsRootPluginの自動ダウンロードとFAIL_ON_PROJECT_REPOSの衝突回避）。
    // browser側のtestTaskは無効化し、:analysis:allTestsがブラウザテストの欠如で失敗しないようにする。
    js(IR) {
        browser {
            binaries.executable()
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    // Web版（CMP for Web）でレポート画面を動かすためのターゲット。js(IR)と同じ理由で
    // browser側のtestTaskは無効化する（wasmJsのブラウザテストランナーはCI/サンドボックスに
    // 用意しない方針。ルートbuild.gradle.ktsのWasmNodeJs/WasmYarn/Binaryen download=false
    // 設定と対で機能する）。
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
            // board/kifu パッケージ（ShogiBoard・ShogiMove等）を使う。api化する理由は
            // :shared/:ui/:kifu と同じ: このモジュールの公開シグネチャ（BlunderClassifier.classify等）が
            // ShogiBoard型を露出しているため、消費側が直接importできる必要がある。
            api(project(":kifu"))
            implementation(libs.kotlinx.serialization.json)
            // AuthRepository（dev.miyado.shogisupplement.auth）が公開シグネチャで
            // StateFlow を露出するため api化する（:kifu と同じ理由）。
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // ForceUpdatePolicyCheckerTest（runTest）用。
            implementation(libs.kotlinx.coroutines.test)
        }
        jsMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// jvmTestは係数表JSON（coefficients_hao_isolate_v1.json）をandroidApp/src/main/assetsの
// 正本からコピーする。:shared にも同じ正本を参照する同名タスクがある
// （複数モジュールのjvmTestが同じ正本を必要とするが、シンボリックリンクはOS/git設定差で
// 復元保証がないため使わない）。
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("jvmTestProcessResources") {
    from(rootProject.file("androidApp/src/main/assets/coefficients_hao_isolate_v1.json"))
}

// 資料の内容もテストの入力にする。これが無いと、資料だけ書き換えたときに
// jvmTestがUP-TO-DATEで飛ばされ、古いままでも緑になる。
tasks.named<Test>("jvmTest") {
    inputs.dir(rootProject.file("docs/opening"))
        .withPropertyName("openingDocs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// 戦型・囲いの資料（docs/opening）を定義データから生成する。判定を変えれば資料も変わる。
val generateOpeningDocs by tasks.registering(JavaExec::class) {
    group = "documentation"
    description = "対応している戦型・囲いの一覧と各ページを docs/opening へ生成する"
    val jvmMain = kotlin.jvm().compilations.getByName("main")
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("dev.miyado.shogisupplement.opening.GenerateOpeningDocsKt")
    args(rootProject.file("docs/opening").absolutePath)
}
