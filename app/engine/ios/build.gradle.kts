plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    // engine_wrapper.h をcinterop経由でiosMainに公開する。実体
    // （libshogiengine.a = wrapper.cpp + libyaneuraou.a をbuild_ios.shがマージしたもの）は
    // ターゲットごとに出力先が異なる（iosSimulatorArm64=engine/build/sim、
    // iosArm64=engine/build/device）ため、linkerOptsでターゲット別に -L/-l を通す。
    // 注意（実測で確認済み）:
    // - テスト実行バイナリはmain+testを1つの「実行体」としてリンクするため、cinterop実体への
    //   リンカフラグが必須（無いとwrapperシンボル未解決でリンク失敗する）。
    // - 一方、静的framework（:uiのSharedUi）は最終リンクを消費側に委ねるため、
    //   iosApp（Xcode）側の OTHER_LDFLAGS/LIBRARY_SEARCH_PATHS でも -lshogiengine を通す
    //   （iosApp/project.yml参照）。
    //
    // engineless（GPL×App Store回避のためエンジン・評価関数を一切リンクしないフレーバー）:
    // `-PiosEngineless=true` を渡すと、cinterop登録・engine系linkerOptsを一切追加せず、
    // エンジン依存のソース（UsiEngineInProcess/IosEngineHost）もコンパイル対象から外す。
    val iosEngineless = (project.findProperty("iosEngineless") as? String).toBoolean()
    val engineWrapperDir = rootProject.projectDir.resolve("iosApp/engine/wrapper")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        val engineLibDir = rootProject.projectDir.resolve(
            "iosApp/engine/build/" + if (iosTarget.name == "iosArm64") "device" else "sim"
        )
        val engineLinkerOpts = if (iosEngineless) {
            emptyList()
        } else {
            listOf("-L${engineLibDir.absolutePath}", "-lshogiengine", "-lc++")
        }
        iosTarget.binaries.getTest("DEBUG").linkerOpts.addAll(engineLinkerOpts)

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

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // Engine interface・Score・係数表・Logger。
            api(project(":analysis"))
            // WASM解析の結果パーサ・例外（サーバー解析と同じ形式を読む）。
            implementation(project(":engine:remote"))
        }

        // エンジン依存のソース（UsiEngineInProcess/IosEngineHost。cinteropシンボルを参照する）は
        // src/iosMain 直下に置かず、フラグに応じて選ぶディレクトリへ分離してある。
        // どちらのディレクトリも IosEngineHost という同名・同一公開APIのオブジェクトを提供する。
        iosMain.get().kotlin.srcDir(
            if (iosEngineless) "src/iosEnginelessMain/kotlin" else "src/iosEngineMain/kotlin",
        )
    }
}
