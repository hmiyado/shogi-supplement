@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.webApp.js

// docs/kento/webapp-bridge.js（window.kentoBridge）へのexternal宣言。
// Worker起動・fetch・JSONの相互運用はブリッジ側（プレーンJS）に閉じ込め、
// こちら側は文字列とコールバック関数だけを境界にする。

external interface CancelHandle : JsAny {
    fun cancel()
}

external interface StudyEngineHandle : JsAny {
    fun analyze(
        baseSfenArg: String,
        movesJson: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    )

    fun dispose()
}

external interface KentoBridge : JsAny {
    val variant: String

    fun fetchText(url: String, onOk: (String) -> Unit, onError: (String) -> Unit)

    fun resolveAssetDirUrl(assetBaseUrl: String, onOk: (String) -> Unit, onError: (String) -> Unit)

    fun checkAssetsAvailable(assetBaseUrl: String, onResult: (Boolean) -> Unit)

    fun goHome()

    fun runAnalysis(
        baseSfenArg: String,
        movesJson: String,
        assetDirUrl: String,
        onPosition: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ): CancelHandle

    fun createStudyEngine(assetDirUrl: String): StudyEngineHandle
}

@Suppress("UnsafeCastFromDynamic")
@JsFun("() => window.kentoBridge")
external fun kentoBridge(): KentoBridge
