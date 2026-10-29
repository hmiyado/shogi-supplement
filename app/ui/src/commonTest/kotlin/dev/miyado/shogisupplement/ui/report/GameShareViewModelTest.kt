package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.gameshare.*
import dev.miyado.shogisupplement.auth.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

class GameShareViewModelTest {
    private class Service : GameShareService {
        override var sharePageUrl: String? = null
        override val currentOwner = MutableStateFlow<AuthUser?>(AuthUser("owner"))
        var creates = 0
        var result: GameShareResult<GameShareSummary> = GameShareResult.Success(GameShareSummary("AbCdEfGhIjKlMnOpQrStUv"))
        var pending: CompletableDeferred<Unit>? = null
        override suspend fun create(draft: GameShareDraft): GameShareResult<GameShareSummary> {creates++;pending?.await();return result}
    }
    private val draft = GameShareDraft("hash","sfen",listOf("7g7f"),1,null,"sente",false,null,null)
    @Test fun openingDoesNotLoadShareState() = runTest {
        val s=Service();val vm=GameShareViewModel(this,s);advanceUntilIdle();assertEquals(GameSharePhase.None,vm.state.value.phase);assertEquals(0,s.creates)
        vm.create(draft);advanceUntilIdle();assertEquals(GameSharePhase.Active,vm.state.value.phase)
        vm.reset();assertNull(vm.state.value.url);assertEquals(GameSharePhase.None,vm.state.value.phase)
    }
    @Test fun missingSourceCanBeRetriedAfterSync() = runTest {
        val s=Service().apply {result=GameShareResult.SourceMissing};val vm=GameShareViewModel(this,s)
        vm.create(draft);advanceUntilIdle();assertEquals(GameSharePhase.SourceMissing,vm.state.value.phase)
        s.result=GameShareResult.Failure("offline");vm.create(draft);advanceUntilIdle();assertEquals(GameSharePhase.CreateFailed,vm.state.value.phase)
    }
    @Test fun doubleTapDoesNotCreateTwoSnapshots() = runTest {
        val s=Service().apply {pending=CompletableDeferred()};val vm=GameShareViewModel(this,s)
        vm.create(draft);vm.create(draft);runCurrent();assertEquals(1,s.creates)
        s.pending!!.complete(Unit);advanceUntilIdle();assertEquals(GameSharePhase.Active,vm.state.value.phase)
    }
    @Test fun configuredPageKeepsDevelopmentSharingOnTheLan() = runTest {
        val service = Service().apply { sharePageUrl = "http://192.0.2.1:4179/kento.html" }
        val vm = GameShareViewModel(this, service)
        vm.create(draft)
        advanceUntilIdle()
        assertEquals("http://192.0.2.1:4179/kento.html?game=v1&id=AbCdEfGhIjKlMnOpQrStUv", vm.state.value.url)
    }

}
