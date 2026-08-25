package dev.miyado.shogisupplement.drill

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.engine.PvInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [EngineDrillSecondaryJudge] / [RemoteDrillSecondaryJudge] の単体テスト。
 *
 * 両実装とも二次判定（曖昧領域）専用なので、best_usi/実戦悪手一致は経由しない
 * （それらは DrillJudge.judge のステップ1/2で既に処理済みという前提）。
 */
class DrillSecondaryJudgeTest {

    private val initialSfen =
        "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
    private val sfenAfter7g7f =
        "lnsgkgsnl/1r5b1/ppppppppp/9/9/2P6/PP1PPPPPP/1B5R1/LNSGKGSNL w - 2"

    private fun sampleBlunder(cpBefore: Long? = null) = BlunderRecord(
        id = 1L,
        gameId = 1L,
        ply = 41L,
        side = "sente",
        moveUsi = "B*3d",
        bestUsi = "2f6f",
        lossWp = 0.225,
        sfenBefore = initialSfen,
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
    )

    // ─── EngineDrillSecondaryJudge ────────────────────────────────────────

    @Test
    fun `EngineDrillSecondaryJudge - 出題局面とユーザー手後局面の2回を解析してloss_wpを計算する`() = runTest {
        val receivedSfens = mutableListOf<String>()
        val scoreBySfen = mapOf(
            initialSfen to Score.Cp(300),
            sfenAfter7g7f to Score.Cp(0),
        )
        val judge = EngineDrillSecondaryJudge { sfen ->
            receivedSfens.add(sfen)
            listOf(PvInfo(multipv = 1, score = scoreBySfen.getValue(sfen), pv = emptyList(), nodes = 400_000L))
        }

        val result = judge.judge(sampleBlunder(), userMoveUsi = "7g7f")

        assertEquals(listOf(initialSfen, sfenAfter7g7f), receivedSfens, "出題局面→ユーザー手後局面の順で2回解析する")
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
        val expected = BlunderJudge.winProb(300) - BlunderJudge.winProb(0)
        assertEquals(expected, result.lossWp, 1e-12)
        assertFalse(result.isCorrect)
    }

    // ─── RemoteDrillSecondaryJudge ────────────────────────────────────────

    @Test
    fun `RemoteDrillSecondaryJudge - cpBeforeを再利用しユーザー手後局面だけを解析する`() = runTest {
        val receivedSfens = mutableListOf<String>()
        val judge = RemoteDrillSecondaryJudge { sfen ->
            receivedSfens.add(sfen)
            listOf(PvInfo(multipv = 1, score = Score.Cp(0), pv = listOf("2g2f"), nodes = 400_000L))
        }

        val result = judge.judge(sampleBlunder(cpBefore = 300L), userMoveUsi = "7g7f")

        assertEquals(listOf(sfenAfter7g7f), receivedSfens, "サーバー版はユーザー手後局面のみ解析する（cpBeforeは保存済み値を再利用）")
        val expected = BlunderJudge.winProb(300) - BlunderJudge.winProb(0)
        assertEquals(expected, result.lossWp, 1e-12)
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
        assertEquals("2g2f", result.pv)
    }

    @Test
    fun `RemoteDrillSecondaryJudge - cpBeforeが無ければ不正解フォールバック（解析は呼ばない）`() = runTest {
        var called = false
        val judge = RemoteDrillSecondaryJudge {
            called = true
            emptyList()
        }

        val result = judge.judge(sampleBlunder(cpBefore = null), userMoveUsi = "7g7f")

        assertFalse(called, "cpBeforeが無いなら解析すら試みない")
        assertFalse(result.isCorrect)
        assertTrue(result.lossWp.isNaN())
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
    }

    @Test
    fun `RemoteDrillSecondaryJudge - 不正なUSIは例外にならず不正解`() = runTest {
        val judge = RemoteDrillSecondaryJudge { emptyList() }

        val result = judge.judge(sampleBlunder(cpBefore = 300L), userMoveUsi = "xx")

        assertFalse(result.isCorrect)
        assertEquals(DrillJudge.Reason.ENGINE_EVAL, result.reason)
    }
}
