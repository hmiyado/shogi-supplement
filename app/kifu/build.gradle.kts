plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    androidTarget()
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        // kifu/board パッケージは commonMain 限定の純粋 Kotlin（platform固有APIなし）のため、
        // androidMain/jvmMain/iosMain 等のプラットフォームソースセットは作らない。
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "dev.miyado.shogisupplement.kifu"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
}

// jvmTestはリソースを複製せず、shared側と同じ実KIFサンプルをこのモジュールが正本として持つ
// （shared/src/jvmTest/resourcesには GoldenTest 用に miyado_game1/2 系のみ複製済み）。
