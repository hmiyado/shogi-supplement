plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.engine.subprocess"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Why not commonMain: java.lang.ProcessBuilder はJVM/Android専用APIで、
        // KMPの標準階層にjvm+android専用の合流点が無いため手動でdependsOnを配線する。
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)

        commonMain.dependencies {
            // Engine interface と Score（USI出力のパース結果）。
            api(project(":analysis"))
        }
    }
}
