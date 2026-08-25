@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
}

// AndroidのversionCodeはgradle.propertiesが唯一の値源。ビルド時に定数として生成し、
// 強制アップデート判定（policy/BuildNumber）が参照する。
val generatedAndroidBuildNumberDir =
    layout.buildDirectory.dir("generated/sources/buildNumber/androidMain/kotlin")
val generateAndroidBuildNumber by tasks.registering(Copy::class) {
    val versionCode = providers.gradleProperty("shogisupplement.versionCode")
    inputs.property("versionCode", versionCode)
    from("src/androidMain/templates")
    into(generatedAndroidBuildNumberDir)
    rename { it.removeSuffix(".template") }
    filter { line -> line.replace("@APP_VERSION_CODE@", versionCode.get()) }
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.application"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    // :ui（wasmJs）がRepositoryのinterfaceを参照するため、:analysis と同じ理由で
    // browser側のtestTaskは無効化する。
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
        androidMain.get().kotlin.srcDir(generateAndroidBuildNumber)

        commonMain.dependencies {
            // 保存レコード・悪手レポート等のdomain型を公開シグネチャで露出する。
            api(project(":analysis"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
