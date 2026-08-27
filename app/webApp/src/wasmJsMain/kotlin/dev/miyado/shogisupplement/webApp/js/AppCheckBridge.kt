@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.miyado.shogisupplement.webApp.js

// docs/kento/appcheck-bridge.js（window.appCheckBridge）へのexternal宣言。
// Firebase JS SDKの相互運用はブリッジ側（プレーンJS）に閉じ込め、こちら側は
// 文字列とコールバック関数だけを境界にする（KentoBridge.ktと同じ方針）。

external interface AppCheckBridge : JsAny {
    fun getToken(onOk: (String) -> Unit, onError: (String) -> Unit)
}

@Suppress("UnsafeCastFromDynamic")
@JsFun("() => window.appCheckBridge")
external fun appCheckBridge(): AppCheckBridge

/** [dev.miyado.shogisupplement.transfer.RemoteTransferRestoreService]のappCheckTokenProviderに渡す。 */
suspend fun fetchAppCheckToken(): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    appCheckBridge().getToken(
        onOk = { cont.resume(it, onCancellation = null) },
        onError = { cont.resume(null, onCancellation = null) },
    )
}
