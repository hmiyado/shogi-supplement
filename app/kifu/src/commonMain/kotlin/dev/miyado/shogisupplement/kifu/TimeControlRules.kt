package dev.miyado.shogisupplement.kifu

// 対局サービスごとの持ち時間（KIF「持ち時間」ヘッダの原文）と呼び名。呼び名がnullなのは、
// ヘッダの表記がそのまま呼び名のもの（切れ負け各種）と、棋桜の「10分+30秒」のように
// カジュアルと真剣勝負の両方に使われて区別できないもの。ここに無い組み合わせは判定しない
// ——将棋ウォーズの「10秒将棋」は0分＋秒読み10秒で共通判定に当たるため、将棋クエストは
// KIFのメタデータから判定できるルールが無いため、それぞれ載せていない。
private val SERVICE_TIME_CONTROLS: Map<String, Map<String, String?>> = mapOf(
    KifuSource.KIOU.wireValue to mapOf(
        "3分切れ負け" to "ショート",
        "5分+5秒追加" to "フィッシャー",
        "10分+30秒" to null,
    ),
    KifuSource.WARS.wireValue to mapOf(
        "10分切れ負け" to null,
        "3分切れ負け" to null,
    ),
)

// lishogiの「N分+M秒」は加算（フィッシャー）ではなく秒読みとして表示する決まり。
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
    val table = SERVICE_TIME_CONTROLS[sourcePlace]
    if (table != null && trimmedMain in table) return table[trimmedMain] to trimmedMain
    if (sourcePlace == KifuSource.LISHOGI.wireValue) {
        return LISHOGI_INCREMENT_HEADER.find(trimmedMain)
            ?.let { null to "${it.groupValues[1]}秒読み${it.groupValues[2]}" }
    }
    return null
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
