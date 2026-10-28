package dev.miyado.shogisupplement.strength

enum class MateOutcome { FOUND, MISSED, UNKNOWN }

data class MateOpportunity(
    val plies: Int,
    val outcome: MateOutcome,
)

data class MateBucket(
    val plies: Int,
    val found: Int,
    val missed: Int,
    val unknown: Int = 0,
) {
    val decided: Int get() = found + missed

    val successRate: Double? get() = if (decided == 0) null else found.toDouble() / decided
}

data class MateStatistics(
    val buckets: List<MateBucket> = emptyList(),
    val analyzedGames: Int = 0,
    val verifiedGames: Int = 0,
) {
    companion object {
        const val LAST_BUCKET = 31

        fun aggregate(
            opportunities: List<MateOpportunity>,
            analyzedGames: Int,
            verifiedGames: Int,
        ): MateStatistics {
            if (opportunities.isEmpty()) {
                return MateStatistics(emptyList(), analyzedGames, verifiedGames)
            }
            val maximum = opportunities.maxOf { bucketOf(it.plies) }
            val buckets = (1..maximum step 2).map { plies ->
                var found = 0
                var missed = 0
                var unknown = 0
                for (opportunity in opportunities) {
                    if (bucketOf(opportunity.plies) != plies) continue
                    when (opportunity.outcome) {
                        MateOutcome.FOUND -> found++
                        MateOutcome.MISSED -> missed++
                        MateOutcome.UNKNOWN -> unknown++
                    }
                }
                MateBucket(plies = plies, found = found, missed = missed, unknown = unknown)
            }
            return MateStatistics(buckets = buckets, analyzedGames = analyzedGames, verifiedGames = verifiedGames)
        }

        private fun bucketOf(plies: Int): Int = when {
            plies >= LAST_BUCKET -> LAST_BUCKET
            plies <= 1 -> 1
            plies % 2 == 1 -> plies
            else -> plies - 1
        }
    }
}
