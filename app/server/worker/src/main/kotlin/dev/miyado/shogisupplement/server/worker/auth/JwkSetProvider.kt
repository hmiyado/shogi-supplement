package dev.miyado.shogisupplement.server.worker.auth

import com.nimbusds.jose.jwk.JWKSet
import java.net.URI
import java.time.Clock
import java.time.Duration

// デフォルト引数（forceRefresh）を持つため `fun interface`（SAM変換）にはできない
// （Kotlinの制約: SAM抽象メソッドはデフォルト値を持てない）。
interface JwkSetProvider {
    /** @param forceRefresh 未知のkid（鍵ローテーションの可能性）のとき呼び出し側がtrueを指定する。 */
    fun jwkSet(forceRefresh: Boolean = false): JWKSet
}

class RemoteJwkSetProvider(
    private val jwksUrl: String,
    private val cacheTtl: Duration = Duration.ofMinutes(10),
    private val clock: Clock = Clock.systemUTC(),
    // JWKSet.load(URL) をそのまま使うとテスト時にモック差し替えできないため関数として注入する。
    private val loader: (String) -> JWKSet = { url -> JWKSet.load(URI(url).toURL()) },
) : JwkSetProvider {

    @Volatile
    private var cached: JWKSet? = null

    @Volatile
    private var fetchedAtMillis: Long = 0L

    private val lock = Any()

    override fun jwkSet(forceRefresh: Boolean): JWKSet {
        if (!forceRefresh) {
            cached?.let { if (isFresh()) return it }
        }
        synchronized(lock) {
            if (!forceRefresh) {
                cached?.let { if (isFresh()) return it }
            }
            val fresh = loader(jwksUrl)
            cached = fresh
            fetchedAtMillis = clock.millis()
            return fresh
        }
    }

    private fun isFresh(): Boolean = clock.millis() - fetchedAtMillis < cacheTtl.toMillis()
}
