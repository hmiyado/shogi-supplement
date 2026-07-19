package dev.miyado.shogisupplement.judge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 係数表（data/coefficients_v1.json 準拠のスキーマ）。
 * アプリ同梱＋リモート更新可。versionを解析結果レコードに記録すること。
 */
@Serializable
data class CoefficientTable(
    val version: String,
    val built_from: BuiltFrom,
    val band_edges: List<Int>,
    val band_names: List<String>,
    val rates_per_1000_moves: Map<String, Map<String, Double>>,
    val mate_miss: Map<String, Map<String, MateMissCell>>,
) {
    @Serializable
    data class BuiltFrom(val games: Int, val moves: Int, val blunders: Int)

    @Serializable
    data class MateMissCell(val opportunities: Int, val misses: Int, val rate: Double)

    /** レート → (帯index, 帯名)。scripts/report_kifu.py band_of() と同一。 */
    fun bandOf(rating: Int): Pair<Int, String> {
        for (i in band_names.indices) {
            if (rating >= band_edges[i] && rating < band_edges[i + 1]) {
                return i to band_names[i]
            }
        }
        return band_names.lastIndex to band_names.last()
    }

    /** 帯×カテゴリの発生率（件/1000手）。無いセルは 0.0。 */
    fun ratePer1000(band: String, category: String): Double =
        rates_per_1000_moves[band]?.get(category) ?: 0.0

    /** 帯×詰み手数バケットの見逃し率。 */
    fun mateMissRate(band: String, bucket: String): Double? =
        mate_miss[band]?.get(bucket)?.rate

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): CoefficientTable = json.decodeFromString(text)
    }
}
