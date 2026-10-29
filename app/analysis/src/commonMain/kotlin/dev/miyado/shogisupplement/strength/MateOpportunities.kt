package dev.miyado.shogisupplement.strength

import dev.miyado.shogisupplement.db.PositionEvalRow

object MateOpportunities {

    private val terminalEndReasons = setOf("投了", "詰み")

    fun compute(
        moves: List<String>,
        evaluations: List<PositionEvalRow>,
        userSide: String?,
        gameWinner: String?,
        endReason: String?,
    ): List<MateOpportunity> {
        if (userSide != "sente" && userSide != "gote") return emptyList()
        val byPly = evaluations.groupBy { it.ply }
        val result = mutableListOf<MateOpportunity>()
        var run: Run? = null

        for (ply in 0..moves.size) {
            val active = run
            if (active == null) {
                if (ply < moves.size && sideAt(ply) == userSide) {
                    val row = byPly[ply]?.singleOrNull()
                    val start = row?.let { startPlies(it, ply, userSide) }
                    if (start != null) {
                        run = Run(plies = start)
                    }
                }
                continue
            }

            val rows = byPly[ply]
            if (rows == null || rows.size != 1) {
                active.unknown = true
                continue
            }

            when (stateOf(rows.single(), ply, userSide)) {
                MateState.POSITIVE -> Unit
                MateState.OPPONENT_MATE, MateState.CP_GONE, MateState.UNVERIFIED_MATE -> {
                    result += MateOpportunity(active.plies, if (active.unknown) MateOutcome.UNKNOWN else MateOutcome.MISSED)
                    run = null
                }
                MateState.UNKNOWN -> active.unknown = true
            }
        }

        run?.let { active ->
            val outcome = if (!active.unknown && gameWinner == userSide && endReason in terminalEndReasons) {
                MateOutcome.FOUND
            } else {
                MateOutcome.UNKNOWN
            }
            result += MateOpportunity(active.plies, outcome)
        }

        return result
    }

    private fun startPlies(row: PositionEvalRow, ply: Int, userSide: String): Int? {
        val mateIn = row.mateIn ?: return null
        if (!row.mateVerified) return null
        val view = mateView(mateIn, ply, userSide)
        if (!view.userMates) return null
        return view.plies.takeIf { it > 0 && it % 2 == 1 }
    }

    private fun stateOf(row: PositionEvalRow, ply: Int, userSide: String): MateState {
        val mateIn = row.mateIn
        if (mateIn == null) {
            return if (row.scoreCp != null) MateState.CP_GONE else MateState.UNKNOWN
        }
        if (!row.mateVerified) return MateState.UNVERIFIED_MATE
        return if (mateView(mateIn, ply, userSide).userMates) MateState.POSITIVE else MateState.OPPONENT_MATE
    }

    private fun mateView(mateIn: Int, ply: Int, userSide: String): MateView {
        if (mateIn == 0) {
            val userToMove = (userSide == "sente") == (ply % 2 == 0)
            return MateView(userMates = !userToMove, plies = 0)
        }
        val userValue = if (userSide == "gote") -mateIn else mateIn
        return MateView(userMates = userValue > 0, plies = kotlin.math.abs(userValue))
    }

    private fun sideAt(ply: Int): String = if (ply % 2 == 0) "sente" else "gote"

    private data class Run(val plies: Int, var unknown: Boolean = false)

    private data class MateView(val userMates: Boolean, val plies: Int)

    private enum class MateState { POSITIVE, OPPONENT_MATE, CP_GONE, UNVERIFIED_MATE, UNKNOWN }
}
