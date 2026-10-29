package dev.miyado.shogisupplement.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 解析結果が一定間隔で1手ずつ、ply順に出ること。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionRevealPacerTest {

    private val interval = 500L
    private val budget = 2_000L

    private fun newPacer(onReveal: (Int) -> Unit) = PositionRevealPacer(
        { ply, _ -> onReveal(ply) },
        intervalMs = interval,
        catchUpBudgetMs = budget,
    )

    @Test
    fun `まとまって届いても間隔ごとに1手ずつ出す`() = runTest {
        val revealed = mutableListOf<Int>()
        val pacer = newPacer { ply -> revealed.add(ply) }
        backgroundScope.launch { pacer.pace() }

        pacer.submit(0, emptyList())
        pacer.submit(1, emptyList())
        pacer.submit(2, emptyList())
        runCurrent()
        assertEquals(listOf(0), revealed)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(listOf(0, 1), revealed)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(listOf(0, 1, 2), revealed)
    }

    @Test
    fun `順が乱れて届いても連続する手から順に出す`() = runTest {
        val revealed = mutableListOf<Int>()
        val pacer = newPacer { ply -> revealed.add(ply) }
        backgroundScope.launch { pacer.pace() }

        pacer.submit(2, emptyList())
        pacer.submit(1, emptyList())
        runCurrent()
        assertEquals(emptyList(), revealed)

        pacer.submit(0, emptyList())
        advanceTimeBy(interval)
        runCurrent()
        assertEquals(listOf(0), revealed)

        advanceTimeBy(interval)
        runCurrent()
        assertEquals(listOf(0, 1), revealed)
    }

    @Test
    fun `残りが少なければ追いつきは上限の間隔で出す`() = runTest {
        val revealed = mutableListOf<Int>()
        val pacer = newPacer { ply -> revealed.add(ply) }

        repeat(5) { ply -> pacer.submit(ply, emptyList()) }
        pacer.revealRemaining()

        assertEquals(listOf(0, 1, 2, 3, 4), revealed)
        // 予算を残り手数で割ると上限を超えるため上限に張り付く。先頭は待たないので間隔は4つ。
        assertEquals(POSITION_CATCH_UP_MAX_INTERVAL_MS * 4, testScheduler.currentTime)
    }

    @Test
    fun `残りが多くても追いつきは下限の間隔までしか詰めない`() = runTest {
        val revealed = mutableListOf<Int>()
        val pacer = newPacer { ply -> revealed.add(ply) }

        repeat(100) { ply -> pacer.submit(ply, emptyList()) }
        pacer.revealRemaining()

        assertEquals(100, revealed.size)
        assertEquals(POSITION_CATCH_UP_MIN_INTERVAL_MS * 99, testScheduler.currentTime)
    }

    @Test
    fun `残りが1手なら追いつきで待たない`() = runTest {
        val revealed = mutableListOf<Int>()
        val pacer = newPacer { ply -> revealed.add(ply) }

        pacer.submit(0, emptyList())
        pacer.revealRemaining()

        assertEquals(listOf(0), revealed)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun `出し終えた手が再送されても二度出さない`() = runTest {
        val revealed = mutableListOf<Int>()
        val pacer = newPacer { ply -> revealed.add(ply) }
        backgroundScope.launch { pacer.pace() }

        pacer.submit(0, emptyList())
        pacer.submit(1, emptyList())
        runCurrent()
        advanceTimeBy(interval)
        runCurrent()
        assertEquals(listOf(0, 1), revealed)

        pacer.submit(0, emptyList())
        pacer.submit(1, emptyList())
        pacer.submit(2, emptyList())
        pacer.revealRemaining()

        assertEquals(listOf(0, 1, 2), revealed)
    }

    @Test
    fun `欠けた局面があっても残りを落とさない`() = runTest {
        val revealed = mutableListOf<Int>()
        val pacer = newPacer { ply -> revealed.add(ply) }

        pacer.submit(0, emptyList())
        pacer.submit(2, emptyList())
        pacer.revealRemaining()

        assertEquals(listOf(0, 2), revealed)
    }
}
