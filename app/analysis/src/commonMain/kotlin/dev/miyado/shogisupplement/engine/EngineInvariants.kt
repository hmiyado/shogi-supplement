package dev.miyado.shogisupplement.engine

// 解析条件の不変条件。端末解析と完全一致させることが golden パリティテスト（S0）の前提のため、
// ここを変更しないこと。
object EngineInvariants {
    const val NODES: Int = Engine.DEFAULT_NODES
    const val MULTI_PV: Int = Engine.MULTI_PV
    /** ドリル二次判定は保存済み解析と同じ物差しを使う。検討モードの条件とは分離する。 */
    const val DRILL_SECONDARY_NODES: Int = NODES
    const val DRILL_SECONDARY_MULTI_PV: Int = MULTI_PV
    const val THREADS: Int = 1
    const val USI_HASH_MB: Int = 128
    const val FV_SCALE: Int = 20
}
