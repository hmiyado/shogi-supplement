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

private val DECISIVE_END_REASONS = setOf("投了", "切れ負け", "時間切れ", "反則負け", "詰み", "反則")

/**
 * 終局理由と手数から勝者を算出する。投了・切れ負け・時間切れ・詰み等は手数パリティで確定、
 * 引き分け系はnull。KIF全文が無くても`headers`/`result`列だけから呼べる。
 */
fun kifuWinner(endReason: String?, moveCount: Int): String? =
    if (endReason in DECISIVE_END_REASONS) {
        // moveCount手目まで指された後、次は (moveCount % 2 == 0) なら sente (1手目が sente)
        if (moveCount % 2 == 0) "gote" else "sente"
    } else {
        null
    }
