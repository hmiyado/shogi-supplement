package dev.miyado.shogisupplement.db

/** レート設定の集約モデル。 */
data class RatingSettings(
    val rating: Int,
    val service: String,
    val ratingRaw: Int,
    val ratingRule: String?,
    val serviceAccountName: String?,
)

/** 対局時点で有効だった申告棋力の履歴。 */
data class RatingDeclaration(
    val service: String?,
    val ratingRaw: Int?,
    val ratingRule: String?,
    val declaredAt: Long,
)
