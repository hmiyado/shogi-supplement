package dev.miyado.shogisupplement.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.auth.FakeAuthRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.account.AccountUiState
import dev.miyado.shogisupplement.ui.account.AccountViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AccountViewModel の単体テスト（匿名認証 v1）。
 * FakeAuthRepository と インメモリ DB を注入して検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /** 同一インメモリDBを共有する GameRepository/SettingsRepository のペア。 */
    private class TestRepos(val game: GameRepository, val settings: SettingsRepository)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDb(): TestRepos {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        val database = ShogiSupplementDatabase(driver)
        return TestRepos(GameRepository(database), SettingsRepository(database))
    }

    /** UnconfinedTestDispatcher を IO として注入して ViewModel を生成する。 */
    private fun buildVm(
        auth: FakeAuthRepository = FakeAuthRepository(),
        repos: TestRepos = createDb(),
    ) = AccountViewModel(auth, repos.game, repos.settings, ioDispatcher = testDispatcher)

    // ─── 初期状態 ─────────────────────────────────────────────────────────────

    @Test
    fun initialState_notLoggedIn() {
        val vm = buildVm()
        assertTrue(vm.uiState.value is AccountUiState.NotLoggedIn)
    }

    @Test
    fun initialState_loggedIn() {
        val fakeAuth = FakeAuthRepository(
            initialUser = AuthUser("fake-anon-uid"),
        )
        val vm = buildVm(auth = fakeAuth)
        val state = vm.uiState.value
        assertTrue("Expected LoggedIn but got $state", state is AccountUiState.LoggedIn)
    }

    @Test
    fun initialState_loggedIn_loadsAutoUpload() {
        val fakeAuth = FakeAuthRepository(initialUser = AuthUser("fake-anon-uid"))
        val repos = createDb()
        repos.settings.saveAutoUpload(true)  // DB に ON をセット

        val vm = buildVm(auth = fakeAuth, repos = repos)

        val state = vm.uiState.value as AccountUiState.LoggedIn
        assertTrue("autoUpload should be loaded from DB", state.autoUpload)
    }

    @Test
    fun initialState_loggedIn_loadsUploadedCount() {
        val fakeAuth = FakeAuthRepository(initialUser = AuthUser("fake-anon-uid"))
        val repos = createDb()
        val gameId = saveUploadedGame(repos.game)

        val vm = buildVm(auth = fakeAuth, repos = repos)

        val state = vm.uiState.value as AccountUiState.LoggedIn
        assertEquals("uploadedCount should be loaded from DB", 1, state.uploadedCount)
    }

    // ─── 匿名サインイン ───────────────────────────────────────────────────────

    @Test
    fun signInAnonymously_success_becomesLoggedIn() {
        val vm = buildVm()
        vm.signInAnonymously()
        val state = vm.uiState.value
        assertTrue("Expected LoggedIn but got $state", state is AccountUiState.LoggedIn)
        assertEquals(1, (vm.uiState.value as? AccountUiState.NotLoggedIn)?.let { 0 } ?: 1)
    }

    @Test
    fun signInAnonymously_success_callsSignInOnce() {
        val fakeAuth = FakeAuthRepository()
        val vm = buildVm(auth = fakeAuth)
        vm.signInAnonymously()
        assertEquals(1, fakeAuth.signInAnonymouslyCalls)
    }

    @Test
    fun signInAnonymously_success_noUidInState() {
        // uid は LoggedIn 状態に含まれない（表示しない仕様）
        val vm = buildVm()
        vm.signInAnonymously()
        val state = vm.uiState.value
        assertTrue("Expected LoggedIn but got $state", state is AccountUiState.LoggedIn)
        // LoggedIn に uid/email フィールドがないことを型で保証済み
    }

    @Test
    fun signInAnonymously_networkError_showsJapaneseError() {
        val fakeAuth = FakeAuthRepository(
            signInAnonymouslyResult = Result.failure(
                java.net.UnknownHostException("Unable to resolve host"),
            ),
        )
        val vm = buildVm(auth = fakeAuth)
        vm.signInAnonymously()
        val state = vm.uiState.value
        assertTrue(state is AccountUiState.NotLoggedIn)
        assertEquals(AppStrings.AUTH_ERROR_NETWORK, (state as AccountUiState.NotLoggedIn).error)
    }

    @Test
    fun signInAnonymously_genericError_showsJapaneseError() {
        val fakeAuth = FakeAuthRepository(
            signInAnonymouslyResult = Result.failure(RuntimeException("Supabase error")),
        )
        val vm = buildVm(auth = fakeAuth)
        vm.signInAnonymously()
        val state = vm.uiState.value
        assertTrue(state is AccountUiState.NotLoggedIn)
        assertEquals(
            AppStrings.AUTH_ERROR_ANON_SIGN_IN_GENERIC,
            (state as AccountUiState.NotLoggedIn).error,
        )
    }

    // ─── サインアウト ─────────────────────────────────────────────────────────

    @Test
    fun signOut_becomesNotLoggedIn() {
        val fakeAuth = FakeAuthRepository(
            initialUser = AuthUser("fake-anon-uid"),
        )
        val vm = buildVm(auth = fakeAuth)
        assertTrue(vm.uiState.value is AccountUiState.LoggedIn)

        vm.signOut()
        val state = vm.uiState.value
        assertTrue(state is AccountUiState.NotLoggedIn)
        assertNull((state as AccountUiState.NotLoggedIn).error)
    }

    // ─── 自動アップロードトグル ───────────────────────────────────────────────

    @Test
    fun setAutoUpload_true_savesToDbAndUpdatesState() {
        val fakeAuth = FakeAuthRepository(initialUser = AuthUser("fake-anon-uid"))
        val repos = createDb()
        val vm = buildVm(auth = fakeAuth, repos = repos)

        vm.setAutoUpload(true)

        assertTrue(repos.settings.getAutoUpload())
        val state = vm.uiState.value as AccountUiState.LoggedIn
        assertTrue(state.autoUpload)
    }

    @Test
    fun setAutoUpload_false_savesToDbAndUpdatesState() {
        val fakeAuth = FakeAuthRepository(initialUser = AuthUser("fake-anon-uid"))
        val repos = createDb()
        repos.settings.saveAutoUpload(true)  // 先に ON にしておく
        val vm = buildVm(auth = fakeAuth, repos = repos)

        vm.setAutoUpload(false)

        assertFalse(repos.settings.getAutoUpload())
        val state = vm.uiState.value as AccountUiState.LoggedIn
        assertFalse(state.autoUpload)
    }

    @Test
    fun setAutoUpload_persistsAcrossLogin() {
        val repos = createDb()
        repos.settings.saveAutoUpload(true)

        // ログアウト状態で DB 確認
        assertTrue(repos.settings.getAutoUpload())

        // ログイン後に ViewModel を作成すると設定が読み込まれる
        val fakeAuth = FakeAuthRepository(initialUser = AuthUser("fake-anon-uid"))
        val vm = buildVm(auth = fakeAuth, repos = repos)
        val state = vm.uiState.value as AccountUiState.LoggedIn
        assertTrue("autoUpload should persist across sessions", state.autoUpload)
    }

    // ─── アカウント削除（提供をやめてデータ削除） ──────────────────────────────

    /** ゲームを1件保存して uploaded_at を設定するヘルパ。game_id を返す。 */
    private fun saveUploadedGame(game: GameRepository): Long {
        val gameId = game.saveAnalysis(
            fileName = "g1.kif",
            contentHash = "hash-delete-test",
            moves = listOf("7g7f"),
            headers = emptyMap(),
            reports = emptyList(),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        game.updateUploadedAt(gameId, 1_780_000_000L)
        return gameId
    }

    @Test
    fun deleteAccount_success_becomesNotLoggedIn_andResetsUploadedAt() {
        val fakeAuth = FakeAuthRepository(initialUser = AuthUser("fake-anon-uid"))
        val repos = createDb()
        val gameId = saveUploadedGame(repos.game)
        val vm = buildVm(auth = fakeAuth, repos = repos)

        vm.deleteAccount()

        // 未提供状態になる
        val state = vm.uiState.value
        assertTrue("Expected NotLoggedIn but got $state", state is AccountUiState.NotLoggedIn)
        assertNull((state as AccountUiState.NotLoggedIn).error)
        assertNull(fakeAuth.currentUser.value)
        assertEquals(1, fakeAuth.deleteAccountCalls)
        // uploaded_at が全リセットされ、再アップロード可能な状態に戻る
        assertNull(repos.game.getGameById(gameId)!!.uploadedAt)
        assertEquals(1, repos.game.getNotUploadedGames().size)
    }

    @Test
    fun deleteAccount_failure_staysLoggedIn_withError_andKeepsUploadedAt() {
        val fakeAuth = FakeAuthRepository(
            initialUser = AuthUser("fake-anon-uid"),
            deleteAccountResult = Result.failure(RuntimeException("サーバーに接続できません")),
        )
        val repos = createDb()
        val gameId = saveUploadedGame(repos.game)
        val vm = buildVm(auth = fakeAuth, repos = repos)

        vm.deleteAccount()

        // 提供中状態を維持し、エラーメッセージを表示
        val state = vm.uiState.value
        assertTrue("Expected LoggedIn but got $state", state is AccountUiState.LoggedIn)
        assertEquals(AppStrings.AUTH_ERROR_DELETE_GENERIC, (state as AccountUiState.LoggedIn).error)
        // uploaded_at は維持される（サーバー側データは消えていない）
        assertEquals(1_780_000_000L, repos.game.getGameById(gameId)!!.uploadedAt)
    }

    @Test
    fun deleteAccount_notLoggedIn_isNoOp() {
        val fakeAuth = FakeAuthRepository()
        val vm = buildVm(auth = fakeAuth)

        vm.deleteAccount()

        assertTrue(vm.uiState.value is AccountUiState.NotLoggedIn)
        assertEquals(0, fakeAuth.deleteAccountCalls)
    }

    // ─── 状態遷移テスト（有効化 → uid取得（画面に出ない）→ 削除 → サインアウト）─────

    @Test
    fun fullFlow_enable_then_delete_becomesNotLoggedIn() {
        val fakeAuth = FakeAuthRepository()
        val repos = createDb()
        val vm = buildVm(auth = fakeAuth, repos = repos)

        // 初期: 未提供
        assertTrue(vm.uiState.value is AccountUiState.NotLoggedIn)

        // 有効化: 匿名サインイン
        vm.signInAnonymously()
        assertTrue("After signInAnonymously expected LoggedIn", vm.uiState.value is AccountUiState.LoggedIn)
        // uid は画面の状態に露出しない
        val loggedIn = vm.uiState.value as AccountUiState.LoggedIn
        // uploadedCount・autoUpload のみ
        assertEquals(0, loggedIn.uploadedCount)

        // 削除: 提供をやめる
        vm.deleteAccount()
        assertTrue("After deleteAccount expected NotLoggedIn", vm.uiState.value is AccountUiState.NotLoggedIn)
        assertNull((vm.uiState.value as AccountUiState.NotLoggedIn).error)
    }

    @Test
    fun fullFlow_enable_then_signOut_becomesNotLoggedIn() {
        val fakeAuth = FakeAuthRepository()
        val vm = buildVm(auth = fakeAuth)

        vm.signInAnonymously()
        assertTrue(vm.uiState.value is AccountUiState.LoggedIn)

        vm.signOut()
        assertTrue(vm.uiState.value is AccountUiState.NotLoggedIn)
    }
}
