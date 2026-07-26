package dev.miyado.shogisupplement.server.worker.fakes

import dev.miyado.shogisupplement.server.worker.auth.AuthResult
import dev.miyado.shogisupplement.server.worker.auth.AuthVerifier

/**
 * トークン文字列 -> userId の単純なマップで検証結果を決めるフェイク。
 * マップに無いトークンは常に Invalid（401）を返す。
 */
class FakeAuthVerifier(
    private val validTokens: Map<String, String> = emptyMap(),
) : AuthVerifier {
    var lastVerifiedToken: String? = null
        private set

    override suspend fun verify(bearerToken: String): AuthResult {
        lastVerifiedToken = bearerToken
        val userId = validTokens[bearerToken] ?: return AuthResult.Invalid("invalid or expired token")
        return AuthResult.Valid(userId)
    }
}
