package dev.miyado.shogisupplement.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class AnalysisEngineSelectionTest {

    @Test
    fun `サーバーが利用可能ならWASMフォールバック付きサーバー経路を選ぶ`() {
        assertEquals(
            AnalysisEngineRoute.REMOTE_WITH_WASM_FALLBACK,
            selectAnalysisEngineRoute(serverAvailable = true, nativeEngineAvailable = true),
        )
    }

    @Test
    fun `サーバーがなくネイティブエンジンがあればローカルネイティブ経路を選ぶ`() {
        assertEquals(
            AnalysisEngineRoute.LOCAL_NATIVE,
            selectAnalysisEngineRoute(serverAvailable = false, nativeEngineAvailable = true),
        )
    }

    @Test
    fun `サーバーもネイティブもなければローカルWASM経路を選ぶ`() {
        assertEquals(
            AnalysisEngineRoute.LOCAL_WASM,
            selectAnalysisEngineRoute(serverAvailable = false, nativeEngineAvailable = false),
        )
    }
}
