package dev.miyado.shogisupplement.strength

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 20サンプルの生特徴量と予測レートのペアとの突合テスト。
 * [StrengthEstimator.predict] に同じ生特徴量を通し、Python側の凍結係数と数値的に
 * 一致することを検証する（許容誤差1e-4）。
 */
class EstimatorV2GoldenTest {

    @Serializable
    private data class GoldenRawFeatures(
        val pv1_match_rate: Double,
        val opening_mean_loss: Double,
        val own_log_rate: Double,
        val middle_mean_loss: Double,
        val max_lead_drop: Double,
        val mate_miss_rate1000: Double,
    )

    @Serializable
    private data class GoldenSample(
        val game_id: String,
        val side: String,
        val note: String = "",
        val n_moves: Int,
        val true_rating: Double,
        val raw_features: GoldenRawFeatures,
        val predicted_rating: Double,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadGolden(): List<GoldenSample> {
        val text = checkNotNull(
            javaClass.classLoader.getResourceAsStream("estimator_v2_golden.json"),
        ) { "estimator_v2_golden.json not found in test resources" }
            .readBytes().decodeToString()
        return json.decodeFromString(text)
    }

    @Test
    fun `20サンプルの生特徴量から予測レートが1e-4以内で一致する`() {
        val samples = loadGolden()
        assertTrue(samples.size == 20, "golden サンプル数は20のはず（実際=${samples.size}）")

        for (s in samples) {
            val features = RawFeaturesV2(
                pv1MatchRate = s.raw_features.pv1_match_rate,
                openingMeanLoss = s.raw_features.opening_mean_loss,
                ownLogRate = s.raw_features.own_log_rate,
                middleMeanLoss = s.raw_features.middle_mean_loss,
                maxLeadDrop = s.raw_features.max_lead_drop,
                mateMissRate1000 = s.raw_features.mate_miss_rate1000,
                nMoves = s.n_moves,
            )
            val predicted = StrengthEstimator.predict(features)
            val diff = kotlin.math.abs(predicted - s.predicted_rating)
            assertTrue(
                diff < 1e-4,
                "${s.game_id}/${s.side}: predicted=$predicted expected=${s.predicted_rating} diff=$diff",
            )
        }
    }
}
