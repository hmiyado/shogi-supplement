package dev.miyado.shogisupplement.server.worker.fakes

import com.nimbusds.jose.jwk.JWKSet
import dev.miyado.shogisupplement.server.worker.auth.JwkSetProvider

/** 実ネットワークを使わず固定のJWKSetを返すテスト用実装。 */
class StaticJwkSetProvider(private val jwkSet: JWKSet) : JwkSetProvider {
    var forceRefreshCallCount: Int = 0
        private set

    override fun jwkSet(forceRefresh: Boolean): JWKSet {
        if (forceRefresh) forceRefreshCallCount++
        return jwkSet
    }
}
