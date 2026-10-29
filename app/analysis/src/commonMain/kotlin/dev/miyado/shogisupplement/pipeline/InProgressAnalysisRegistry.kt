package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 解析中セッション。 @param id 棋譜内容から得た識別子。同じIDの再登録は上書きする。 */
data class InProgressAnalysis(
    val id: String,
    internal val generation: Any = Any(),
    val fileName: String,
    val userSide: String?,
    val progressive: ProgressiveReportState,
    val requestId: String? = null,
)

/**
 * 解析中セッションをプロセス内メモリで保持するレジストリ。
 * Why not DB: 完了前の値を保存するためにスキーマをnullable化し、孤児行を掃除する必要があるため。
 * 実行主体の生存期間と解析期間が一致するため、メモリ保持で足りる。
 */
class InProgressAnalysisRegistry {
    private val _sessions = MutableStateFlow<Map<String, InProgressAnalysis>>(emptyMap())
    val sessions: StateFlow<Map<String, InProgressAnalysis>> = _sessions.asStateFlow()

    /** 解析開始。既に同じidがあれば初期状態で上書きする（フォアグラウンド復帰時の再送など）。 */
    fun start(id: String, fileName: String, moves: List<String>, userSide: String?, requestId: String? = null): Any {
        val generation = Any()
        _sessions.update {
            it + (id to InProgressAnalysis(id, generation, fileName, userSide, ProgressiveReportState.initial(moves), requestId))
        }
        return generation
    }

    /** 局面ごとの中間結果を1件反映する。未登録のidは無視する（start前後の競合を無害化）。 */
    fun updatePosition(id: String, ply: Int, pvs: List<PvInfo>) {
        val generation = _sessions.value[id]?.generation ?: return
        updatePosition(id, generation, ply, pvs)
    }

    /** 世代が一致するセッションだけを更新する。旧解析の遅延コールバックを遮断する。 */
    fun updatePosition(id: String, generation: Any, ply: Int, pvs: List<PvInfo>): Boolean {
        while (true) {
            val cur = _sessions.value
            val existing = cur[id] ?: return false
            if (existing.generation !== generation) return false
            val updated = cur + (id to existing.copy(progressive = existing.progressive.withPosition(ply, pvs)))
            // CASの再試行中に世代が変わった場合も、実際に保存できた更新だけを受理する。
            if (_sessions.compareAndSet(cur, updated)) return true
        }
    }

    /** 解析終了（完了・失敗どちらも）。以後このidはホーム一覧・再接続の対象から消える。 */
    fun finish(id: String) {
        _sessions.update { it - id }
    }

    /** 世代が一致するセッションだけを終了する。後から始まった解析を消さない。 */
    fun finish(id: String, generation: Any) {
        _sessions.update { cur ->
            if (cur[id]?.generation === generation) cur - id else cur
        }
    }

    /** 再接続用の現在スナップショット。既に終了済み（=finish済み）ならnull。 */
    fun snapshot(id: String): InProgressAnalysis? = _sessions.value[id]

    companion object {
        /** アプリプロセス全体で共有する唯一のインスタンス（本番配線用）。テストは個別インスタンスを作る。 */
        val shared = InProgressAnalysisRegistry()
    }
}
