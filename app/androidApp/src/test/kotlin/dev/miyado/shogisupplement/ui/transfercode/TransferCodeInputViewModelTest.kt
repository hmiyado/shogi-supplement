package dev.miyado.shogisupplement.ui.transfercode

import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.auth.FakeAuthRepository
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.transfer.TransferRestoreResult
import dev.miyado.shogisupplement.transfer.TransferRestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [TransferCodeInputViewModel] の単体テスト。
 * FakeAuthRepository（androidApp/src/test）とテスト専用の [FakeTransferRestoreService] を注入する
 * （AccountViewModelTest と同じ流儀）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferCodeInputViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeTransferRestoreService(
        private val result: TransferRestoreResult = TransferRestoreResult.Success,
    ) : TransferRestoreService {
        var lastCode: String? = null
            private set

        override suspend fun restore(code: String): TransferRestoreResult {
            lastCode = code
            return result
        }
    }

    private fun buildVm(
        auth: FakeAuthRepository = FakeAuthRepository(),
        restoreService: FakeTransferRestoreService = FakeTransferRestoreService(),
    ) = TransferCodeInputViewModel(auth, restoreService)

    @Test
    fun initialState_isIdle() {
        val vm = buildVm()
        assertTrue(vm.uiState.value is TransferCodeInputUiState.Idle)
    }

    // ─── 未ログイン端末（確認ダイアログ不要） ───────────────────────────────────

    @Test
    fun submit_notLoggedIn_restoresImmediately_andBecomesSuccess() {
        val restoreService = FakeTransferRestoreService(TransferRestoreResult.Success)
        val vm = buildVm(auth = FakeAuthRepository(), restoreService = restoreService)

        vm.submit("0000-0000-0000-0000-0000-00")

        assertTrue(vm.uiState.value is TransferCodeInputUiState.Success)
        assertEquals("0000-0000-0000-0000-0000-00", restoreService.lastCode)
    }

    @Test
    fun submit_notLoggedIn_invalidCode_showsError() {
        val restoreService = FakeTransferRestoreService(TransferRestoreResult.InvalidCode)
        val vm = buildVm(restoreService = restoreService)

        vm.submit("garbage")

        val state = vm.uiState.value
        assertTrue(state is TransferCodeInputUiState.Error)
        assertEquals(AppStrings.TRANSFER_CODE_INPUT_ERROR_INVALID, (state as TransferCodeInputUiState.Error).message)
    }

    @Test
    fun submit_notLoggedIn_notFound_showsGenericNotFoundError() {
        val restoreService = FakeTransferRestoreService(TransferRestoreResult.NotFound)
        val vm = buildVm(restoreService = restoreService)

        vm.submit("some-code")

        val state = vm.uiState.value as TransferCodeInputUiState.Error
        assertEquals(AppStrings.TRANSFER_CODE_INPUT_ERROR_NOT_FOUND, state.message)
    }

    @Test
    fun submit_notLoggedIn_rateLimited_showsRateLimitedError() {
        val vm = buildVm(restoreService = FakeTransferRestoreService(TransferRestoreResult.RateLimited))
        vm.submit("some-code")
        val state = vm.uiState.value as TransferCodeInputUiState.Error
        assertEquals(AppStrings.TRANSFER_CODE_INPUT_ERROR_RATE_LIMITED, state.message)
    }

    @Test
    fun submit_notLoggedIn_upgradeRequired_showsUpgradeRequiredError() {
        val vm = buildVm(restoreService = FakeTransferRestoreService(TransferRestoreResult.UpgradeRequired))
        vm.submit("some-code")
        val state = vm.uiState.value as TransferCodeInputUiState.Error
        assertEquals(AppStrings.TRANSFER_CODE_INPUT_ERROR_UPGRADE_REQUIRED, state.message)
    }

    @Test
    fun submit_notLoggedIn_sessionImportFailed_showsGenericError() {
        val vm = buildVm(
            restoreService = FakeTransferRestoreService(TransferRestoreResult.SessionImportFailed("boom")),
        )
        vm.submit("some-code")
        val state = vm.uiState.value as TransferCodeInputUiState.Error
        assertEquals(AppStrings.TRANSFER_CODE_INPUT_ERROR_GENERIC, state.message)
    }

    @Test
    fun submit_notLoggedIn_networkError_showsGenericError() {
        val vm = buildVm(
            restoreService = FakeTransferRestoreService(TransferRestoreResult.NetworkError("boom")),
        )
        vm.submit("some-code")
        val state = vm.uiState.value as TransferCodeInputUiState.Error
        assertEquals(AppStrings.TRANSFER_CODE_INPUT_ERROR_GENERIC, state.message)
    }

    // ─── ログイン中端末（確認ダイアログを経由する） ─────────────────────────────

    @Test
    fun submit_alreadyLoggedIn_needsConfirmation_doesNotCallRestoreYet() {
        val restoreService = FakeTransferRestoreService()
        val auth = FakeAuthRepository(initialUser = AuthUser("current-uid"))
        val vm = buildVm(auth = auth, restoreService = restoreService)

        vm.submit("some-code")

        val state = vm.uiState.value
        assertTrue(state is TransferCodeInputUiState.NeedsConfirmation)
        assertEquals("some-code", (state as TransferCodeInputUiState.NeedsConfirmation).code)
        assertNull("確認前はrestore()が呼ばれないはず", restoreService.lastCode)
    }

    @Test
    fun confirmRestore_afterNeedsConfirmation_callsRestoreAndBecomesSuccess() {
        val restoreService = FakeTransferRestoreService(TransferRestoreResult.Success)
        val auth = FakeAuthRepository(initialUser = AuthUser("current-uid"))
        val vm = buildVm(auth = auth, restoreService = restoreService)

        vm.submit("some-code")
        vm.confirmRestore()

        assertEquals("some-code", restoreService.lastCode)
        assertTrue(vm.uiState.value is TransferCodeInputUiState.Success)
    }

    @Test
    fun cancelConfirmation_returnsToIdle_withoutCallingRestore() {
        val restoreService = FakeTransferRestoreService()
        val auth = FakeAuthRepository(initialUser = AuthUser("current-uid"))
        val vm = buildVm(auth = auth, restoreService = restoreService)

        vm.submit("some-code")
        vm.cancelConfirmation()

        assertTrue(vm.uiState.value is TransferCodeInputUiState.Idle)
        assertNull(restoreService.lastCode)
    }

    @Test
    fun confirmRestore_withoutPendingConfirmation_isNoOp() {
        val restoreService = FakeTransferRestoreService()
        val vm = buildVm(restoreService = restoreService)

        // NeedsConfirmationを経由していない状態でconfirmRestoreを呼んでも何もしない
        vm.confirmRestore()

        assertTrue(vm.uiState.value is TransferCodeInputUiState.Idle)
        assertNull(restoreService.lastCode)
    }

    // ─── エラー解除 ───────────────────────────────────────────────────────────

    @Test
    fun dismissError_returnsToIdle() {
        val vm = buildVm(restoreService = FakeTransferRestoreService(TransferRestoreResult.NotFound))
        vm.submit("some-code")
        assertTrue(vm.uiState.value is TransferCodeInputUiState.Error)

        vm.dismissError()

        assertTrue(vm.uiState.value is TransferCodeInputUiState.Idle)
    }
}
