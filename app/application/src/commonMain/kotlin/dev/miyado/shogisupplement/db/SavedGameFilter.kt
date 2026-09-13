package dev.miyado.shogisupplement.db

import kotlinx.serialization.Serializable

/** 棋譜一覧の保存条件。期間は絶対時刻ではなく比較幅（日数）で保持する。 */
@Serializable
data class SavedGameFilter(
    val name: String,
    val source: String? = null,
    val userSide: String? = null,
    val result: String? = null,
    val openingStyle: String? = null,
    val timeControl: String? = null,
    val periodDays: Int = DEFAULT_PERIOD_DAYS,
) {
    /** 期間を除いた条件。期間比較の両側へ共通で適用する。 */
    fun conditionFilter(): GameListFilter = GameListFilter(
        source = source,
        userSide = userSide,
        result = result?.let { runCatching { GameResultFilter.valueOf(it) }.getOrNull() },
        openingStyle = openingStyle,
        timeControl = timeControl,
    )

    /** 現在期間の絞り込み条件。 */
    fun currentFilter(now: Long): GameListFilter = conditionFilter().copy(
        dateFrom = now - periodDays.coerceAtLeast(1) * SECONDS_PER_DAY,
    )

    companion object {
        const val DEFAULT_PERIOD_DAYS = 30
        private const val SECONDS_PER_DAY = 24L * 60 * 60

        /** UIの編集中条件から保存形式へ変換する。期間なしは30日比較にする。 */
        fun fromFilter(name: String, filter: GameListFilter, now: Long): SavedGameFilter {
            val dateFrom = filter.dateFrom
            val periodDays = when {
                dateFrom != null && now - dateFrom <= 8 * SECONDS_PER_DAY -> 7
                else -> DEFAULT_PERIOD_DAYS
            }
            return SavedGameFilter(
                name = name,
                source = filter.source,
                userSide = filter.userSide,
                result = filter.result?.name,
                openingStyle = filter.openingStyle,
                timeControl = filter.timeControl,
                periodDays = periodDays,
            )
        }
    }
}
