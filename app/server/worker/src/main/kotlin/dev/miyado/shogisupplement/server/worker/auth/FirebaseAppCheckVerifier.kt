package dev.miyado.shogisupplement.server.worker.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.time.Clock
import java.util.Date

/**
 * Firebase App Check が発行するアテステーショントークン（RS256・JWKS署名）を検証する。
 * クライアント側でApp Attest（iOS）/ Play Integrity（Android）を経て取得したトークンが
 * 正規のFirebaseプロジェクトから発行されたことだけを確認する（sub＝アプリIDは見ない。
 * 匿名アカウント量産対策はアプリの真正性を見れば十分で、個別アプリの識別は不要なため）。
 *
 * 処理順序は[SupabaseJwtAuthVerifier]と同型: 署名検証 → exp → iss/aud。
 *
 * Why not sub/appId検証: App Checkの目的は「本物のアプリバイナリからのリクエストか」の
 * 判定であり、アプリの種類（iOS/Android）ごとの分岐は不要（同じプロジェクト番号に閉じる）。
 */
class FirebaseAppCheckVerifier(
    private val jwkSetProvider: JwkSetProvider,
    projectNumber: String,
    private val clock: Clock = Clock.systemUTC(),
) : AppCheckVerifier {

    private val issuer = "https://firebaseappcheck.googleapis.com/$projectNumber"
    private val audienceSuffix = "projects/$projectNumber"

    override suspend fun verify(token: String): AppCheckResult = withContext(Dispatchers.IO) {
        try {
            val jwt = SignedJWT.parse(token)
            val kid = jwt.header.keyID

            var jwkSet = jwkSetProvider.jwkSet()
            var jwk = kid?.let { jwkSet.getKeyByKeyId(it) }
            if (jwk == null) {
                // kidが見つからない＝鍵ローテーションの可能性。キャッシュを無視して1回だけ再取得する
                jwkSet = jwkSetProvider.jwkSet(forceRefresh = true)
                jwk = kid?.let { jwkSet.getKeyByKeyId(it) }
            }
            if (jwk == null) return@withContext AppCheckResult.Invalid("unknown key id")

            if (jwt.header.algorithm != JWSAlgorithm.RS256 || jwk !is RSAKey) {
                return@withContext AppCheckResult.Invalid("unsupported algorithm: ${jwt.header.algorithm}")
            }
            if (!jwt.verify(RSASSAVerifier(jwk.toRSAPublicKey()))) {
                return@withContext AppCheckResult.Invalid("signature verification failed")
            }

            val claims = jwt.jwtClaimsSet

            val exp = claims.expirationTime
                ?: return@withContext AppCheckResult.Invalid("missing exp claim")
            if (exp.before(Date(clock.millis()))) {
                return@withContext AppCheckResult.Invalid("token expired")
            }

            if (claims.issuer != issuer) {
                return@withContext AppCheckResult.Invalid("issuer mismatch")
            }
            if (!claims.audience.orEmpty().contains(audienceSuffix)) {
                return@withContext AppCheckResult.Invalid("audience mismatch")
            }

            AppCheckResult.Valid
        } catch (e: ParseException) {
            AppCheckResult.Invalid("malformed token")
        } catch (e: Exception) {
            AppCheckResult.Invalid(e.message ?: "token verification error")
        }
    }

    companion object {
        const val JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks"
    }
}
