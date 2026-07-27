package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.engine.PvInfo

/**
 * 端末エンジン版の二次判定。既存の [DrillJudge.judge] のエンジン評価パス（judgeByEngine）を
 * そのまま移設したもので、挙動は変更していない（出題局面とユーザー手後局面の2回を解析する）。
 *
 * @param engineAnalyze エンジン解析関数 `(sfen: String) -> List<PvInfo>`。エンジンの
 *   起動/破棄ライフサイクルはホスト側の責務（Android=判定ごとにプロセス起動/破棄、
 *   iOS=常駐エンジンを使い回す。DrillScreenHost.kt/DrillDemoFactory.ios.kt 参照）。
 */
class EngineDrillSecondaryJudge(
    private val engineAnalyze: (sfen: String) -> List<PvInfo>,
) : DrillSecondaryJudge {
    override suspend fun judge(blunder: BlunderRecord, userMoveUsi: String): DrillJudge.DrillResult =
        DrillJudge.judge(blunder, userMoveUsi, engineAnalyze = engineAnalyze)
}
