package dev.miyado.shogisupplement.server.worker.fakes

import dev.miyado.shogisupplement.server.worker.auth.AppCheckResult
import dev.miyado.shogisupplement.server.worker.auth.AppCheckVerifier

/**
 * トークン文字列が [validTokens] に含まれるかどうかだけで結果を決めるフェイク。
 * 含まれないトークンは常に Invalid（401）を返す。
 */
class FakeAppCheckVerifier(
    private val validTokens: Set<String> = emptySet(),
) : AppCheckVerifier {
    var lastVerifiedToken: String? = null
        private set

    override suspend fun verify(token: String): AppCheckResult {
        lastVerifiedToken = token
        return if (token in validTokens) AppCheckResult.Valid else AppCheckResult.Invalid("invalid app check token")
    }
}
