package dev.miyado.shogisupplement.judge

import dev.miyado.shogisupplement.text.AppStrings

/** 相応判定の種別（表示文言は今後変わり得るため種別をenumで持つ）。 */
enum class VerdictKind(val symbol: String) {
    PRIORITY("◎"),   // 優先出題
    TARGET("○"),     // 出題対象
    SKIP("△"),       // 見送り（うっかり）
}

/** 相応判定の結果。 */
data class Judgement(
    val kind: VerdictKind,
    val verdict: String,
    val note: String,
    val problem: String,
    val priority: Double,
)

/** 判定入力: 悪手のカテゴリと（詰み見逃しの場合）逃した詰み手数。 */
data class JudgeInput(
    val category: String,
    val missedMateIn: Int? = null,
)

/**
 * 相応判定。
 *
 * - スイング系: 自帯率<2/1000 →△見送り ／ 最上位帯比≥4倍 →◎優先 ／ 他 →○出題対象
 * - 詰み見逃し: 帯別見逃し率 <5% →△ ／ 5〜60% →◎ ／ >60% →○（背伸び）
 */
object Judge {

    const val CATEGORY_MATE_MISS = "詰み見逃し"

    /**
     * ユーザー1局あたりの自分の手数の fallback 定数（55手）。
     * 「約N局に1回」= 1000 ÷ (件/1000手 × AVG_MOVES_PER_PLAYER_GAME)
     */
    const val AVG_MOVES_PER_PLAYER_GAME = 55

    /** 悪手カテゴリ → 教材名。文言の実体は [AppStrings.problemByCategory]。 */
    private val problemByCategory get() = AppStrings.problemByCategory

    /** 詰み手数 → 係数表のバケット名（1手/3手/5手/7手+）。 */
    fun mateBucket(mateIn: Int): String = when (mateIn) {
        1 -> "1手"
        3 -> "3手"
        5 -> "5手"
        else -> "7手+"
    }

    fun judge(input: JudgeInput, bandIndex: Int, coef: CoefficientTable): Judgement {
        val names = coef.band_names
        val band = names[bandIndex]
        val upper = names[minOf(bandIndex + 1, names.lastIndex)]

        if (input.category == CATEGORY_MATE_MISS && input.missedMateIn != null) {
            val bucket = mateBucket(input.missedMateIn)  // 係数表バケット（内部用）
            val rate = coef.mateMissRate(band, bucket) ?: 0.0
            val pct = "${(rate * 100).toInt()}%"
            // 表示は実手数 missedMateIn を使う（「11手詰の詰将棋」等）。バケットは表示しない。
            val problem = AppStrings.problemMate(input.missedMateIn)
            val note = AppStrings.noteMate(input.missedMateIn, pct)
            return when {
                rate < 0.05 -> Judgement(
                    VerdictKind.SKIP, AppStrings.VERDICT_SKIP,
                    note,
                    problem, rate,
                )
                rate <= 0.60 -> Judgement(
                    VerdictKind.PRIORITY, AppStrings.VERDICT_PRIORITY,
                    note,
                    problem, rate,
                )
                else -> Judgement(
                    VerdictKind.TARGET, AppStrings.VERDICT_TARGET,
                    note,
                    problem, rate,
                )
            }
        }

        val rBand = coef.ratePer1000(band, input.category)
        val rUpper = coef.ratePer1000(upper, input.category)
        val rTop = coef.ratePer1000(names.last(), input.category)
        val gradient = if (rTop > 0) rBand / rTop else Double.POSITIVE_INFINITY
        val problem = problemByCategory[input.category] ?: ""

        val nBand = gamesPerBlunder(rBand)
        val twoPointNote = AppStrings.noteTwoPoint(band, nBand)

        return when {
            rBand < 2.0 -> Judgement(
                VerdictKind.SKIP, AppStrings.VERDICT_SKIP,
                AppStrings.noteSkipRare(band, nBand),
                problem, 0.0,
            )
            gradient >= 4.0 -> Judgement(
                VerdictKind.PRIORITY, AppStrings.VERDICT_PRIORITY,
                twoPointNote,
                problem, gradient,
            )
            else -> Judgement(
                VerdictKind.TARGET, AppStrings.VERDICT_TARGET,
                twoPointNote,
                problem, gradient,
            )
        }
    }

    /**
     * 「約N局に1回」の N を計算する。
     * N = 1000 / (ratePer1000 × AVG_MOVES_PER_PLAYER_GAME)。最小値は1。
     */
    private fun gamesPerBlunder(ratePer1000: Double): Int {
        if (ratePer1000 <= 0.0) return 999
        return maxOf(1, kotlin.math.round(1000.0 / (ratePer1000 * AVG_MOVES_PER_PLAYER_GAME)).toInt())
    }
}
