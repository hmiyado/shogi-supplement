plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.engine.remote"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // 失敗の種類（RemoteAnalysisException）・認証のport。
            api(project(":application"))
            // Workerと共有する通信DTO。
            implementation(project(":contracts"))
            implementation(libs.kotlinx.serialization.json)
            // HTTPエンジンはプラットフォーム側が供給する（Android=okhttp / iOS=darwin）。
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
