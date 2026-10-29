package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.pipeline.InProgressAnalysisRegistry

/** OS固有の通知や復帰処理から切り離した、解析セッションの入力。 */
data class AnalysisSession(
    val id: String,
    val fileName: String,
    val moves: List<String>,
    val userSide: String?,
    val requestId: String? = null,
)

/**
 * 解析の開始・局面進捗・終了を共通化する。
 * エンジン生成、通知、バックグラウンド復帰はホスト側へ残し、完了・失敗・キャンセルの
 * いずれでもレジストリを掃除するライフサイクルだけをここで保証する。
 */
class AnalysisSessionCoordinator(
    private val registry: InProgressAnalysisRegistry,
) {
    suspend fun run(
        session: AnalysisSession,
        analyze: suspend (onPositionResult: (Int, List<PvInfo>) -> Unit) -> AnalysisOrchestrator.Outcome,
        onPositionResult: (Int, List<PvInfo>) -> Unit = { _, _ -> },
    ): AnalysisOrchestrator.Outcome {
        val generation = registry.start(session.id, session.fileName, session.moves, session.userSide, session.requestId)
        return try {
            analyze { ply, pvs ->
                if (registry.updatePosition(session.id, generation, ply, pvs)) {
                    onPositionResult(ply, pvs)
                }
            }
        } finally {
            registry.finish(session.id, generation)
        }
    }
}
