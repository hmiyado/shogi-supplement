package dev.miyado.shogisupplement.text

/** Historical database values and generated file names. Changing these requires a data migration. */
object StoredText {
    const val unverifiedMateNote = "詰み手数は未確認です"

    const val VERDICT_PRIORITY = "◎ 優先出題"

    const val VERDICT_TARGET = "○ 出題対象"

    const val VERDICT_SKIP = "△ 見送り"

    val problemByCategory: Map<String, String> = mapOf(
        "頓死" to "受け・危険察知の問題",
        "駒損（即取り）" to "駒の利き・紐の確認問題",
        "駒損（タクティクス）" to "手筋 (両取り・素抜き) の問題",
        "玉の危険（寄せ）" to "玉の早逃げ・受けの問題",
        "位置的・その他" to "形勢判断・大局観 (要人力レビュー)",
    )

    fun problemMate(n: Int): String = "${n}手で勝ち切る問題"

    fun noteMate(n: Int, pct: String): String =
        "あなたの棋力帯の${n}手詰の詰め逃し率: $pct"

    val bandDeviationLabels: Map<String, String> = mapOf(
        "<1300" to "偏差値36未満",
        "1300-1599" to "偏差値36-47",
        "1600-1899" to "偏差値47-59",
        "1900-2199" to "偏差値59-71",
        "2200+" to "偏差値71+",
    )

    private fun bandLabel(bandName: String): String =
        bandDeviationLabels[bandName] ?: bandName

    fun noteTwoPoint(bandName: String, gamesPerBand: Int): String =
        "あなたの棋力帯(${bandLabel(bandName)}): 約${gamesPerBand}局に1回"

    fun noteSkipRare(bandName: String, gamesPerBand: Int): String =
        "あなたの棋力帯(${bandLabel(bandName)}): 約${gamesPerBand}局に1回"

    fun clipboardFileName(dateStr: String): String = "クリップボード $dateStr"

    fun restoredGameFileName(startedAt: String?): String = startedAt ?: "復元した棋譜"

}
