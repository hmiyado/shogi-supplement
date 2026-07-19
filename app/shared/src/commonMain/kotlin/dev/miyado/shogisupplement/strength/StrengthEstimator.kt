package dev.miyado.shogisupplement.strength

import dev.miyado.shogisupplement.judge.CoefficientTable
import kotlin.math.round

/**
 * 強さ指標の推定結果。
 *
 * @param rating lishogi 相当レート値
 * @param clamped クランプされたかどうか（上限/下限の区別）
 * @param errorMargin 表示用の誤差幅（±点）。集計対象手数から
 *   [StrengthEstimator.errorMarginFor] でルックアップする。
 *   手数を積んでも±560程度で頭打ちになるため、常時表示する。
 * @param totalMoves 推定に使った集計対象手数（自分の手のみ・今局＋過去累計）。
 *   rating は手数を積むほど収束するため、単独では意味が薄い。必ずこの値とセットで
 *   保存・比較すること（DB game.rating_sample_moves 参照）。
 */
data class StrengthEstimate(
    val rating: Int,
    val clamped: ClampState,
    val errorMargin: Int,
    val totalMoves: Int,
)

/** 推定値のクランプ状態。 */
enum class ClampState {
    /** クランプなし（補間範囲内）。 */
    NONE,

    /** 悪手率が最高帯よりさらに低い（最強側クランプ）。表示例: "77+ ±22"。 */
    CLAMPED_HIGH,

    /** 悪手率が最低帯よりさらに高い（最弱側クランプ）。表示例: "30未満 ±27"。 */
    CLAMPED_LOW,
}

/**
 * 偏差値換算の基準集団（norm v1）。
 *
 * 較正サンプル（lishogi レート対局者・プレイヤー単位 n=1880）のレート平均と標準偏差。
 * 偏差値はこの集団内での相対位置（平均50・SD10）。
 *
 * 内部推定はレート軸のまま維持し、表示直前でのみ換算する。推定自体を偏差値軸に
 * しない理由: アンカー・誤差幅・係数表がレート軸で較正済みで、換算は単調な線形写像
 * なので表示層で足りる。基準集団を差し替えるときは版を上げ、ヘルプの基準集団の
 * 説明も合わせて更新する。
 */
object StrengthNorm {
    const val VERSION = "v1"
    const val MEAN = 1666.0
    const val SD = 256.0

    /** レート値 → 偏差値（四捨五入）。 */
    fun deviationScore(rating: Int): Int = round(50.0 + 10.0 * (rating - MEAN) / SD).toInt()

    /** レート幅（±点）→ 偏差値幅（四捨五入）。 */
    fun deviationWidth(ratingPoints: Int): Int = round(10.0 * ratingPoints / SD).toInt()
}

/**
 * 実測悪手率（件/1000手）から lishogi 相当レートを推定する。
 *
 * アルゴリズム:
 * - CoefficientTable の帯別全カテゴリ悪手率合計をアンカーとして使う
 * - 帯中点（1150/1450/1750/2050/2350）をレート軸のアンカーに対応付け
 * - 悪手率は単調減少なので、実測値から逆引きして区分線形補間
 * - 範囲外はクランプし、[ClampState] で区別
 */
object StrengthEstimator {

    /**
     * 累計手数から表示用誤差幅（±点）をルックアップする（保守側丸め）。
     *
     * 復元抽出ブートストラップの90% half-width を保守側に丸めた値で、
     * 手数を積んでも±560程度で頭打ちになる（個人レベルの系統誤差が支配的なため）。
     *
     * 境界（累計手数）:
     *   〜300手   → ±700
     *   〜1000手  → ±650
     *   〜2000手  → ±600
     *   2000手〜  → ±560
     */
    internal fun errorMarginFor(totalMoves: Int): Int = when {
        totalMoves <= 300 -> 700
        totalMoves <= 1000 -> 650
        totalMoves <= 2000 -> 600
        else -> 560
    }

    /**
     * 各帯のアンカー（レート中点, 帯別全カテゴリ悪手率合計）。
     * 悪手率は高レート帯ほど小さい（単調減少）ため、rateDesc 降順に並べる。
     *
     * 値は data/coefficients_hao_v1.json から算出（CoefficientsHaoTest でスポットチェック済み）。
     */
    internal data class Anchor(val ratingMidpoint: Int, val totalRatePer1000: Double)

