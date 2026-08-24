package dev.miyado.shogisupplement.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * 認証リポジトリのインターフェース。
 * v1 = 匿名認証のみ。メール/パスワード認証は廃止（個人情報を収集しない）。
 */
interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>

    /** 初回は新しい匿名アカウントを自動発行する。 */
    suspend fun signInAnonymously(): Result<Unit>

    /** トークンは短命で自動更新されるため、値を保持せずリクエストごとに取り直す。 */
    suspend fun accessToken(): String?

    /** signInAnonymously と違い新規アカウントは発行せず、既存セッションのトークンだけを更新する。 */
    suspend fun refreshSession(): Result<Unit>

    suspend fun signOut(): Result<Unit>

    /**
     * サーバー上の棋譜・解析結果は auth.users の cascade で全削除される。
     * 成功時はローカルセッションもクリアし、currentUser が null になる。
     */
    suspend fun deleteAccount(): Result<Unit>

    /**
     * 外部から取得した refresh token でこの端末のセッションを差し替える。新規アカウントは
     * 発行せず、成功して戻った時点で currentUser は渡されたトークンのユーザーを指す。
     */
    suspend fun importSession(refreshToken: String): Result<Unit>
}

/** メールアドレスは保持しない（匿名認証のみ・不要な個人情報面を作らない）。 */
data class AuthUser(
    val id: String,
)
