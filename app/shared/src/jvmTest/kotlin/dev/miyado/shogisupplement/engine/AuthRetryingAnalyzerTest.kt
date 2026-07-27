package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.auth.AuthRepository
import dev.miyado.shogisupplement.auth.AuthUser
import dev.miyado.shogisupplement.blunder.Score
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [AuthRetryingAnalyzer] の単体テスト。
 *
 * androidApp/src/test の FakeAuthRepository はモジュール境界の外（:shared からは参照不可）
 * のため、ここではテスト専用の最小 [AuthRepository] 実装を用意する。
 */
class AuthRetryingAnalyzerTest {

    /** テスト専用の最小 AuthRepository 実装（refreshSession の呼び出し回数・結果のみ検証対象）。 */
    private class FakeAuthRepository(
        private val refreshSessionResult: Result<Unit> = Result.success(Unit),
    ) : AuthRepository {
        var refreshSessionCalls: Int = 0
            private set

        override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(AuthUser(id = "u1"))
        override suspend fun signInAnonymously(): Result<Unit> = Result.success(Unit)
        override suspend fun accessToken(): String? = "token"
        override suspend fun refreshSession(): Result<Unit> {
            refreshSessionCalls++
            return refreshSessionResult
        }
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    }

    /** 呼び出しごとに [responses] を先頭から1つずつ消費して返す/投げる GameAnalyzer。 */
    private class ScriptedAnalyzer(private val responses: List<() -> List<List<PvInfo>>>) : GameAnalyzer {
        var callCount: Int = 0
            private set

        override suspend fun analyzeGame(
            moves: List<String>,
            onProgress: ((done: Int, total: Int) -> Unit)?,
        ): List<List<PvInfo>> {
            val response = responses[callCount]
            callCount++
            return response()
        }
    }

    private val fakeResult = listOf(listOf(PvInfo(multipv = 1, score = Score.Cp(0), pv = emptyList(), nodes = 0L)))

    @Test
    fun `401の後refreshSessionが成功したら1回だけリトライして成功する`() = runBlocking {
        val auth = FakeAuthRepository(refreshSessionResult = Result.success(Unit))
        val delegate = ScriptedAnalyzer(
            listOf(
                { throw RemoteAnalysisException.Unauthorized("expired") },
                { fakeResult },
            ),
        )
        val analyzer = AuthRetryingAnalyzer(delegate, auth)

        val result = analyzer.analyzeGame(listOf("7g7f"))

        assertEquals(fakeResult, result)
        assertEquals(1, auth.refreshSessionCalls)
        assertEquals(2, delegate.callCount)
    }

    @Test
    fun `refreshSessionが失敗したら元の401をそのまま伝播する`() = runBlocking {
        val auth = FakeAuthRepository(refreshSessionResult = Result.failure(RuntimeException("refresh failed")))
        val delegate = ScriptedAnalyzer(
            listOf(
                { throw RemoteAnalysisException.Unauthorized("expired") },
            ),
        )
        val analyzer = AuthRetryingAnalyzer(delegate, auth)

        val e = assertFailsWith<RemoteAnalysisException.Unauthorized> {
            analyzer.analyzeGame(listOf("7g7f"))
        }

        assertEquals("expired", e.message)
        assertEquals(1, auth.refreshSessionCalls)
        // リトライ(delegateの2回目呼び出し)は行われない。
        assertEquals(1, delegate.callCount)
    }

    @Test
    fun `refreshSession成功後にリトライでも再度401なら伝播しそれ以上リトライしない`() = runBlocking {
        val auth = FakeAuthRepository(refreshSessionResult = Result.success(Unit))
        val delegate = ScriptedAnalyzer(
            listOf(
                { throw RemoteAnalysisException.Unauthorized("expired") },
                { throw RemoteAnalysisException.Unauthorized("expired again") },
            ),
        )
        val analyzer = AuthRetryingAnalyzer(delegate, auth)

        val e = assertFailsWith<RemoteAnalysisException.Unauthorized> {
            analyzer.analyzeGame(listOf("7g7f"))
        }

        assertEquals("expired again", e.message)
        assertEquals(1, auth.refreshSessionCalls)
        assertEquals(2, delegate.callCount)
        assertTrue(delegate.callCount < 3, "1回リトライした時点で打ち切ること（無限リトライしない）")
    }
}