    /**
     * CoefficientTable から帯中点・帯別悪手率合計のアンカーリストを構築する。
     *
     * 帯中点の定義:
     *   <1300       → 1150
     *   1300-1599   → 1450
     *   1600-1899   → 1750
     *   1900-2199   → 2050
     *   2200+       → 2350
     */
    private val BAND_MIDPOINTS = mapOf(
        "<1300" to 1150,
        "1300-1599" to 1450,
        "1600-1899" to 1750,
        "1900-2199" to 2050,
        "2200+" to 2350,
    )

    /**
     * [CoefficientTable] からアンカーリストを構築する（悪手率降順 = レート昇順）。
     */
    internal fun buildAnchors(table: CoefficientTable): List<Anchor> {
        return table.band_names.mapNotNull { band ->
            val midpoint = BAND_MIDPOINTS[band] ?: return@mapNotNull null
            val totalRate = table.rates_per_1000_moves[band]?.values?.sum() ?: 0.0
            Anchor(midpoint, totalRate)
        }.sortedByDescending { it.totalRatePer1000 }  // 悪手率降順 = 低レートから高レートの順
    }

    /**
     * 実測悪手率（件/1000手）と集計対象手数から強さ指標を推定する。
     *
     * @param observedRatePer1000 実測悪手率（件/1000手）
     * @param totalMoves 集計対象手数（自分の手のみ）
     * @param table 係数表（帯別悪手率アンカーの源泉）
     */
    fun estimate(
        observedRatePer1000: Double,
        totalMoves: Int,
        table: CoefficientTable,
    ): StrengthEstimate {
        val anchors = buildAnchors(table)
        val errorMargin = errorMarginFor(totalMoves)

        // アンカー最高帯（最小悪手率 = 高レート端）
        val highAnchor = anchors.last()   // 悪手率が最も小さい = 高レート
        // アンカー最低帯（最大悪手率 = 低レート端）
        val lowAnchor = anchors.first()   // 悪手率が最も大きい = 低レート

        // クランプ: 実測悪手率がアンカー範囲外
        if (observedRatePer1000 <= highAnchor.totalRatePer1000) {
            return StrengthEstimate(highAnchor.ratingMidpoint, ClampState.CLAMPED_HIGH, errorMargin, totalMoves)
        }
        if (observedRatePer1000 >= lowAnchor.totalRatePer1000) {
            return StrengthEstimate(lowAnchor.ratingMidpoint, ClampState.CLAMPED_LOW, errorMargin, totalMoves)
        }

        // 区分線形補間: anchors は悪手率降順（低レート→高レート）
        // 実測悪手率が [anchors[i].rate, anchors[i-1].rate] の範囲に入る区間を探す
        for (i in 1 until anchors.size) {
            val lo = anchors[i]      // 悪手率が小さい = 高レート側
            val hi = anchors[i - 1] // 悪手率が大きい = 低レート側
            if (observedRatePer1000 in lo.totalRatePer1000..hi.totalRatePer1000) {
                // hi.rate が大きく lo.rate が小さい（rate 降順軸）
                val t = (hi.totalRatePer1000 - observedRatePer1000) /
                    (hi.totalRatePer1000 - lo.totalRatePer1000)
                val rating = (hi.ratingMidpoint + t * (lo.ratingMidpoint - hi.ratingMidpoint)).toInt()
                return StrengthEstimate(rating, ClampState.NONE, errorMargin, totalMoves)
            }
        }

        // ここには到達しないはずだが安全のため高レート側クランプ
        return StrengthEstimate(highAnchor.ratingMidpoint, ClampState.CLAMPED_HIGH, errorMargin, totalMoves)
    }
}

/**
 * 強さ指標を表示用文字列（偏差値・norm v1換算）に変換する。
 *
 * 常に誤差幅「±NN」を付けて表示する形式。単位ラベル（「偏差値」）は付けない——
 * カードタイトルや接頭文言が単位を持つため、値側に重ねると冗長になる。
 * 例: "51 ±25" / "77+ ±22" / "30未満 ±27"
 */
fun StrengthEstimate.toDisplayString(): String {
    val dev = StrengthNorm.deviationScore(rating)
    val width = StrengthNorm.deviationWidth(errorMargin)
    val base = when (clamped) {
        ClampState.CLAMPED_HIGH -> "${dev}+"
        ClampState.CLAMPED_LOW -> "${dev}未満"
        ClampState.NONE -> "$dev"
    }
    return "$base ±$width"
}
