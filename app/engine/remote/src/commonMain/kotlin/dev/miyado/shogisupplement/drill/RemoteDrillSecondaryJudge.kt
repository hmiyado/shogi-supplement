package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * サーバー版の二次判定。
 * Why not 出題局面を再解析しない: 保存済みcpBeforeを使えば通信を1回にできる。
 * @param analyzePosition 単発局面解析関数。
 */
class RemoteDrillSecondaryJudge(
    private val analyzePosition: suspend (sfen: String) -> List<PvInfo>,
) : DrillSecondaryJudge {
    override suspend fun judge(blunder: BlunderRecord, userMoveUsi: String): DrillJudge.DrillResult {
        val cpBefore = blunder.cpBefore
            ?: return unavailable(blunder, userMoveUsi)

        val sfenAfterUser = try {
            val board = ShogiBoard.fromSfen(blunder.sfenBefore)
            board.push(ShogiMove.fromUsi(userMoveUsi))
            board.toSfen()
        } catch (e: Exception) {
            return unavailable(blunder, userMoveUsi)
        }

        val pvAfterUser = analyzePosition(sfenAfterUser)
        val afterScore = pvAfterUser.firstOrNull()?.score
            ?: return unavailable(blunder, userMoveUsi)

        val wpBest = BlunderJudge.winProb(cpBefore.toInt())
        val wpAfterUser = BlunderJudge.winProb(-BlunderJudge.toCp(afterScore))
        val lossWp = (wpBest - wpAfterUser).coerceAtLeast(0.0)
        val continuationPv = pvAfterUser.firstOrNull()?.pv
            ?.joinToString(" ")?.takeIf { it.isNotBlank() }

        return DrillJudge.DrillResult(
            isCorrect = lossWp <= DrillJudge.CORRECT_LOSS_WP_THRESHOLD,
            lossWp = lossWp,
            userMoveUsi = userMoveUsi,
            bestMoveUsi = blunder.bestUsi,
            reason = DrillJudge.Reason.ENGINE_EVAL,
            pv = continuationPv,
        )
    }

    private fun unavailable(blunder: BlunderRecord, userMoveUsi: String) = DrillJudge.DrillResult(
        isCorrect = false,
        lossWp = Double.NaN,
        userMoveUsi = userMoveUsi,
        bestMoveUsi = blunder.bestUsi,
        reason = DrillJudge.Reason.ENGINE_EVAL,
    )
}
