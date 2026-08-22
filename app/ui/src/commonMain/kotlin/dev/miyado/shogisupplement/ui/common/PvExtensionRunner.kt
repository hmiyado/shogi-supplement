package dev.miyado.shogisupplement.ui.common

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.pv.PvExtender

/** 読み筋オンデマンド延長の状態。Errorは固定の未提供表示として扱う。 */
sealed class PvExtState {
    object Idle : PvExtState()
    object Loading : PvExtState()
    object Error : PvExtState()
}

/** 空PVまたは先頭手が非合法な延長失敗を表す内部例外。 */
internal class IllegalPvMoveException : Exception("not a legal move")

/** 読み筋延長、合法性検証、DB更新をまとめる共通ロジック。エンジンのライフサイクルはfactoryに委ねる。 */
internal object PvExtensionRunner {
    /** 指定局面からPVを延長して保存する。 @param blunderId 対象レコード。 @param sfenAtLineEnd ライン末尾のSFEN。 @param currentPvStr 保存済みPV。 @param repository 更新先。 @param engineFactory 解析用エンジンを生成するfactory。 @return 保存後のPV。 @throws IllegalPvMoveException PVが空または非合法な場合。 */
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
            // PV全体を再生し、最初の非合法手の手前で切り詰める。
            // 2手目以降の非合法手を保存すると、局面再生と棋譜表記が壊れるため。
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

    /** sfenAtLineEndから手列を再生し、最初の非合法手の手前で切り詰める。全手を検証する。 */
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
