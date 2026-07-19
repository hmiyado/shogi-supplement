package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.engine.PvInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DrillJudge の単体テスト（fake エンジン注入）。
 *
 * 検証範囲:
 * - best_usi 一致 → 即正解（エンジン不要）
 * - 実戦悪手一致 → 即不正解（エンジン不要）
 * - エンジン判定: loss_wp = winProb(best) - winProb(-after) と閾値 0.05 の境界
 * - エンジン無し・不正な指し手のフォールバック（不正解）
 */
class DrillJudgeTest {

    private val initialSfen =
        "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"

    private fun sampleBlunder(
        sfenBefore: String = initialSfen,
        moveUsi: String = "B*3d",
        bestUsi: String? = "2f6f",
    ) = BlunderRecord(
        id = 1L,
        gameId = 1L,
        ply = 41L,
        side = "sente",
        moveUsi = moveUsi,
        bestUsi = bestUsi,
        lossWp = 0.225,
        sfenBefore = sfenBefore,
        category = "駒損（タクティクス）",
        diffMaterial = -11L,
        punishChecks = 0L,
        tookMovedPiece = false,
        missedMateIn = null,
        verdict = "○ 出題対象",
        note = "自帯6.3件/1000手 (上帯5.2件)。帯として典型的なミス",
        problemType = "手筋 (両取り・素抜き) の問題",
        priority = 2.9978349024480666,
    )

    /** 呼び出し記録つき fake エンジン。sfen ごとに返す評価値を指定する。 */
    private class FakeEngine(private val scoreBySfen: Map<String, Score>) {
        val receivedSfens = mutableListOf<String>()

        fun analyze(sfen: String): List<PvInfo> {
            receivedSfens.add(sfen)
            val score = scoreBySfen[sfen]
                ?: error("unexpected sfen: $sfen")
            return listOf(PvInfo(multipv = 1, score = score, pv = emptyList(), nodes = 400_000L))
        }
    }

    // ─── 即判定（エンジン不要） ───────────────────────────────────────────────

    @Test
    fun `best_usiと一致したら即正解でエンジンは呼ばれない`() {
        val engine = FakeEngine(emptyMap())
        val result = DrillJudge.judge(sampleBlunder(), userMoveUsi = "2f6f", engineAnalyze = engine::analyze)

        assertTrue(result.isCorrect)
        assertEquals(0.0, result.lossWp, 1e-12)
        assertEquals(DrillJudge.Reason.MATCH_BEST, result.reason)
        assertTrue(engine.receivedSfens.isEmpty(), "engine should not be called")
    }

    @Test
    fun `実戦の悪手と同じ手は即不正解でエンジンは呼ばれない`() {
        val engine = FakeEngine(emptyMap())
        val result = DrillJudge.judge(sampleBlunder(), userMoveUsi = "B*3d", engineAnalyze = engine::analyze)

        assertFalse(result.isCorrect)
        assertEquals(0.225, result.lossWp, 1e-12)
        assertEquals(DrillJudge.Reason.MATCH_ACTUAL_BLUNDER, result.reason)
        assertTrue(engine.receivedSfens.isEmpty(), "engine should not be called")
    }

    // ─── エンジン判定 ─────────────────────────────────────────────────────────

    /** 初期局面から 7g7f を指した後の SFEN（GameRepositoryTest と同じ既知値）。 */
    private val sfenAfter7g7f =
        "lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 2"

    private fun engineJudge(bestCp: Int, afterCpOpponent: Int): DrillJudge.DrillResult {
        val engine = FakeEngine(
            mapOf(
                initialSfen to Score.Cp(bestCp),
                sfenAfter7g7f to Score.Cp(afterCpOpponent),
            ),
        )
        val result = DrillJudge.judge(sampleBlunder(), userMoveUsi = "7g7f", engineAnalyze = engine::analyze)
        // 出題局面 → ユーザー手後局面 の順で2回解析される
        assertEquals(listOf(initialSfen, sfenAfter7g7f), engine.receivedSfens)
        return result
    }

