package dev.miyado.shogisupplement.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * 認証リポジトリのインターフェース。
 * 実装: SupabaseAuthRepository（androidApp）、FakeAuthRepository（テスト）
 *
 * v1 = 匿名認証のみ。メール/パスワード認証は廃止（個人情報を収集しない）。
 */
interface AuthRepository {
    /** ログイン中ユーザー情報の Flow。未ログインは null。 */
    val currentUser: StateFlow<AuthUser?>

    /**
     * 匿名ユーザーとしてサインインする（Supabase Anonymous Auth）。
     * 初回は新しい匿名アカウントを自動発行する。uid はユーザーに表示しない。
     * @return 成功時 Result.success(Unit)、失敗時 Result.failure(exception)
     */
    suspend fun signInAnonymously(): Result<Unit>

    /**
     * ログアウトする。
     * @return 成功時 Result.success(Unit)、失敗時 Result.failure(exception)
     */
    suspend fun signOut(): Result<Unit>

    /**
     * アカウントを削除する。
     * サーバー上の棋譜・解析結果は auth.users の cascade で全削除される。
     * 成功時はローカルセッションもクリアし、currentUser が null になる。
     * @return 成功時 Result.success(Unit)、失敗時 Result.failure(exception)
     */
    suspend fun deleteAccount(): Result<Unit>
}

/** ログイン中ユーザーの最小情報。uid はサーバー連携用のみ（UI に表示しない）。
 * メールアドレスは保持しない（匿名認証のみ・不要な個人情報面を作らない）。 */
data class AuthUser(
    val id: String,
)
