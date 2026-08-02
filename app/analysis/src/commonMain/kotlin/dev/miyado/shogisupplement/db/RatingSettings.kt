package dev.miyado.shogisupplement.db

/** レート設定の集約モデル。 */
data class RatingSettings(
    val rating: Int,
    val service: String,
    val ratingRaw: Int,
    val ratingRule: String?,
    val serviceAccountName: String?,
)
