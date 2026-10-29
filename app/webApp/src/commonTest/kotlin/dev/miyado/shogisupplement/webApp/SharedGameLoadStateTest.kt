package dev.miyado.shogisupplement.webApp

import dev.miyado.shogisupplement.gameshare.GameShareResult
import dev.miyado.shogisupplement.gameshare.GameShareSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedGameLoadStateTest {
    private val snapshot = GameShareSnapshot(
        id = "AbCdEfGhIjKlMnOpQrStUv",
        initialSfen = "sfen",
        mainline = emptyList(),
        selectedPly = 0,
        branch = null,
        userSide = "sente",
        flip = false,
        winner = null,
        endReason = null,
    )

    @Test
    fun mapsAnonymousReadOutcomes() {
        assertEquals(SharedGameLoadState.Ready(snapshot), GameShareResult.Success(snapshot).toLoadState())
        assertEquals(SharedGameLoadState.Missing, GameShareResult.NotFound.toLoadState())
        assertEquals(SharedGameLoadState.Invalid, GameShareResult.Invalid.toLoadState())
        assertEquals(SharedGameLoadState.Failed, GameShareResult.Failure("offline").toLoadState())
        assertEquals(SharedGameLoadState.Failed, GameShareResult.RateLimited.toLoadState())
    }
}
