package dev.miyado.shogisupplement.supabase

import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/** 接続先をまたいだ旧セッションの移行を行わない独立した保存先。 */
interface AuthSessionStorage {
    fun load(): String?
    fun save(value: String)
    fun clear()
}

internal class IsolatedSessionManager(private val storage: AuthSessionStorage) : SessionManager {
    override suspend fun saveSession(session: UserSession) = storage.save(Json.encodeToString(UserSession.serializer(), session))
    override suspend fun loadSession(): UserSession = requireNotNull(loadSessionOrNull())
    override suspend fun loadSessionOrNull(): UserSession? = storage.load()?.let {
        runCatching { Json.decodeFromString(UserSession.serializer(), it) }.getOrNull()
    }
    override suspend fun deleteSession() = storage.clear()
}
