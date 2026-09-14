package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.pipeline.InProgressAnalysisRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisSessionCoordinatorTest {

    private val session = AnalysisSession(
        id = "session-id",
        fileName = "game.kif",
        moves = listOf("7g7f"),
        userSide = "sente",
    )

    private fun outcome() = AnalysisOrchestrator.Outcome.Completed(gameId = 1L, alreadyExisted = false)

    @Test
    fun `完了するとセッションをレジストリから除去する`() = runTest {
        val registry = InProgressAnalysisRegistry()
        val coordinator = AnalysisSessionCoordinator(registry)

        val result = coordinator.run(session, analyze = { onPositionResult ->
            onPositionResult(0, emptyList())
            outcome()
        })

        assertEquals(outcome(), result)
        assertTrue(registry.sessions.value.isEmpty())
    }

    @Test
    fun `解析中は共通レジストリにセッションと進捗を公開する`() = runTest {
        val registry = InProgressAnalysisRegistry()
        val coordinator = AnalysisSessionCoordinator(registry)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val job = launch {
            coordinator.run(session, analyze = { onPositionResult ->
                onPositionResult(0, emptyList())
                started.complete(Unit)
                release.await()
                outcome()
            })
        }
        started.await()

        val current = registry.snapshot(session.id)
        assertEquals(session.fileName, current?.fileName)
        assertEquals(1, current?.progressive?.doneCount)

        release.complete(Unit)
        job.join()
        assertTrue(registry.sessions.value.isEmpty())
    }

    @Test
    fun `失敗でもセッションを除去する`() = runTest {
        val registry = InProgressAnalysisRegistry()
        val coordinator = AnalysisSessionCoordinator(registry)
        val failure = AnalysisOrchestrator.Outcome.Failed("failed")

        val result = coordinator.run(session, analyze = { failure })

        assertEquals(failure, result)
        assertTrue(registry.sessions.value.isEmpty())
    }

    @Test
    fun `キャンセル相当の例外でもセッションを除去する`() = runTest {
        val registry = InProgressAnalysisRegistry()
        val coordinator = AnalysisSessionCoordinator(registry)

        runCatching {
            coordinator.run(session, analyze = { throw IllegalStateException("cancelled") })
        }

        assertTrue(registry.sessions.value.isEmpty())
    }
}
