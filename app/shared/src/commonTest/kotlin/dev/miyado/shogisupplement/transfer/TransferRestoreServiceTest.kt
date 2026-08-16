package dev.miyado.shogisupplement.transfer

import dev.miyado.shogisupplement.api.ApiHeaders
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.crypto.TRANSFER_SECRET_BYTES
import dev.miyado.shogisupplement.crypto.TransferCode
import dev.miyado.shogisupplement.crypto.TransferSecretStore
import dev.miyado.shogisupplement.db.RatingSettings
import dev.miyado.shogisupplement.db.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [RemoteTransferRestoreService] のHTTPをMockEngineで差し替えたテスト
 * （HTTPのやり取り自体は[dev.miyado.shogisupplement.engine.RemoteAnalysisRunnerTest]と同じ流儀）。
 */
class TransferRestoreServiceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val validCode = TransferCode.encode(ByteArray(TRANSFER_SECRET_BYTES) { it.toByte() })

    private class FakeAuthRepository(
        private val importSessionResult: Result<Unit> = Result.success(Unit),
    ) : AuthRepository {
        var lastImported: Pair<String, String>? = null
            private set

        override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(null)
        override suspend fun signInAnonymously(): Result<Unit> = Result.success(Unit)
        override suspend fun accessToken(): String? = null
        override suspend fun refreshSession(): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun importSession(accessToken: String, refreshToken: String): Result<Unit> {
            lastImported = accessToken to refreshToken
            return importSessionResult
        }
    }

    private class FakeTransferSecretStore : TransferSecretStore {
        var saved: ByteArray? = null
            private set

        override suspend fun load(): ByteArray? = null
        override suspend fun save(secret: ByteArray) {
            saved = secret
        }
        override suspend fun clear() {
            saved = null
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        private var accountDeclined = false
        private var autoUpload = false
        private var consentAcceptedAt: Long? = null

        override fun saveRating(rating: Int) {}
        override fun saveRatingFull(rating: Int, service: String, ratingRaw: Int) {}
        override fun saveRatingSettings(service: String?, ratingRaw: Int?, ratingRule: String?, serviceAccountName: String?) {}
        override fun getRatingSettings(): RatingSettings = RatingSettings(1750, "lishogi", 1750, null, null)
        override fun hasUserSavedRatingSettings(): Boolean = false
        override fun getRating(): Int = 1750
        override fun getRatingFull(): Triple<Int, String, Int> = Triple(1750, "lishogi", 1750)
        override fun getServiceAccountName(): String? = null
        override fun upsertServiceAccount(service: String, accountName: String) {}
        override fun getAllServiceAccounts(): Map<String, String> = emptyMap()
        override fun getServiceAccountByService(service: String): String? = null
        override fun deleteServiceAccount(service: String) {}
        override fun hasAnyServiceAccount(): Boolean = false
        override fun saveLastUserSide(userSide: String?) {}
        override fun getLastUserSide(): String? = null
        override fun saveConsentAcceptedAt(epochSeconds: Long) { consentAcceptedAt = epochSeconds }
        override fun getConsentAcceptedAt(): Long? = consentAcceptedAt
        override fun saveAccountDeclined(declined: Boolean) { accountDeclined = declined }
        override fun isAccountDeclined(): Boolean = accountDeclined
        override fun saveAutoUpload(enabled: Boolean) { autoUpload = enabled }
        override fun getAutoUpload(): Boolean = autoUpload
        override fun saveThemeMode(themeMode: String) {}
        override fun getThemeMode(): String = "system"
        override fun saveServiceRank(service: String, rule: String, rankRaw: Int) {}
        override fun getAllServiceRanks(): Map<String, Map<String, Int>> = emptyMap()
        override fun deleteServiceRank(service: String, rule: String) {}
        override fun saveEvalDisplay(mode: String) {}
        override fun getEvalDisplay(): String = "cp"
        override fun saveSkipSideConfirm(skip: Boolean) {}
        override fun getSkipSideConfirm(): Boolean = false
        override fun saveAppPolicyCache(json: String) {}
        override fun getAppPolicyCache(): String? = null
    }

    private fun service(
        engine: MockEngine,
        authRepository: AuthRepository = FakeAuthRepository(),
        transferSecretStore: TransferSecretStore = FakeTransferSecretStore(),
        settingsRepository: SettingsRepository = FakeSettingsRepository(),
    ) = RemoteTransferRestoreService(
        baseUrl = "https://analysis-worker.example",
        authRepository = authRepository,
        transferSecretStore = transferSecretStore,
        settingsRepository = settingsRepository,
        platform = "ios",
        httpClient = HttpClient(engine),
    )

    @Test
    fun `不正なコードはネットワークに触れずInvalidCodeを返す`() = runTest {
        var requested = false
        val engine = MockEngine { _ -> requested = true; respond("", HttpStatusCode.InternalServerError) }

        val result = service(engine).restore("0000-0000")

        assertEquals(TransferRestoreResult.InvalidCode, result)
        assertTrue(!requested, "デコードに失敗した時点でHTTPリクエストは送られないはず")
    }

    @Test
    fun `200応答はセッションを取り込みSecretを保存してSuccessを返す`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"access_token":"at-1","refresh_token":"rt-1"}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val authRepository = FakeAuthRepository()
        val store = FakeTransferSecretStore()

        val result = service(engine, authRepository, store).restore(validCode)

        assertEquals(TransferRestoreResult.Success, result)
        assertEquals("at-1" to "rt-1", authRepository.lastImported)
        assertTrue(store.saved != null, "importSession成功後はSecretが保存されるはず")
    }

    @Test
    fun `復元に成功するとアカウントを作らない判断が戻り棋譜の保存が有効になる`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"access_token":"at-1","refresh_token":"rt-1"}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val settings = FakeSettingsRepository()
        settings.saveAccountDeclined(true)

        val result = service(engine, settingsRepository = settings).restore(validCode)

        assertEquals(TransferRestoreResult.Success, result)
        assertEquals(false, settings.isAccountDeclined(), "復元後はサーバー解析を使えるはず")
        assertEquals(true, settings.getAutoUpload())
        assertTrue(settings.getConsentAcceptedAt() != null, "同意済みとして扱うはず")
    }

    @Test
    fun `復元に失敗したときは作らない判断を変えない`() = runTest {
        val engine = MockEngine { _ -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound) }
        val settings = FakeSettingsRepository()
        settings.saveAccountDeclined(true)

        service(engine, settingsRepository = settings).restore(validCode)

        assertEquals(true, settings.isAccountDeclined())
    }

    @Test
    fun `200応答でもimportSessionが失敗すればSecretは保存しない`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"access_token":"at-1","refresh_token":"rt-1"}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }
        val store = FakeTransferSecretStore()
        val authRepository = FakeAuthRepository(importSessionResult = Result.failure(RuntimeException("boom")))

        val result = service(engine, authRepository, store).restore(validCode)

        assertTrue(result is TransferRestoreResult.SessionImportFailed)
        assertNull(store.saved, "importSession失敗時はSecretを保存しないはず（矛盾した端末状態を避ける）")
    }

    @Test
    fun `404はNotFound`() = runTest {
        val engine = MockEngine { _ ->
            respond(content = ByteReadChannel("""{"error":"not found"}"""), status = HttpStatusCode.NotFound, headers = jsonHeaders)
        }

        assertEquals(TransferRestoreResult.NotFound, service(engine).restore(validCode))
    }

    @Test
    fun `429はRateLimited`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"rate limited"}"""),
                status = HttpStatusCode.TooManyRequests,
                headers = jsonHeaders,
            )
        }

        assertEquals(TransferRestoreResult.RateLimited, service(engine).restore(validCode))
    }

    @Test
    fun `426はUpgradeRequired`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""{"error":"app update required"}"""),
                status = HttpStatusCode.UpgradeRequired,
                headers = jsonHeaders,
            )
        }

        assertEquals(TransferRestoreResult.UpgradeRequired, service(engine).restore(validCode))
    }

    @Test
    fun `想定外のステータスはNetworkError`() = runTest {
        val engine = MockEngine { _ ->
            respond(content = ByteReadChannel("""{"error":"boom"}"""), status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
        }

        assertTrue(service(engine).restore(validCode) is TransferRestoreResult.NetworkError)
    }

    @Test
    fun `アプリ版情報ヘッダを送る`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = ByteReadChannel("""{"access_token":"at-1","refresh_token":"rt-1"}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        service(engine).restore(validCode)

        assertEquals("ios", captured?.headers?.get(ApiHeaders.APP_PLATFORM))
        assertTrue(captured?.headers?.get(ApiHeaders.APP_BUILD) != null)
    }
}
