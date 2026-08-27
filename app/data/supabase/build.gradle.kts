@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.data.supabase"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
    jvm()
    wasmJs { browser() }

    // ktor-client-darwin導入後、cryptography-kotlinのCryptoKit Swift interopが要求する
    // Swift ABI互換シム（libswiftCompatibility56.a等）をリンカが見つけられず
    // "symbol(s) not found" でリンク失敗する。Kotlin/Nativeのリンカドライバは既定で
    // `/Applications/Xcode.app` を前提にSwiftライブラリ探索パスを構築するが、複数バージョン
    // 共存環境ではそのパスが存在しないことがあるため、`xcode-select -p` で実際の
    // Developer Dirを取得し、SDK別のSwift互換ライブラリディレクトリを -L で明示する。
    // Why not 無条件に呼ぶ: KMPは全ターゲットのbinaries設定をconfiguration時に評価するため、
    // xcode-selectが無いホスト（server/workerのDockerビルド等）ではプロセス起動自体が
    // 例外になりconfigurationごと失敗する。非macOSでは空文字へフォールバックする。
    // Why not org.gradle.internal.os.OperatingSystem: internalパッケージのAPIで
    // Gradleの更新により予告なく変わりうるため、`os.name` のみでOS判定する。
    val isMacOsHost = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    val xcodeDeveloperDir = if (isMacOsHost) {
        providers.exec {
            commandLine("xcode-select", "-p")
        }.standardOutput.asText.get().trim()
    } else {
        ""
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        val swiftSdkName = if (iosTarget.name == "iosArm64") "iphoneos" else "iphonesimulator"
        val swiftCompatLibDir =
            "$xcodeDeveloperDir/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$swiftSdkName"
        // テスト実行バイナリはmain+testを1つの実行体としてリンクするため、ここで通す必要がある。
        iosTarget.binaries.getTest("DEBUG").linkerOpts.add("-L$swiftCompatLibDir")
    }

    sourceSets {
        commonMain.dependencies {
            // 認証・アップロード・ダウンロードのportと、ビルド番号（強制アップデート判定用）。
            api(project(":application"))
            // Workerと共有する通信DTO（dev.miyado.shogisupplement.api）。
            implementation(project(":contracts"))
            implementation(libs.kotlinx.serialization.json)
            // supabase-kt/ktor-client-darwinのiOS klibはABI 2.3.0でビルドされており、
            // Kotlin 2.3系コンパイラで消費できるためcommonMainに置く
            // （HTTPエンジンはAndroid=okhttp（androidApp側で提供）/iOS=darwinを各所で注入）。
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.ktor.client.core)
            // 引き継ぎコード鍵導出（HKDF）・private_enc暗号化（AES-256-GCM）用。
            // provider-optimalがターゲットごとにJDK provider(JVM/Android)/CryptoKit・OpenSSL3(iOS)を
            // 自動選択するため、expect/actualはS本体の永続化（Keychain/Keystore）にだけ残る
            // （crypto/TransferSecretStore参照）。
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        iosMain.dependencies {
            // supabase-kt（ktor-client-core経由）のHTTPエンジンをiOS向けに提供。
            // Android側は androidApp/build.gradle.kts の ktor-client-okhttp が担う。
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}
