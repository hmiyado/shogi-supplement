plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.sqldelightPlugin)
}

sqldelight {
    databases {
        create("ShogiSupplementDatabase") {
            packageName.set("dev.miyado.shogisupplement.db")
            // 新規作成スキーマ（CREATE TABLE群）と .sqm を積み上げてマイグレーションした
            // スキーマが一致することをビルド時に機械検証する（verifySqlDelightMigration*タスク）。
            // verifyMigrationsは比較対象のスキーマスナップショット（.db）の出力先が必要なため
            // schemaOutputDirectoryも合わせて指定する。
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}

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
        namespace = "dev.miyado.shogisupplement.shared"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
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
    //
    // engineless（GPL×App Store回避のためエンジン・評価関数を一切リンクしないフレーバー）:
    // `-PiosEngineless=true` を渡すと、この節のcinterop登録・engine系linkerOptsを
    // 一切追加しない。エンジン依存のiOSソース（UsiEngineInProcess/IosEngineHost）も
    // コンパイル対象から外す（下の iosMain.get().kotlin.srcDir 切り替え参照）。
    // 既定（フラグ無し）は従来どおりエンジンを同梱する＝この節の挙動は不変。
    val iosEngineless = (project.findProperty("iosEngineless") as? String).toBoolean()
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
        // Swift互換シム側（cryptography-kotlinのCryptoKit interop対策）はengineの有無と無関係
        // （ktor-client-darwin依存で発生するため、engineless時も変わらず必要）。
        // engine側（-lshogiengine等）だけをiosEnginelessで空にする。
        val swiftCompatLinkerOpts = listOf("-L$swiftCompatLibDir")
        val engineLinkerOpts = if (iosEngineless) {
            emptyList()
        } else {
            listOf("-L${engineLibDir.absolutePath}", "-lshogiengine", "-lc++")
        }

        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts.addAll(engineLinkerOpts + swiftCompatLinkerOpts)
        }
        // iosSimulatorArm64Test（回帰4系統の1つ）はmain+testを1つの実行体としてリンクする
        // ため、cinterop実体（libshogiengine.a）へのリンカフラグを test バイナリにも通す
        // （iosEngineless時はengineLinkerOptsが空なのでSwift互換シムのみ）。
        iosTarget.binaries.getTest("DEBUG").linkerOpts.addAll(engineLinkerOpts + swiftCompatLinkerOpts)

        // engineless時はcinterop自体を登録しない（wrapperシンボルへの依存を一切生成しない）。
        if (!iosEngineless) {
            iosTarget.compilations.getByName("main") {
                cinterops.create("engine_wrapper") {
                    defFile(project.file("src/nativeInterop/cinterop/engine_wrapper.def"))
                    packageName("dev.miyado.shogisupplement.engine.wrapper")
                    includeDirs(engineWrapperDir)
                }
            }
        }
    }

    sourceSets {
        androidMain.get().kotlin.srcDir(generateAndroidBuildNumber)

        commonMain.dependencies {
            implementation(project(":kifu"))
            implementation(project(":analysis"))
            // Workerと共有する通信DTO（dev.miyado.shogisupplement.api）。
            implementation(project(":contracts"))
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
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            // supabase-kt（ktor-client-core経由）のHTTPエンジンをiOS向けに提供。
            // Android側は androidApp/build.gradle.kts の ktor-client-okhttp が担う。
            implementation(libs.ktor.client.darwin)
        }

        // エンジン依存のiOSソース（UsiEngineInProcess/IosEngineHost。cinteropシンボルを参照する）は
        // src/iosMain 直下に置かず、フラグに応じて選ぶディレクトリ（iosEngineMain/
        // iosEnginelessMain）へ分離してある。iosMain は iosArm64/iosSimulatorArm64 共通の
        // 中間ソースセット（applyDefaultHierarchyTemplateが生成）のため、ここに srcDir を
        // 足せば両ターゲットに効く。どちらのディレクトリも IosEngineHost という同名・同一公開APIの
        // オブジェクトを提供するため、:ui iosMain 側は無変更でどちらのフレーバーでもコンパイルが通る
        // （engineless版は shared/src/iosEnginelessMain/.../IosEngineHost.kt 参照）。
        iosMain.get().kotlin.srcDir(
            if (iosEngineless) "src/iosEnginelessMain/kotlin" else "src/iosEngineMain/kotlin",
        )
    }
}

// jvmTestはリソースを複製せず、androidApp/src/main/assetsの正本からコピーする。
// Why not シンボリックリンク: ホストのOS/git設定によっては復元される保証がなく、
// CIや開発機の差でテストが壊れうるため。
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("jvmTestProcessResources") {
    from(rootProject.file("androidApp/src/main/assets/coefficients_hao_isolate_v1.json"))
}
