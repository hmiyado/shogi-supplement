package dev.miyado.shogisupplement.kifu

/** パース済みの1局。 @property moves USI手列。 @property timesSeconds 各手の消費秒。 @property headers KIFヘッダ。 @property endReason 終局理由。 @property winner 勝者。 @property displayMoves 原文の手表記。 */
data class KifuGame(
    val moves: List<String>,
    val timesSeconds: List<Int?>,
    val headers: Map<String, String>,
    val endReason: String? = null,
    val winner: String? = null,
    val displayMoves: List<String> = emptyList(),
) {
    val senteName: String? get() = headers["先手"]
    val goteName: String? get() = headers["後手"]
}

/** 棋譜パースの失敗（平手以外・不正な指し手行など）。 */
class KifuParseException(message: String, val line: String? = null) :
    Exception(if (line != null) "$message: $line" else message)

/** KIF / CSA 共通のパーサinterface。CSA実装は後続フェーズ。 */
interface KifuParser {
    /** @throws KifuParseException 平手以外、または解釈できない指し手があった場合 */
    fun parse(text: String): KifuGame
}
