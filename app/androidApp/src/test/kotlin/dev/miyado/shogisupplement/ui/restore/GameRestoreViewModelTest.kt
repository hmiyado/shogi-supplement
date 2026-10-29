package dev.miyado.shogisupplement.ui.restore

import dev.miyado.shogisupplement.ui.generated.resources.allStringResources

import dev.miyado.shogisupplement.download.GameDownloadOutcome
import dev.miyado.shogisupplement.download.GameDownloadService
import dev.miyado.shogisupplement.download.GameImportOutcome
import dev.miyado.shogisupplement.download.ReconstructedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class GameRestoreViewModelTest {
    @org.junit.Before
    fun prepareStringResources() = kotlinx.coroutines.runBlocking {
        // Resolve the same resource environment before assertions on synchronous test dispatchers.
        dev.miyado.shogisupplement.ui.generated.resources.Res.allStringResources.values.forEach {
            org.jetbrains.compose.resources.getString(it)
        }
    }


    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeGameDownloadService(
        private val countResult: Result<Int> = Result.success(0),
        private val downloadOutcome: GameDownloadOutcome = GameDownloadOutcome.Completed(0, 0, 0),
        private val progressSteps: List<Pair<Int, Int>> = emptyList(),
    ) : GameDownloadService {
        var downloadCalled = false
            private set

        override suspend fun countRemoteGames(): Result<Int> = countResult

        override suspend fun downloadAndImport(
            onProgress: (done: Int, total: Int) -> Unit,
            importGame: suspend (ReconstructedGame) -> GameImportOutcome,
        ): GameDownloadOutcome {
            downloadCalled = true
            progressSteps.forEach { (done, total) -> onProgress(done, total) }
            return downloadOutcome
        }
    }

    private fun buildVm(
        service: GameDownloadService,
        importGame: suspend (ReconstructedGame) -> GameImportOutcome = { GameImportOutcome(success = true, gameId = 1L) },
    ) = GameRestoreViewModel(service, importGame)

    @Test
    fun 初期化直後にcountRemoteGamesを呼びReady状態になる() {
        val vm = buildVm(FakeGameDownloadService(countResult = Result.success(7)))
        val state = vm.uiState.value
        assertTrue(state is GameRestoreUiState.Ready)
        assertEquals(7, (state as GameRestoreUiState.Ready).count)
    }

    @Test
    fun 件数取得に失敗するとError状態になる() {
        val vm = buildVm(FakeGameDownloadService(countResult = Result.failure(RuntimeException("boom"))))
        val state = vm.uiState.value
        assertTrue(state is GameRestoreUiState.Error)
        assertEquals("棋譜を取得できませんでした。しばらくしてから再度お試しください。", (state as GameRestoreUiState.Error).message)
    }

    /** 1回目は失敗・2回目以降は成功する（retryが実際にcountRemoteGamesをやり直すことを確認するため）。 */
    private class FlakyThenOkGameDownloadService : GameDownloadService {
        private var callCount = 0

        override suspend fun countRemoteGames(): Result<Int> {
            callCount++
            return if (callCount == 1) Result.failure(RuntimeException("boom")) else Result.success(4)
        }

        override suspend fun downloadAndImport(
            onProgress: (done: Int, total: Int) -> Unit,
            importGame: suspend (ReconstructedGame) -> GameImportOutcome,
        ): GameDownloadOutcome = GameDownloadOutcome.Completed(0, 0, 0)
    }

    @Test
    fun retryは件数確認をやり直す() {
        val vm = buildVm(FlakyThenOkGameDownloadService())
        assertTrue(vm.uiState.value is GameRestoreUiState.Error)

        vm.retry()

        val state = vm.uiState.value
        assertTrue(state is GameRestoreUiState.Ready)
        assertEquals(4, (state as GameRestoreUiState.Ready).count)
    }

    @Test
    fun startDownloadで進捗が反映され完了状態になる() {
        val service = FakeGameDownloadService(
            countResult = Result.success(3),
            downloadOutcome = GameDownloadOutcome.Completed(total = 3, succeeded = 3, failed = 0),
            progressSteps = listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3),
        )
        val vm = buildVm(service)

        vm.startDownload()

        assertTrue(service.downloadCalled)
        val state = vm.uiState.value
        assertTrue(state is GameRestoreUiState.Completed)
        assertEquals(GameRestoreUiState.Completed(total = 3, succeeded = 3, failed = 0), state)
    }

    @Test
    fun startDownloadは件数確認前Ready以外の状態では何もしない() {
        val service = FakeGameDownloadService(countResult = Result.failure(RuntimeException("boom")))
        val vm = buildVm(service)
        assertTrue(vm.uiState.value is GameRestoreUiState.Error)

        vm.startDownload()

        assertTrue("Error状態からのstartDownloadはdownloadAndImportを呼ばないはず", !service.downloadCalled)
    }

    @Test
    fun 未ログインならNotAuthenticatedのエラー文言になる() {
        val service = FakeGameDownloadService(
            countResult = Result.success(2),
            downloadOutcome = GameDownloadOutcome.NotAuthenticated,
        )
        val vm = buildVm(service)

        vm.startDownload()

        val state = vm.uiState.value as GameRestoreUiState.Error
        assertEquals("ログイン状態を確認できませんでした。設定からやり直してください。", state.message)
    }

    @Test
    fun 端末シークレット未生成ならNoSecretのエラー文言になる() {
        val service = FakeGameDownloadService(
            countResult = Result.success(2),
            downloadOutcome = GameDownloadOutcome.NoSecret,
        )
        val vm = buildVm(service)

        vm.startDownload()

        val state = vm.uiState.value as GameRestoreUiState.Error
        assertEquals("復元用のデータが見つかりませんでした。もう一度お試しください。", state.message)
    }

    @Test
    fun 通信失敗ならネットワークエラー文言になる() {
        val service = FakeGameDownloadService(
            countResult = Result.success(2),
            downloadOutcome = GameDownloadOutcome.NetworkError("boom"),
        )
        val vm = buildVm(service)

        vm.startDownload()

        val state = vm.uiState.value as GameRestoreUiState.Error
        assertEquals("棋譜を取得できませんでした。しばらくしてから再度お試しください。", state.message)
    }

    @Test
    fun 一部失敗でも完了状態にfailed件数が反映される() {
        val service = FakeGameDownloadService(
            countResult = Result.success(5),
            downloadOutcome = GameDownloadOutcome.Completed(total = 5, succeeded = 3, failed = 2),
        )
        val vm = buildVm(service)

        vm.startDownload()

        assertEquals(
            GameRestoreUiState.Completed(total = 5, succeeded = 3, failed = 2),
            vm.uiState.value,
        )
    }
}
