package dev.miyado.shogisupplement.pv

import dev.miyado.shogisupplement.board.ShogiBoard

/**
 * 読み筋のオンデマンド延長 — PV連結・合法手検証ユーティリティ。
 *
 * - concatenate: 既存PVに新たな手列を継ぎ足す
 * - isLegalFirstMove: sfenAtLineEnd 局面において firstMove が合法手かどうか検証
 */
object PvExtender {
    /**
     * 現在の PV に新たな手列を継ぎ足す。
     * @param currentPv 現在の best_pv（スペース区切り。null or blank = ゼロ）
     * @param newMoves  追加する手列
     * @return 連結済みの PV 文字列（スペース区切り）
     */
    fun concatenate(currentPv: String?, newMoves: List<String>): String {
        val existing = currentPv?.trim()?.takeIf { it.isNotBlank() } ?: ""
        val newPart = newMoves.joinToString(" ")
        return if (existing.isEmpty()) newPart else "$existing $newPart"
    }

    /**
     * sfenAtLineEnd 局面において firstMove が合法手かどうかを検証する。
     * パース・局面生成エラー時は false を返す（防御的）。
     */
    fun isLegalFirstMove(sfenAtLineEnd: String, firstMove: String): Boolean {
        return runCatching {
            val board = ShogiBoard.fromSfen(sfenAtLineEnd)
            board.legalMoves().any { it.toUsiString() == firstMove }
        }.getOrElse { false }
    }
}
