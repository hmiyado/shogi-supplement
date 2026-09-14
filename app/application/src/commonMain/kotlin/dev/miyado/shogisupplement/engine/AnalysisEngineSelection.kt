package dev.miyado.shogisupplement.engine

/** 解析本体が利用する実行経路。OS固有のエンジン生成は各ホストに残す。 */
enum class AnalysisEngineRoute {
    REMOTE_WITH_WASM_FALLBACK,
    LOCAL_WASM,
    LOCAL_NATIVE,
}

/** サーバー・WASM・ネイティブの優先順位を両OSで共有する。 */
fun selectAnalysisEngineRoute(
    serverAvailable: Boolean,
    nativeEngineAvailable: Boolean,
): AnalysisEngineRoute = when {
    serverAvailable -> AnalysisEngineRoute.REMOTE_WITH_WASM_FALLBACK
    nativeEngineAvailable -> AnalysisEngineRoute.LOCAL_NATIVE
    else -> AnalysisEngineRoute.LOCAL_WASM
}
