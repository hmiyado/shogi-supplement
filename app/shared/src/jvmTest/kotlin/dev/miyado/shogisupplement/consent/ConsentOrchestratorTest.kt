package dev.miyado.shogisupplement.consent

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.crypto.TransferSecretRegistrar
import dev.miyado.shogisupplement.db.SettingsRepository
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import dev.miyado.shogisupplement.db.SqlDelightSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ConsentOrchestrator] の単体テスト。
 *
 * androidApp/src/test の FakeAuthRepository はモジュール境界の外（:shared からは参照不可）
 * のため、AuthRetryingAnalyzerTest と同じ流儀でテスト専用の最小実装を用意する。
 */
class ConsentOrchestratorTest {

    private class FakeAuthRepository(
        private val signInAnonymouslyResult: Result<Unit> = Result.success(Unit),
    ) : AuthRepository {
        var signInAnonymouslyCalls: Int = 0
            private set

        private val _currentUser = MutableStateFlow<AuthUser?>(null)
        override val currentUser: StateFlow<AuthUser?> = _currentUser

        override suspend fun signInAnonymously(): Result<Unit> {
            signInAnonymouslyCalls++
            if (signInAnonymouslyResult.isSuccess) {
                _currentUser.value = AuthUser(id = "fake-anon-uid")
            }
            return signInAnonymouslyResult
        }

        override suspend fun accessToken(): String? = _currentUser.value?.let { "fake-token" }
        override suspend fun refreshSession(): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> {
            _currentUser.value = null
            return Result.success(Unit)
        }

        override suspend fun deleteAccount(): Result<Unit> {
            _currentUser.value = null
            return Result.success(Unit)
        }

        override suspend fun importSession(accessToken: String, refreshToken: String): Result<Unit> {
            _currentUser.value = AuthUser(id = "fake-imported-uid")
            return Result.success(Unit)
        }
    }

    private class FakeTransferSecretRegistrar(
        private val result: Result<Unit> = Result.success(Unit),
    ) : TransferSecretRegistrar {
        val registeredUserIds = mutableListOf<String>()

        override suspend fun registerIfNeeded(userId: String): Result<Unit> {
            registeredUserIds.add(userId)
            return result
        }
    }

    private fun newSettingsRepository(): SettingsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        return SqlDelightSettingsRepository(ShogiSupplementDatabase(driver))
    }

    @Test
    fun `同意すると同意フラグ保存 匿名サインイン 自動アップロードON 登録が順に行われる`() = runBlocking {
        val settings = newSettingsRepository()
        val auth = FakeAuthRepository()
        val registrar = FakeTransferSecretRegistrar()
        val orchestrator = ConsentOrchestrator(settings, auth, registrar)

        assertNull(settings.getConsentAcceptedAt())
        assertTrue(!settings.getAutoUpload())

        orchestrator.acceptConsent()

        assertNotNull(settings.getConsentAcceptedAt())
        assertEquals(1, auth.signInAnonymouslyCalls)
        assertTrue(settings.getAutoUpload())
        assertEquals(listOf("fake-anon-uid"), registrar.registeredUserIds)
    }

    @Test
    fun `既にログイン済みなら匿名サインインを呼び直さない`() = runBlocking {
        val settings = newSettingsRepository()
        val auth = FakeAuthRepository()
        // 事前に一度サインインさせておく。
        auth.signInAnonymously()
        val registrar = FakeTransferSecretRegistrar()
        val orchestrator = ConsentOrchestrator(settings, auth, registrar)

        orchestrator.acceptConsent()

        assertEquals(1, auth.signInAnonymouslyCalls, "既存セッションがあるなら再サインインしない")
        assertEquals(listOf("fake-anon-uid"), registrar.registeredUserIds)
    }

    @Test
    fun `匿名サインイン失敗でも同意フラグと自動アップロード設定は保存される`() = runBlocking {
        val settings = newSettingsRepository()
        val auth = FakeAuthRepository(signInAnonymouslyResult = Result.failure(RuntimeException("network")))
        val registrar = FakeTransferSecretRegistrar()
        val orchestrator = ConsentOrchestrator(settings, auth, registrar)

        orchestrator.acceptConsent()

        assertNotNull(settings.getConsentAcceptedAt(), "サインイン失敗でも同意フラグは保存する")
        assertTrue(settings.getAutoUpload(), "サインイン失敗でも自動アップロードはONにする")
        // 未ログインのため登録は呼ばれない（呼び出し元の解析フローが再サインインを担う）。
        assertEquals(emptyList(), registrar.registeredUserIds)
    }

    @Test
    fun `K_auth登録が失敗しても例外を投げず同意処理は完了する`() = runBlocking {
        val settings = newSettingsRepository()
        val auth = FakeAuthRepository()
        val registrar = FakeTransferSecretRegistrar(result = Result.failure(RuntimeException("network")))
        val orchestrator = ConsentOrchestrator(settings, auth, registrar)

        orchestrator.acceptConsent()

        assertNotNull(settings.getConsentAcceptedAt())
        assertTrue(settings.getAutoUpload())
        assertEquals(listOf("fake-anon-uid"), registrar.registeredUserIds)
    }
}
