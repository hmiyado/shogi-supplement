package dev.miyado.shogisupplement.engine

// 解析条件の不変条件。端末解析と完全一致させることが golden パリティテスト（S0）の前提のため、
// ここを変更しないこと。
object EngineInvariants {
    const val NODES: Int = Engine.DEFAULT_NODES
    const val MULTI_PV: Int = Engine.MULTI_PV
    const val THREADS: Int = 1
    const val USI_HASH_MB: Int = 128
    const val FV_SCALE: Int = 20
}
