package dev.miyado.shogisupplement.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * テスト用 AuthRepository 実装（匿名認証のみ）。
 * signInAnonymously / signOut / deleteAccount を呼び出すと currentUser が自動的に更新される。
 *
 * @param initialUser 初期ユーザー（デフォルトは未ログイン）
 * @param signInAnonymouslyResult signInAnonymously の返却値（デフォルトは成功）
 * @param deleteAccountResult deleteAccount の返却値（デフォルトは成功）
 */
class FakeAuthRepository(
    initialUser: AuthUser? = null,
    private val signInAnonymouslyResult: Result<Unit> = Result.success(Unit),
    private val deleteAccountResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {

    /** signInAnonymously が呼ばれた回数（テスト検証用）。 */
    var signInAnonymouslyCalls: Int = 0
        private set

    /** deleteAccount が呼ばれた回数（テスト検証用）。 */
    var deleteAccountCalls: Int = 0
        private set

    private val _currentUser = MutableStateFlow(initialUser)
    override val currentUser: StateFlow<AuthUser?> = _currentUser

    override suspend fun signInAnonymously(): Result<Unit> {
        signInAnonymouslyCalls++
        if (signInAnonymouslyResult.isSuccess) {
            _currentUser.value = AuthUser(id = "fake-anon-uid")
        }
        return signInAnonymouslyResult
    }

    override suspend fun signOut(): Result<Unit> {
        _currentUser.value = null
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        deleteAccountCalls++
        if (deleteAccountResult.isSuccess) {
            _currentUser.value = null
        }
        return deleteAccountResult
    }
}
