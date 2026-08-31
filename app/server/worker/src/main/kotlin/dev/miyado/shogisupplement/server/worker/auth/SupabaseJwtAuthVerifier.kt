package dev.miyado.shogisupplement.server.worker.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.text.ParseException
import java.time.Clock
import java.util.Date

private val log = LoggerFactory.getLogger(SupabaseJwtAuthVerifier::class.java)

/**
 * Supabase Auth が発行するJWT（RS256/ES256・JWKS署名）を検証する。
 *
 * 処理順序（不変条件・変更しないこと）: 署名検証 → exp（期限切れ）→ iss/aud → sub取得。
 * kid未知（鍵ローテーション直後の可能性）の場合は [jwkSetProvider] を強制再取得して1回だけ
 * リトライする。
 *
 * Why not HS256対応: 非対称鍵（SupabaseのJWKS）での検証に統一する。
 */
class SupabaseJwtAuthVerifier(
    private val jwkSetProvider: JwkSetProvider,
    private val issuer: String,
    private val audience: String = "authenticated",
    private val clock: Clock = Clock.systemUTC(),
) : AuthVerifier {

    override suspend fun verify(bearerToken: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val jwt = SignedJWT.parse(bearerToken)
            val kid = jwt.header.keyID

            var jwkSet = jwkSetProvider.jwkSet()
            var jwk = kid?.let { jwkSet.getKeyByKeyId(it) }
            if (jwk == null) {
                // kidが見つからない＝鍵ローテーションの可能性。キャッシュを無視して1回だけ再取得する
                jwkSet = jwkSetProvider.jwkSet(forceRefresh = true)
                jwk = kid?.let { jwkSet.getKeyByKeyId(it) }
            }
            if (jwk == null) return@withContext AuthResult.Invalid("unknown key id")

            val verifier: JWSVerifier = when {
                jwt.header.algorithm == JWSAlgorithm.RS256 && jwk is RSAKey ->
                    RSASSAVerifier(jwk.toRSAPublicKey())
                jwt.header.algorithm == JWSAlgorithm.ES256 && jwk is ECKey ->
                    ECDSAVerifier(jwk.toECPublicKey())
                else -> return@withContext AuthResult.Invalid("unsupported algorithm: ${jwt.header.algorithm}")
            }

            if (!jwt.verify(verifier)) {
                return@withContext AuthResult.Invalid("signature verification failed")
            }

            val claims = jwt.jwtClaimsSet

            val exp = claims.expirationTime
                ?: return@withContext AuthResult.Invalid("missing exp claim")
            if (exp.before(Date(clock.millis()))) {
                return@withContext AuthResult.Invalid("token expired")
            }

            if (issuer.isNotBlank() && claims.issuer != issuer) {
                return@withContext AuthResult.Invalid("issuer mismatch")
            }
            if (audience.isNotBlank() && !claims.audience.orEmpty().contains(audience)) {
                return@withContext AuthResult.Invalid("audience mismatch")
            }

            val userId = claims.subject
                ?: return@withContext AuthResult.Invalid("missing sub claim")

            AuthResult.Valid(userId)
        } catch (e: ParseException) {
            AuthResult.Invalid("malformed token")
        } catch (e: Exception) {
            log.warn("token verification failed", e)
            AuthResult.Invalid("token verification error")
        }
    }
}
