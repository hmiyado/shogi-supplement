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

class DrillJudgeTest {

    private val initialSfen =
        "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"

    private fun sampleBlunder(
        sfenBefore: String = initialSfen,
        moveUsi: String = "B*3d",
        bestUsi: String? = "2f6f",
        cpBefore: Long? = null,
        secondUsi: String? = null,
        secondCp: Long? = null,
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
        cpBefore = cpBefore,
        secondUsi = secondUsi,
        secondCp = secondCp,
    )

    private class FakeEngine(private val scoreBySfen: Map<String, Score>) {
        val receivedSfens = mutableListOf<String>()

        fun analyze(sfen: String): List<PvInfo> {
            receivedSfens.add(sfen)
            val score = scoreBySfen[sfen]
                ?: error("unexpected sfen: $sfen")
            return listOf(PvInfo(multipv = 1, score = score, pv = emptyList(), nodes = 400_000L))
        }
    }


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
        assertEquals(listOf(initialSfen, sfenAfter7g7f), engine.receivedSfens)
        return result
    }

    @Test
    fun `エンジン判定 - loss_wpが公式どおり計算される`() {
        val result = engineJudge(bestCp = 300, afterCpOpponent = 0)
        val expected = BlunderJudge.winProb(300) - BlunderJudge.winProb(0)

        assertEquals(expected, result.lossWp, 1e-12)
        assertFalse(result.isCorrect) // 約0.122 > 0.05
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
    }

    @Test
    fun `エンジン判定 - 閾値0_05の境界（下側）は正解`() {
        val result = engineJudge(bestCp = 120, afterCpOpponent = 0)
        val expected = BlunderJudge.winProb(120) - BlunderJudge.winProb(0)

        assertTrue(expected <= DrillJudge.CORRECT_LOSS_WP_THRESHOLD, "test premise")
        assertEquals(expected, result.lossWp, 1e-12)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `エンジン判定 - 閾値0_05の境界（上側）は不正解`() {
        val result = engineJudge(bestCp = 121, afterCpOpponent = 0)
        val expected = BlunderJudge.winProb(121) - BlunderJudge.winProb(0)

        assertTrue(expected > DrillJudge.CORRECT_LOSS_WP_THRESHOLD, "test premise")
        assertEquals(expected, result.lossWp, 1e-12)
        assertFalse(result.isCorrect)
    }

    @Test
    fun `エンジン判定 - ユーザー手後のほうが良い場合はloss_wpが0に切り上げ`() {
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


    @Test
    fun `judgePrimary - pv2データが無ければUnavailable`() {
        val verdict = DrillJudge.judgePrimary(sampleBlunder(), userMoveUsi = "7g7f")
        assertEquals(DrillJudge.PrimaryVerdict.Unavailable, verdict)
    }

    @Test
    fun `judgePrimary - pv2一致かつ閾値内はCorrect`() {
        val blunder = sampleBlunder(cpBefore = 0L, secondUsi = "7g7f", secondCp = -120L)
        val verdict = DrillJudge.judgePrimary(blunder, userMoveUsi = "7g7f")

        val expected = BlunderJudge.winProb(120) - BlunderJudge.winProb(0)
        assertTrue(verdict is DrillJudge.PrimaryVerdict.Correct)
        assertEquals(expected, verdict.lossWp, 1e-12)
    }

    @Test
    fun `judgePrimary - pv2一致でも閾値超はIncorrect`() {
        val blunder = sampleBlunder(cpBefore = 0L, secondUsi = "7g7f", secondCp = -121L)
        val verdict = DrillJudge.judgePrimary(blunder, userMoveUsi = "7g7f")

        assertTrue(verdict is DrillJudge.PrimaryVerdict.Incorrect)
    }

    @Test
    fun `judgePrimary - top-2圏外でpv2のloss_wpが既に閾値超なら確定Incorrect`() {
        val blunder = sampleBlunder(cpBefore = 0L, secondUsi = "7g7f", secondCp = -121L)
        val verdict = DrillJudge.judgePrimary(blunder, userMoveUsi = "2g2f")

        assertTrue(verdict is DrillJudge.PrimaryVerdict.Incorrect)
    }

    @Test
    fun `judgePrimary - top-2圏外でpv2のloss_wpが閾値内ならAmbiguous`() {
        val blunder = sampleBlunder(cpBefore = 0L, secondUsi = "7g7f", secondCp = -120L)
        val verdict = DrillJudge.judgePrimary(blunder, userMoveUsi = "2g2f")

        assertEquals(DrillJudge.PrimaryVerdict.Ambiguous, verdict)
    }


    @Test
    fun `judge - pv2一致かつ閾値内は一次判定だけで正解確定しエンジンは呼ばれない`() {
        val engine = FakeEngine(emptyMap())
        val blunder = sampleBlunder(cpBefore = 0L, secondUsi = "7g7f", secondCp = -120L)
        val result = DrillJudge.judge(blunder, userMoveUsi = "7g7f", engineAnalyze = engine::analyze)

        assertTrue(result.isCorrect)
        assertEquals(DrillJudge.Reason.PRIMARY_MATCH_SECOND, result.reason)
        assertTrue(engine.receivedSfens.isEmpty(), "engine should not be called")
    }

    @Test
    fun `judge - top-2圏外で下界が既に閾値超なら一次判定だけで不正解確定しエンジンは呼ばれない`() {
        val engine = FakeEngine(emptyMap())
        val blunder = sampleBlunder(cpBefore = 0L, secondUsi = "7g7f", secondCp = -121L)
        val result = DrillJudge.judge(blunder, userMoveUsi = "2g2f", engineAnalyze = engine::analyze)

        assertFalse(result.isCorrect)
        assertEquals(DrillJudge.Reason.PRIMARY_OUT_OF_TOP2, result.reason)
        assertTrue(engine.receivedSfens.isEmpty(), "engine should not be called")
    }

    @Test
    fun `judge - 曖昧領域はエンジン未注入なら不正解フォールバック`() {
        val blunder = sampleBlunder(cpBefore = 0L, secondUsi = "7g7f", secondCp = -120L)
        val result = DrillJudge.judge(blunder, userMoveUsi = "2g2f", engineAnalyze = null)

        assertFalse(result.isCorrect)
        assertTrue(result.lossWp.isNaN())
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
    }

    @Test
    fun `judge - 一次判定がAmbiguousのときはエンジン注入時に二次判定へ委譲され実際に呼ばれる`() {
        val engine = FakeEngine(
            mapOf(
                initialSfen to Score.Cp(300),
                sfenAfter7g7f to Score.Cp(0),
            ),
        )
        val blunder = sampleBlunder(cpBefore = 0L, secondUsi = "3c3d", secondCp = -120L)
        val result = DrillJudge.judge(blunder, userMoveUsi = "7g7f", engineAnalyze = engine::analyze)

        assertEquals(listOf(initialSfen, sfenAfter7g7f), engine.receivedSfens)
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
        val expected = BlunderJudge.winProb(300) - BlunderJudge.winProb(0)
        assertEquals(expected, result.lossWp, 1e-12)
    }


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
