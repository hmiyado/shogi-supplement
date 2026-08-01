package dev.miyado.shogisupplement.kifu

/**
 * パース済みの1局。
 *
 * @property moves USI形式の指し手列（例: "7g7f", "P*2e", "4d8h+"）
 * @property timesSeconds 各手の消費時間（秒）。KIFに時間欄が無ければ null
 * @property headers KIFヘッダ（"先手" → "..." など）
 * @property endReason 終局語（"投了"/"切れ負け"/"時間切れ"/"千日手"/"持将棋"/"反則"等。棋譜末尾から取得。無ければ null）
 * @property winner 勝者（"sente"/"gote"/null。引き分け・不明は null）
 */
data class KifuGame(
    val moves: List<String>,
    val timesSeconds: List<Int?>,
    val headers: Map<String, String>,
    val endReason: String? = null,
    val winner: String? = null,
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
