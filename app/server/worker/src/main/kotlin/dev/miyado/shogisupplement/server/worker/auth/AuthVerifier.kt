package dev.miyado.shogisupplement.server.worker.auth

sealed class AuthResult {
    data class Valid(val userId: String) : AuthResult()
    data class Invalid(val reason: String) : AuthResult()
}

// 本番実装は [SupabaseJwtAuthVerifier]。テストはこのインターフェースを直接フェイクで差し替える。
interface AuthVerifier {
    suspend fun verify(bearerToken: String): AuthResult
}

/** "Bearer xxx" ヘッダ値からトークン本体を取り出す。形式不正なら null。 */
fun extractBearerToken(authorizationHeader: String?): String? {
    if (authorizationHeader == null) return null
    val prefix = "Bearer "
    if (!authorizationHeader.startsWith(prefix)) return null
    val token = authorizationHeader.removePrefix(prefix).trim()
    return token.ifBlank { null }
}
