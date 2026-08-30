package dev.miyado.shogisupplement.kifu

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

/** 判定表で解決できたラベルと表示テキストの対。表に無い組み合わせはnull。 */
private fun matchTimeControlRule(sourcePlace: String?, main: String, byoyomi: String?): Pair<String?, String>? {
    // KifParserはヘッダ値をtrimしないため、コロン直後に空白を挟む表記でも一致するようここでtrimする。
    val trimmedMain = main.trim()
    val trimmedByoyomi = byoyomi?.trim()
    // 基本時間0分＋「N秒」の秒読みはサービス共通で「1手N秒」。想定外の秒読み値はサービス別判定へ落とす。
    if (trimmedMain == "0分" && trimmedByoyomi != null && BYOYOMI_ONLY_HEADER.matches(trimmedByoyomi)) {
        return null to "1手$trimmedByoyomi"
    }
    return when (sourcePlace) {
        "kiou" -> KIOU_LABELS[trimmedMain]?.let { it to trimmedMain }
        "lishogi" -> LISHOGI_INCREMENT_HEADER.find(trimmedMain)
            ?.let { null to "${it.groupValues[1]}秒読み${it.groupValues[2]}" }
        else -> null
    }
}

/**
 * 持ち時間の表示ラベルと表示テキストの対。ラベルが非nullなら「ラベル（テキスト）」の形式で表示する。
 * [sourcePlace]は対局サービス（[KifuSource.wireValue]）、[main]は「持ち時間」、[byoyomi]は「秒読み」の原文。
 * 判定表に無い組み合わせは原文をそのまま表示テキストにする。
 */
fun resolveTimeControlDisplay(sourcePlace: String?, main: String, byoyomi: String?): Pair<String?, String> =
    matchTimeControlRule(sourcePlace, main, byoyomi) ?: (null to main.trim())

/** 判定表で解決できた持ち時間かどうか。falseなら表示はKIFヘッダの原文のまま。 */
fun isKnownTimeControlRule(sourcePlace: String?, mainRaw: String?, byoyomiRaw: String?): Boolean =
    !mainRaw.isNullOrBlank() && matchTimeControlRule(sourcePlace, mainRaw, byoyomiRaw) != null

/** 持ち時間の表示文字列。ラベルがあれば「ラベル（テキスト）」に合成する。[mainRaw]が空欄ならnull。 */
fun timeControlDisplayText(sourcePlace: String?, mainRaw: String?, byoyomiRaw: String?): String? {
    if (mainRaw.isNullOrBlank()) return null
    val (label, displayText) = resolveTimeControlDisplay(sourcePlace, mainRaw, byoyomiRaw)
    return if (label != null) "$label（$displayText）" else displayText
}
