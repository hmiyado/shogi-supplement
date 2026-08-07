package dev.miyado.shogisupplement.pipeline

import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 解析中の1セッション（ホーム一覧の解析中カード・解析中レポート画面の再接続で使う）。
 *
 * @param id セッションの識別子。棋譜内容のsha256Hex（[AnalysisOrchestrator]がDB保存に使う
 *   content_hashと同じ値）。同一棋譜の再解析（フォアグラウンド復帰の再送等）でも
 *   同じidを指すため、レジストリ上は上書きで自然に一本化される。
 */
data class InProgressAnalysis(
    val id: String,
    val fileName: String,
    val userSide: String?,
    val progressive: ProgressiveReportState,
)

/**
 * 解析中セッションのレジストリ（プロセス内・メモリのみ・DBには一切書かない）。
 *
 * 実行主体（Android=AnalysisService、iOS=IosMainController）だけが [start]/[updatePosition]/
 * [finish] を呼ぶ唯一の書き手。画面側（ホーム一覧・解析中レポート画面）は [sessions]/[snapshot]
 * を読むだけの購読者にする——ナビゲーション状態（画面のバック操作で破棄されるVM/State）の
 * ライフサイクルから解析の実行・進捗を切り離すのがこのクラスの役目。
 *
 * Why not DB: gameテーブルは analyzed_at・rating 等「解析完了後にしか決まらない値」が
 * 前提の完了済みレコード用スキーマで、解析中プレースホルダを入れるにはほぼ全列を
 * nullable化しクラッシュ時の孤児行掃除も要る。実行主体の生存期間＝解析の生存期間
 * （Androidはフォアグラウンドサービス、iOSはプロセス生存期間のCoroutineScope）と
 * 一致するため、メモリ保持で要件を満たせる。
 */
class InProgressAnalysisRegistry {
    private val _sessions = MutableStateFlow<Map<String, InProgressAnalysis>>(emptyMap())
    val sessions: StateFlow<Map<String, InProgressAnalysis>> = _sessions.asStateFlow()

    /** 解析開始。既に同じidがあれば初期状態で上書きする（フォアグラウンド復帰時の再送など）。 */
    fun start(id: String, fileName: String, moves: List<String>, userSide: String?) {
        _sessions.update { it + (id to InProgressAnalysis(id, fileName, userSide, ProgressiveReportState.initial(moves))) }
    }

    /** 局面ごとの中間結果を1件反映する。未登録のidは無視する（start前後の競合を無害化）。 */
    fun updatePosition(id: String, ply: Int, pvs: List<PvInfo>) {
        _sessions.update { cur ->
            val existing = cur[id] ?: return@update cur
            cur + (id to existing.copy(progressive = existing.progressive.withPosition(ply, pvs)))
        }
    }

    /** 解析終了（完了・失敗どちらも）。以後このidはホーム一覧・再接続の対象から消える。 */
    fun finish(id: String) {
        _sessions.update { it - id }
    }

    /** 再接続用の現在スナップショット。既に終了済み（=finish済み）ならnull。 */
    fun snapshot(id: String): InProgressAnalysis? = _sessions.value[id]

    companion object {
        /** アプリプロセス全体で共有する唯一のインスタンス（本番配線用）。テストは個別インスタンスを作る。 */
        val shared = InProgressAnalysisRegistry()
    }
}
