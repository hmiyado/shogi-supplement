plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelightPlugin)
}

sqldelight {
    databases {
        create("ShogiSupplementDatabase") {
            packageName.set("dev.miyado.shogisupplement.db")
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    androidTarget()
    jvm()

    // iOS: framework出力＋エンジンin-process化のcinterop接続。
    // 純度チェック: ./gradlew :shared:compileKotlinIosArm64
    //
    // engine_wrapper.h をcinterop経由でiosMainに公開する。実体
    // （libshogiengine.a = wrapper.cpp + libyaneuraou.a をbuild_ios.shがマージしたもの）は
    // ターゲットごとに出力先が異なる（iosSimulatorArm64=engine/build/sim、
    // iosArm64=engine/build/device）ため、linkerOptsでターゲット別に -L/-l を通す。
    // 注意（実測で確認済み）:
    // - テスト実行バイナリ（iosSimulatorArm64Test）はmain+testを1つの「実行体」として
    //   リンクするため、cinterop実体へのリンカフラグが必須（無いとwrapperシンボル
    //   未解決でリンク失敗する）。binaries.getTest("DEBUG") 側のlinkerOptsがそれ。
    // - 一方、静的framework（Shared / :uiのSharedUi）は最終リンクを消費側に委ねるため、
    //   ここのframework linkerOptsだけではシンボルは埋め込まれない。iosApp（Xcode）側の
    //   OTHER_LDFLAGS/LIBRARY_SEARCH_PATHSでも -lshogiengine を通す（iosApp/project.yml参照）。
    val engineWrapperDir = rootProject.projectDir.resolve("iosApp/engine/wrapper")
    // ktor-client-darwin導入後、cryptography-kotlinのCryptoKit Swift interopが要求する
    // Swift ABI互換シム（libswiftCompatibility56.a等）をリンカが見つけられず
    // "symbol(s) not found" でリンク失敗する。Kotlin/Nativeのリンカドライバは既定で
    // `/Applications/Xcode.app` を前提にSwiftライブラリ探索パスを構築するが、複数バージョン
    // 共存環境ではそのパスが存在しないことがあるため、`xcode-select -p` で実際の
    // Developer Dirを取得し、SDK別のSwift互換ライブラリディレクトリを -L で明示する。
    // Why not call unconditionally: KMPは全ターゲットのbinaries設定をconfiguration時に
    // 評価するため、xcode-selectがそもそも存在しないLinuxホスト（server/workerの
    // Dockerfileビルド等、iOSターゲットを一切ビルドしないタスクの実行時も含む）では
    // プロセス起動自体が例外になり :shared のconfigurationごと失敗する。iOSのリンカ設定は
    // macOSホストでのみ意味を持つため、非macOSでは空文字にフォールバックし、
    // configurationは通す（iOSターゲットの実コンパイル/リンクは元々非macOSでは動かない）。
    // Why not org.gradle.internal.os.OperatingSystem: internalパッケージのAPIで
    // Gradleの更新により予告なく変わりうるため、`os.name` システムプロパティという
    // 公開されたJVM標準APIのみでOS判定する。
    val isMacOsHost = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    val xcodeDeveloperDir = if (isMacOsHost) {
        providers.exec {
            commandLine("xcode-select", "-p")
        }.standardOutput.asText.get().trim()
    } else {
        ""
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        val engineLibDir = rootProject.projectDir.resolve(
            "iosApp/engine/build/" + if (iosTarget.name == "iosArm64") "device" else "sim"
        )
        val swiftSdkName = if (iosTarget.name == "iosArm64") "iphoneos" else "iphonesimulator"
        val swiftCompatLibDir =
            "$xcodeDeveloperDir/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$swiftSdkName"
        val engineLinkerOpts = listOf(
            "-L${engineLibDir.absolutePath}", "-lshogiengine", "-lc++",
            "-L$swiftCompatLibDir",
        )

        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts.addAll(engineLinkerOpts)
        }
        // iosSimulatorArm64Test（回帰4系統の1つ）はmain+testを1つの実行体としてリンクする
        // ため、cinterop実体（libshogiengine.a）へのリンカフラグを test バイナリにも通す。
        iosTarget.binaries.getTest("DEBUG").linkerOpts.addAll(engineLinkerOpts)

        iosTarget.compilations.getByName("main") {
            cinterops.create("engine_wrapper") {
                defFile(project.file("src/nativeInterop/cinterop/engine_wrapper.def"))
                packageName("dev.miyado.shogisupplement.engine.wrapper")
                includeDirs(engineWrapperDir)
            }
        }
    }

    sourceSets {
        // androidTarget/jvmの両方でしか使えないAPI（java.lang.ProcessBuilder等）を置く
        // 中間source set。KMPの標準階層にjvm+android専用の合流点は無いため手動でdependsOnを配線する。
        // Why not commonMain: java.lang.ProcessBuilder はJVM/Android専用APIでiOS(Kotlin/Native)には
        // 存在せず、commonMainに置くとiOSターゲットのコンパイルが壊れる。
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)

        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            // supabase-kt/ktor-client-darwinのiOS klibはABI 2.3.0でビルドされており、
            // Kotlin 2.3系コンパイラで消費できるためcommonMainに置く
            // （HTTPエンジンはAndroid=okhttp（androidApp側で提供）/iOS=darwinを各所で注入）。
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            // RemoteAnalysisRunner用。エンジン自体はプラットフォーム側が既存どおり供給する
            // （androidApp=okhttp/iosMain=darwin。上のsupabase依存と同じ方針）。
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            // supabase-kt（ktor-client-core経由）のHTTPエンジンをiOS向けに提供。
            // Android側は androidApp/build.gradle.kts の ktor-client-okhttp が担う。
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "dev.miyado.shogisupplement.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
}
