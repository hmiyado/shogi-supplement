package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.db.BlunderRecord

/** 一次判定で確定できないドリルを二次判定する注入インターフェース。 */
fun interface DrillSecondaryJudge {
    suspend fun judge(blunder: BlunderRecord, userMoveUsi: String): DrillJudge.DrillResult
}
