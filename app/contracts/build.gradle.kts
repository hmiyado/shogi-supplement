plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.contracts"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // DTOはdomain型を持たないが、ワイヤ形式とdomainの相互変換（AnalysisApiMapping）は
            // 送受信の両側が同じ実装を使う必要があるためこのモジュールに置く。
            // Why not :analysis側に置く: domainがワイヤ形式を知ることになるため。
            api(project(":analysis"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