    @Test
    fun `エンジン判定 - loss_wpが公式どおり計算される`() {
        // best=+300、ユーザー手後は相手番視点0（互角）→ loss = winProb(300) - 0.5
        val result = engineJudge(bestCp = 300, afterCpOpponent = 0)
        val expected = BlunderJudge.winProb(300) - BlunderJudge.winProb(0)

        assertEquals(expected, result.lossWp, 1e-12)
        assertFalse(result.isCorrect) // 約0.122 > 0.05
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
    }

    @Test
    fun `エンジン判定 - 閾値0_05の境界（下側）は正解`() {
        // SIGMOID_SCALE=600 では winProb(120)-winProb(0) ≈ 0.0498 ≤ 0.05
        val result = engineJudge(bestCp = 120, afterCpOpponent = 0)
        val expected = BlunderJudge.winProb(120) - BlunderJudge.winProb(0)

        assertTrue(expected <= DrillJudge.CORRECT_LOSS_WP_THRESHOLD, "test premise")
        assertEquals(expected, result.lossWp, 1e-12)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `エンジン判定 - 閾値0_05の境界（上側）は不正解`() {
        // winProb(121)-winProb(0) ≈ 0.0502 > 0.05
        val result = engineJudge(bestCp = 121, afterCpOpponent = 0)
        val expected = BlunderJudge.winProb(121) - BlunderJudge.winProb(0)

        assertTrue(expected > DrillJudge.CORRECT_LOSS_WP_THRESHOLD, "test premise")
        assertEquals(expected, result.lossWp, 1e-12)
        assertFalse(result.isCorrect)
    }

    @Test
    fun `エンジン判定 - ユーザー手後のほうが良い場合はloss_wpが0に切り上げ`() {
        // best=0（互角）だがユーザー手後に相手番視点-200（ユーザー優勢）→ 負のlossは0へ
        val result = engineJudge(bestCp = 0, afterCpOpponent = -200)

        assertEquals(0.0, result.lossWp, 1e-12)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `エンジン判定 - 相手にmateが出る手は不正解`() {
        val engine = FakeEngine(
            mapOf(
                initialSfen to Score.Cp(100),
                sfenAfter7g7f to Score.Mate(5), // 相手番視点: 相手が5手詰めで勝ち
            ),
        )
        val result = DrillJudge.judge(sampleBlunder(), userMoveUsi = "7g7f", engineAnalyze = engine::analyze)

        assertFalse(result.isCorrect)
        assertTrue(result.lossWp > DrillJudge.CORRECT_LOSS_WP_THRESHOLD)
    }

    // ─── フォールバック ───────────────────────────────────────────────────────

    @Test
    fun `エンジン無しで即判定できない手は不正解`() {
        val result = DrillJudge.judge(sampleBlunder(), userMoveUsi = "7g7f", engineAnalyze = null)

        assertFalse(result.isCorrect)
        assertTrue(result.lossWp.isNaN())
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
    }

    @Test
    fun `不正なUSI文字列は例外にならず不正解`() {
        val engine = FakeEngine(mapOf(initialSfen to Score.Cp(100)))
        val result = DrillJudge.judge(sampleBlunder(), userMoveUsi = "xx", engineAnalyze = engine::analyze)

        assertFalse(result.isCorrect)
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
    }

    @Test
    fun `best_usiがnullでも実戦悪手一致は不正解になる`() {
        val result = DrillJudge.judge(
            sampleBlunder(bestUsi = null),
            userMoveUsi = "B*3d",
            engineAnalyze = null,
        )

        assertFalse(result.isCorrect)
        assertEquals(DrillJudge.Reason.MATCH_ACTUAL_BLUNDER, result.reason)
        assertNull(result.bestMoveUsi)
    }
}
