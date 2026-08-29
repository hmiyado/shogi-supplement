package dev.miyado.shogisupplement.ui.common

// KIF「持ち時間」ヘッダの表記は対局サービスごとに固有の呼び名と対応する
// （例: 棋桜の「10分+30秒」はカジュアルと真剣勝負の両方に使われ数値だけでは
// 区別できないため意図的にラベルを付けない）。ここに無い組み合わせは
// ヘッダの原文をそのまま表示する。
private val KIOU_LABELS: Map<String, String> = mapOf(
    "3分切れ負け" to "ショート",
    "5分+5秒追加" to "フィッシャー",
)

// lishogiの「N分+M秒」はフィッシャー（1手ごとの加算）ではなく秒読みとして扱う
// （フィッシャールールは棋桜のみが持つ概念）。
private val LISHOGI_INCREMENT_HEADER = Regex("""^(\d+分)\+(\d+秒)$""")

private val BYOYOMI_ONLY_HEADER = Regex("""^\d+秒$""")

/**
 * 持ち時間の表示ラベルと表示テキストの対。[label]が非nullなら「ラベル（テキスト）」の
 * 形式で表示する。[sourcePlace]は対局サービス（[dev.miyado.shogisupplement.kifu.KifuSource.wireValue]）、
 * [main]はKIF「持ち時間」ヘッダの原文、[byoyomi]は「秒読み」ヘッダの原文。
 */
fun resolveTimeControlDisplay(sourcePlace: String?, main: String, byoyomi: String?): Pair<String?, String> {
    // KifParserはヘッダ値をtrimせずそのまま保持するため、コロン直後に空白を
    // 挟む表記のKIFでも一致判定できるようここでtrimする。
    val trimmedMain = main.trim()
    val trimmedByoyomi = byoyomi?.trim()
    // 基本時間0分＋「N秒」形式の秒読みは対局サービス共通で「1手N秒」と呼ぶ。
    // 秒読み値が想定外の形式なら誤解を招く表示を避け、通常の判定へフォールバックする。
    if (trimmedMain == "0分" && trimmedByoyomi != null && BYOYOMI_ONLY_HEADER.matches(trimmedByoyomi)) {
        return null to "1手$trimmedByoyomi"
    }
    return when (sourcePlace) {
        "kiou" -> (KIOU_LABELS[trimmedMain] ?: null) to trimmedMain
        "lishogi" -> {
            val m = LISHOGI_INCREMENT_HEADER.find(trimmedMain)
            if (m != null) null to "${m.groupValues[1]}秒読み${m.groupValues[2]}" else null to trimmedMain
        }
        else -> null to trimmedMain
    }
}
