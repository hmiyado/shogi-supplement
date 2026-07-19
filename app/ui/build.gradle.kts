plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)

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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // ReportBackHandler（BackHandler の expect/actual）のAndroid実装用。
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
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
