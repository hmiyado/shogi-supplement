package dev.miyado.shogisupplement.strength

import kotlin.math.round
import kotlin.math.roundToInt

/** 強さ指標の推定結果。 @param rating 推定レート。 @param clamped 上下限へのクランプ状態。 @param errorMargin 表示用誤差幅。 @param totalMoves 推定対象の手数。 */
data class StrengthEstimate(
    val rating: Int,
    val clamped: ClampState,
    val errorMargin: Int,
    val totalMoves: Int,
)

/** 推定値のクランプ状態。 */
enum class ClampState {
    /** クランプなし（線形式には範囲がないため常にこの状態になる）。 */
    NONE,

    /** 悪手率が最高帯よりさらに低い（最強側クランプ）。表示例: "77+ ±22"。 */
    CLAMPED_HIGH,

    /** 悪手率が最低帯よりさらに高い（最弱側クランプ）。表示例: "30未満 ±27"。 */
    CLAMPED_LOW,
}

/**
 * 偏差値換算の基準集団（norm v2）。
 * 平均50・SD10の較正集団を基準に、表示時だけレートを換算する。
 * 推定をレート軸に保つのは、係数表と誤差幅がその軸で較正されているため。
 */
object StrengthNorm {
    const val VERSION = "v2"
    const val MEAN = 1718.0852941473634
    const val SD = 61.43099285964464

    /** 真レート分布のSD。誤差幅を偏差値へ換算するときに必要とする。 */
    const val TRUE_SCALE_SD = 256.0

    /** レート値 → 偏差値（四捨五入）。 */
    fun deviationScore(rating: Int): Int = round(50.0 + 10.0 * (rating - MEAN) / SD).toInt()

    /** レート幅を偏差値幅へ換算する。真レート軸のSDを使い、予測分布のSDは使わない。 */
    fun deviationWidth(ratingPoints: Int): Int = round(10.0 * ratingPoints / TRUE_SCALE_SD).toInt()
}

/**
 * 実測特徴量（[RawFeaturesV2]）から lishogi 相当レートを推定する（推定器v2・G-sparse線形式）。
 *
 * rating = intercept + Σ standardized_coefficient_i × (raw_i − mean_i) / sd_i。
 * 欠損特徴量（null）は標準化平均を代入する: 標準化後の値が0になり、モデルへの寄与が
 * ちょうどゼロになる（欠損を「集団の平均的な手」として扱うのと同じ効果）。
 */
object StrengthEstimator {

    /** 累計手数から表示用誤差幅を保守側に丸めて返す。個人差を考慮して上限を設ける。 */
    internal fun errorMarginFor(totalMoves: Int): Int = when {
        totalMoves <= 300 -> 290
        else -> 280
    }

    private const val INTERCEPT = 1724.495669870962

    /** 標準化パラメータ・係数（mean, sd, standardized_coefficient）。 */
    private data class FeatureSpec(val mean: Double, val sd: Double, val coef: Double)

    private val PV1_MATCH_RATE = FeatureSpec(0.37513600002764885, 0.10291743610223654, 61.087094275459)
    private val OPENING_MEAN_LOSS = FeatureSpec(0.022990356941248426, 0.016643172469514186, -27.239917864105276)
    private val OWN_LOG_RATE = FeatureSpec(3.26777933587524, 1.8304010455240363, -17.376580561580642)
    private val MIDDLE_MEAN_LOSS = FeatureSpec(0.050515917597618025, 0.03942328013644469, -5.91195639252834)
    private val MAX_LEAD_DROP = FeatureSpec(0.48867553849484713, 0.2920569613264152, -7.804273777934729)
    private val MATE_MISS_RATE1000 = FeatureSpec(0.21691152308117567, 2.1429366329114954, -4.2010898745613146)

    private fun contribution(spec: FeatureSpec, raw: Double?): Double {
        val value = raw ?: spec.mean
        return spec.coef * (value - spec.mean) / spec.sd
    }

    /** 線形式の適用のみ行う（丸めなし）。ゴールデン突合テストで直接使う。 */
    fun predict(features: RawFeaturesV2): Double =
        INTERCEPT +
            contribution(PV1_MATCH_RATE, features.pv1MatchRate) +
            contribution(OPENING_MEAN_LOSS, features.openingMeanLoss) +
            contribution(OWN_LOG_RATE, features.ownLogRate) +
            contribution(MIDDLE_MEAN_LOSS, features.middleMeanLoss) +
            contribution(MAX_LEAD_DROP, features.maxLeadDrop) +
            contribution(MATE_MISS_RATE1000, features.mateMissRate1000)

    /**
     * 帯割り当ての境界。v2予測には平均への縮小（regression-to-the-mean）があるため、
     * 生の帯境界{1300,1600,1900,2200}をそのまま使うと極端帯にほぼ割り当てられなくなる。
     * そのためOOF予測分布を基準にした写像境界を使う。
     */
    private val BAND_EDGES = listOf(
        0.0, 1604.272205993674, 1702.7952271705592, 1801.3182483474445, 1899.8412695243296, 99999.0,
    )

    /**
     * レート → 帯index（0-4）。範囲外は最寄りの端の帯に丸める
     * （無帯扱いにせず最弱/最強帯に含めるのが Judge の入力として自然なため）。
     */
    fun bandIndex(rating: Double): Int {
        if (rating < BAND_EDGES[0]) return 0
        for (i in 0 until BAND_EDGES.size - 1) {
            if (rating >= BAND_EDGES[i] && rating < BAND_EDGES[i + 1]) return i
        }
        return BAND_EDGES.size - 2
    }

    /**
     * 特徴量から強さ指標を推定する。
     *
     * @param features [FeatureExtractorV2.extract] で計算した生特徴量
     */
    fun estimate(features: RawFeaturesV2): StrengthEstimate {
        val rating = predict(features)
        return StrengthEstimate(
            rating = rating.roundToInt(),
            clamped = ClampState.NONE,
            errorMargin = errorMarginFor(features.nMoves),
            totalMoves = features.nMoves,
        )
    }

    /** 複数局の推定レートを平均して強さ指標を組み立てる。局単位の結果を平均する。 */
    fun aggregate(ratings: List<Int>, totalMoves: Int): StrengthEstimate {
        require(ratings.isNotEmpty()) { "ratings must not be empty" }
        val avgRating = ratings.sumOf { it }.toDouble() / ratings.size
        return StrengthEstimate(
            rating = avgRating.roundToInt(),
            clamped = ClampState.NONE,
            errorMargin = errorMarginFor(totalMoves),
            totalMoves = totalMoves,
        )
    }
}

/** 強さ指標を偏差値と誤差幅の表示用文字列へ変換する。 */
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
