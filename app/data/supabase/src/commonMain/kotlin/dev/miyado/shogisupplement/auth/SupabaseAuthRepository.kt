package dev.miyado.shogisupplement.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Supabase Auth を使ったリポジトリ実装（匿名認証のみ）。
 * 共有 SupabaseClient（Auth + Postgrest プラグイン済み）を受け取る。
 */
class SupabaseAuthRepository(
    private val supabase: SupabaseClient,
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + authIoDispatcher)

    override val currentUser: StateFlow<AuthUser?> = supabase.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val user = status.session.user
                    user?.let { AuthUser(id = it.id) }
                }
                else -> null
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    override suspend fun signInAnonymously(): Result<Unit> = runCatching {
        supabase.auth.signInAnonymously()
    }

    override suspend fun accessToken(): String? = supabase.auth.currentAccessTokenOrNull()

    override suspend fun refreshSession(): Result<Unit> = runCatching {
        supabase.auth.refreshCurrentSession()
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        supabase.auth.signOut()
    }

    override suspend fun importSession(refreshToken: String): Result<Unit> = runCatching {
        // Why not importAuthToken: expiresIn=0 のセッションを組むため必ず期限切れ扱いになり、
        // 認証の確定が join されない別コルーチンへ逃げる（成功が戻った時点ではまだ未認証）。
        val session = supabase.auth.refreshSession(refreshToken)
        supabase.auth.importSession(session, autoRefresh = true)
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        supabase.postgrest.rpc("delete_user")
        // サーバー上のユーザーは既に存在しないため、ローカルセッションのみ破棄する
        // （GLOBAL だとサーバーの logout エンドポイントを呼んで 4xx になり得る）。
        supabase.auth.signOut(SignOutScope.LOCAL)
    }

    companion object {
        /**
         * URL と anon key から SupabaseAuthRepository を生成するファクトリ。
         * Auth + Postgrest の両プラグインを持つクライアントを生成する。
         */
        fun create(supabaseUrl: String, supabaseKey: String): SupabaseAuthRepository {
            val client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseKey,
            ) {
                install(Auth)
                install(Postgrest)
            }
            return SupabaseAuthRepository(client)
        }
    }
}
