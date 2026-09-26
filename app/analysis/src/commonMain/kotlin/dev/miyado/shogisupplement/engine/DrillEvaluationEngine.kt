package dev.miyado.shogisupplement.engine

/** ドリルの物差し専用。検討のノード数・MultiPVを判定側から指定させない。 */
fun interface DrillEvaluationEngine {
    fun analyze(sfen: String): List<PvInfo>
}

/** 起動時のThreads・Hash・評価関数は解析用factoryの不変条件を引き継ぐ。 */
class InvariantDrillEngine(private val engine: Engine) : DrillEvaluationEngine {
    override fun analyze(sfen: String): List<PvInfo> {
        // 常駐エンジンで直前の検討の置換表・探索履歴を引き継がない。
        engine.newGame()
        return engine.analyzeSfen(
            sfen = sfen,
            additionalMoves = emptyList(),
            nodes = EngineInvariants.DRILL_SECONDARY_NODES,
            multiPv = EngineInvariants.DRILL_SECONDARY_MULTI_PV,
        )
    }
}
