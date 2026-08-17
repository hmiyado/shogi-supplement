@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.kifu"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    // Why browser+nodejs両方: 生成物はブラウザで使うが、テストはnodejsランナーで
    // 実行する(ヘッドレスブラウザ(karma+Chrome)をCI/サンドボックスに用意しない方針)。
    // browser側のtestTaskは無効化し、:kifu:allTestsがブラウザテストの欠如で失敗しないようにする。
    js(IR) {
        browser {
            binaries.executable()
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    // CMP for Web（Kotlin/Wasm）本実装向け。board/kifu パッケージは commonMain 限定の
    // 純粋Kotlinのため wasmJs でもそのままコンパイルできる。js(IR)と同じ理由
    // （ヘッドレスブラウザ未整備）でbrowser側のtestTaskは無効化する。
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
        // kifu/board パッケージは commonMain 限定の純粋 Kotlin（platform固有APIなし）のため、
        // androidMain/jvmMain/iosMain 等のプラットフォームソースセットは作らない。
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// jvmTestはリソースを複製せず、shared側と同じ実KIFサンプルをこのモジュールが正本として持つ
// （shared/src/jvmTest/resourcesには GoldenTest 用に miyado_game1/2 系のみ複製済み）。
