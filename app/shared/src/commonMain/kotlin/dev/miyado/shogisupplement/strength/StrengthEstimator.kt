package dev.miyado.shogisupplement.strength

import kotlin.math.round
import kotlin.math.roundToInt

/**
 * 強さ指標の推定結果。
 *
 * @param rating lishogi 相当レート値
 * @param clamped クランプされたかどうか（上限/下限の区別）
 * @param errorMargin 表示用の誤差幅（±点）。集計対象手数から
 *   [StrengthEstimator.errorMarginFor] でルックアップする。
 *   手数を積んでも±560程度で頭打ちになるため、常時表示する。
 * @param totalMoves 推定に使った集計対象手数（自分の手のみ）。
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
    /** クランプなし（線形式は範囲を持たないため、v2推定では常にこれになる）。 */
    NONE,

    /** 悪手率が最高帯よりさらに低い（最強側クランプ）。表示例: "77+ ±22"。 */
    CLAMPED_HIGH,

    /** 悪手率が最低帯よりさらに高い（最弱側クランプ）。表示例: "30未満 ±27"。 */
    CLAMPED_LOW,
}

/**
 * 偏差値換算の基準集団（norm v2）。
 *
 * 較正サンプル（lishogi レート対局者・プレイヤー単位 n=1880）の推定器v2予測値の
 * 平均と標準偏差（research/data/estimator_v2_spec.json の strength_norm_v2）。
 * 偏差値はこの集団内での相対位置（平均50・SD10）。
 *
 * 内部推定はレート軸のまま維持し、表示直前でのみ換算する。推定自体を偏差値軸に
 * しない理由: アンカー・誤差幅・係数表がレート軸で較正済みで、換算は単調な線形写像
 * なので表示層で足りる。基準集団を差し替えるときは版を上げ、ヘルプの基準集団の
 * 説明も合わせて更新する。
 */
object StrengthNorm {
    const val VERSION = "v2"
    const val MEAN = 1718.0852941473634
    const val SD = 61.43099285964464

    /** レート値 → 偏差値（四捨五入）。 */
    fun deviationScore(rating: Int): Int = round(50.0 + 10.0 * (rating - MEAN) / SD).toInt()

    /** レート幅（±点）→ 偏差値幅（四捨五入）。 */
    fun deviationWidth(ratingPoints: Int): Int = round(10.0 * ratingPoints / SD).toInt()
}

/**
 * 実測特徴量（[RawFeaturesV2]）から lishogi 相当レートを推定する（推定器v2・G-sparse線形式）。
 *
 * アルゴリズム: rating = intercept + Σ standardized_coefficient_i × (raw_i − mean_i) / sd_i。
 * 係数・標準化パラメータは research/data/estimator_v2_spec.json の凍結値
 * （nested CV MAE ≈162.5、隣接帯以内95.1%で検証済み）をそのまま埋め込む。
 * 欠損特徴量（null）は標準化平均を代入する——標準化後の値が0になり、モデルへの寄与が
 * ちょうどゼロになるため、欠損を「集団の平均的な手」として扱うのと同じ効果になる。
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
     * 帯割り当ての境界（推奨: mapped_sample。research/data/estimator_v2_spec.json の
     * band_assignment.recommended_full_edges）。
     *
     * v2予測には平均への縮小（regression-to-the-mean）があるため、生の帯境界
     * {1300,1600,1900,2200} をそのまま使うと極端帯にほぼ割り当てられなくなる。
     * 1局単位（サンプルレベル）のOOF予測分布を基準にした写像境界を使うことで、
     * 相応判定（帯別係数表の参照）の入力粒度と整合させる。
     */
    private val BAND_EDGES = listOf(
        0.0, 1604.272205993674, 1702.7952271705592, 1801.3182483474445, 1899.8412695243296, 99999.0,
    )

    /**
     * レート → 帯index（0-4）。[BAND_EDGES] による区分線形の帯判定。
     * 下限(0.0)未満・上限(99999.0)以上は最寄りの端の帯に丸める
     * （v2予測は理論上どちらの方向にも値域を持たないため、範囲外は無帯扱いにせず既存の
     * 最弱/最強帯に含めるのが Judge の入力として自然なため）。
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
            // 線形式は値域を持たないため、v1のアンカー補間のようなクランプ概念が無い。
            clamped = ClampState.NONE,
            errorMargin = errorMarginFor(features.nMoves),
            totalMoves = features.nMoves,
        )
    }

    /**
     * 複数局の推定値（それぞれ [estimate] で計算済みの game.rating）を平均して
     * 表示用の強さ指標を組み立てる。
     *
     * 推定器v2は1局単位の予測のため、複数局の集約は特徴量を積み上げて再度線形式を
     * 通すのではなく、既に確定した各局のレートを平均する（誤差が特徴量側で相殺し合う
     * 複雑な合成より、確定値の単純平均の方が挙動を説明しやすいため）。
     *
     * @param ratings 集約対象の各局のレート（[estimate] の結果を保存したもの）
     * @param totalMoves 誤差幅表示用の集計対象手数（通常は対象局の自分の手数合計）
     */
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

/**
 * 強さ指標を表示用文字列（偏差値・norm v2換算）に変換する。
 *
 * 常に誤差幅「±NN」を付けて表示する形式。単位ラベル（「偏差値」）は付けない——
 * カードタイトルや接頭文言が単位を持つため、値側に重ねると冗長になる。
 * 例: "51 ±25"
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
