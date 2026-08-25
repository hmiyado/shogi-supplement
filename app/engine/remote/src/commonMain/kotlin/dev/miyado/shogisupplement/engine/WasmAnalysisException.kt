package dev.miyado.shogisupplement.engine

/**
 * WKWebView×WASM解析（`WasmAnalysisRunner`。shared/src/iosMain参照）の失敗。
 * ホスト未初期化・エンジンWASMバイナリ取得失敗（オフライン等）・WKWebView異常終了等をまとめて表す。
 */
class WasmAnalysisException(message: String) : Exception(message)
