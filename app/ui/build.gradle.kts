plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    androidTarget()

    // iOS: ComposeUIViewController のエントリを提供する umbrella framework。
    // :shared を export し、iosApp 側は SharedUi のみをリンクすればよい構成にする
    // （:shared と :ui の framework を二重にリンクしない）。
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedUi"
            isStatic = true
            export(project(":shared"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
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
        // IosMainController がサーバー解析（RemoteAnalysisRunner）用に HttpClient(Darwin) を
        // 直接構築するため（:shared側は implementation 依存のため api経由では見えない。
        // shared/build.gradle.ktsのiosMainブロックと同じ方針）。
        iosMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "dev.miyado.shogisupplement.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "dev.miyado.shogisupplement.ui.generated.resources"
    generateResClass = auto
}
