package dev.miyado.shogisupplement.ui.gamelist

import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.upload.DeleteGameOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GameListSelectionTest {

    private fun game(id: Long, uploadedAt: Long? = null) = GameRecord(
        id = id,
        fileName = "game$id.kif",
        contentHash = "hash$id",
        moveCount = 10L,
        senteName = null,
        goteName = null,
        analyzedAt = 1_000_000L,
        rating = 1500L,
        coefVersion = "hao_v1",
        uploadedAt = uploadedAt,
    )

    @Test
    fun `全件成功すると削除対象が空になりSuccessを返す`() = runTest {
        val result = deleteSelectedGames(
            targets = listOf(game(1), game(2)),
            deleteServer = false,
        ) { _, _, onResult -> onResult(DeleteGameOutcome.Success) }

        assertEquals(emptySet(), result.remainingIds)
        assertEquals(DeleteGameOutcome.Success, result.outcome)
    }

    @Test
    fun `一部だけサーバー削除に失敗すると失敗分だけ残ってServerFailedを返す`() = runTest {
        val result = deleteSelectedGames(
            targets = listOf(game(1), game(2), game(3)),
            deleteServer = true,
        ) { target, _, onResult ->
            onResult(if (target.id == 2L) DeleteGameOutcome.ServerFailed else DeleteGameOutcome.Success)
        }

        assertEquals(setOf(2L), result.remainingIds)
        assertEquals(DeleteGameOutcome.ServerFailed, result.outcome)
    }

    @Test
    fun `再試行で残っていた分だけ渡すと全件成功で空になる`() = runTest {
        val firstAttempt = deleteSelectedGames(
            targets = listOf(game(1), game(2)),
            deleteServer = true,
        ) { target, _, onResult ->
            onResult(if (target.id == 2L) DeleteGameOutcome.ServerFailed else DeleteGameOutcome.Success)
        }
        assertEquals(setOf(2L), firstAttempt.remainingIds)

        val retry = deleteSelectedGames(
            targets = listOf(game(2)),
            deleteServer = true,
        ) { _, _, onResult -> onResult(DeleteGameOutcome.Success) }

        assertEquals(emptySet(), retry.remainingIds)
        assertEquals(DeleteGameOutcome.Success, retry.outcome)
    }
}
