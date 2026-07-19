package dev.miyado.shogisupplement.ui.common

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.pv.PvExtender

/**
 * 読み筋オンデマンド延長の状態。ReportViewModel・DrillViewModel の両方から使う共用型。
 *
 * Error は表示上「（—）」の固定プレースホルダーである（詳細メッセージは表示しない。
 * DESIGN.md No-jitter原則により計器行自体を持たないため）。message フィールドは持たない
 * （原因の区別が必要になれば追加すること）。
 */
sealed class PvExtState {
    object Idle : PvExtState()
    object Loading : PvExtState()
    object Error : PvExtState()
}

/**
 * 読み筋のオンデマンド延長で使う内部例外。エンジンが空PVを返した場合・先頭手が非合法な
 * 場合の両方をこの型で表す（呼び出し元はどちらも PvExtState.Error への遷移として扱うため、
 * 区別する必要がない）。
 */
internal class IllegalPvMoveException : Exception("not a legal move")

/**
 * 読み筋のオンデマンド延長 — エンジン呼び出し〜合法性検証〜DB更新の共通ロジック。
 *
 * ReportViewModel（レポート画面・最善の変化タブ）と DrillViewModel（ドリル結果画面・
 * 最善タブ）の両方から使う協力オブジェクト。エンジンのライフサイクル（生成・quit）は
 * 呼び出し元が渡す engineFactory に委ねる（Android=使い捨てプロセス、iOS=常駐エンジンを
 * 生かしたまま quit を no-op にする委譲ラッパー。IosEngineHost.studyEngineFactory 参照）。
 */
internal object PvExtensionRunner {
    /**
     * 指定局面からエンジンで1手先を解析し、既存 PV に継ぎ足して DB へ保存する。
     *
     * @param blunderId      延長対象の blunder_report.id
     * @param sfenAtLineEnd  ライン末尾局面の SFEN
     * @param currentPvStr   現在の best_pv 文字列（null = 未保存）
     * @param repository     DB更新先
     * @param engineFactory  解析に使う都度生成のエンジン
     * @return 連結後の best_pv 文字列（DB更新済み）
     * @throws IllegalPvMoveException エンジンが空PVを返した、または先頭手が非合法だった場合
     */
    fun extend(
        blunderId: Long,
        sfenAtLineEnd: String,
        currentPvStr: String?,
        repository: GameRepository,
        engineFactory: () -> Engine,
    ): String {
        val engine = engineFactory()
        try {
            val pv1 = engine.analyzeSfen(sfenAtLineEnd).firstOrNull()?.pv ?: emptyList()
            if (pv1.isEmpty()) throw IllegalPvMoveException()
            if (!PvExtender.isLegalFirstMove(sfenAtLineEnd, pv1.first())) {
                throw IllegalPvMoveException()
            }
            // PvExtender.isLegalFirstMove は pv1 の先頭手しか合法性を検証しない。
            // エンジンが詰み後などに継続手を返すと2手目以降が非合法になることが
            // あり、非合法な手が best_pv に混入すると computeSfenAtStep
            // （ReportScreen.kt）の局面再生が非合法手で暗黙に止まらず、
            // buildCurrentMoveLabel の JapaneseNotation.format が「駒がない」
            // 例外で失敗して生USI（例:「42手目 2d2e」）へフォールバックする
            // （読み筋延長で追記された手のタブ末尾表示に現れる）。それを避けるため、
            // pv1 全体を sfenAtLineEnd から順に再生し、最初の非合法手の手前で
            // 切り詰めることで best_pv を常に sfenBefore から完全に合法な
            // 手列に保つ。
            val legalPv1 = truncateToLegalPrefix(sfenAtLineEnd, pv1)
            if (legalPv1.isEmpty()) {
                throw IllegalPvMoveException()
            }
            val concatenated = PvExtender.concatenate(currentPvStr, legalPv1)
            repository.updateBestPv(blunderId, concatenated)
            return concatenated
        } finally {
            engine.quit()
        }
    }

    /**
     * sfenAtLineEnd から moves を順に再生し、最初の非合法手の手前で切り詰めた
     * 手列を返す（moves 全体が合法なら moves をそのまま返す）。
     * PvExtender.isLegalFirstMove は先頭手しか検証しないため、延長PVの2手目以降に
     * 非合法な手が混じる根本原因への対策としてここで全手を検証する。
     */
    private fun truncateToLegalPrefix(sfenAtLineEnd: String, moves: List<String>): List<String> {
        val board = runCatching { ShogiBoard.fromSfen(sfenAtLineEnd) }.getOrNull() ?: return emptyList()
        val legal = mutableListOf<String>()
        for (usi in moves) {
            val move = runCatching { ShogiMove.fromUsi(usi) }.getOrNull() ?: break
            val isLegal = runCatching { board.legalMoves().any { it.toUsiString() == usi } }.getOrDefault(false)
            if (!isLegal) break
            runCatching { board.push(move) }.getOrElse { break }
            legal.add(usi)
        }
        return legal
    }
}
