package dev.miyado.shogisupplement.server.worker.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dev.miyado.shogisupplement.server.worker.fakes.StaticJwkSetProvider
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// 実ネットワーク・実Supabaseは一切使わない: テストごとにRSA鍵ペアを生成し、そのJWKSetを
// [StaticJwkSetProvider] で固定して検証する（本物のSupabase JWKSと同じRS256運用を模す）。
class SupabaseJwtAuthVerifierTest {

    private val issuer = "https://project-ref.supabase.co/auth/v1"
    private val audience = "authenticated"
    private val now = Instant.parse("2026-07-26T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun generateRsaKey(kid: String): RSAKey =
        RSAKeyGenerator(2048).keyID(kid).generate()

    private fun sign(
        key: RSAKey,
        userId: String = "user-1",
        expiresAt: Instant = now.plusSeconds(3600),
        issuedIssuer: String = issuer,
        issuedAudience: String = audience,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .subject(userId)
            .issuer(issuedIssuer)
            .audience(issuedAudience)
            .expirationTime(Date.from(expiresAt))
            .issueTime(Date.from(now))
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
        jwt.sign(RSASSASigner(key))
        return jwt.serialize()
    }

    @Test
    fun `valid token is accepted and yields sub as userId`() = runTest {
        val key = generateRsaKey("kid-1")
        val provider = StaticJwkSetProvider(JWKSet(key.toPublicJWK()))
        val verifier = SupabaseJwtAuthVerifier(provider, issuer = issuer, audience = audience, clock = clock)

        val result = verifier.verify(sign(key, userId = "user-42"))

        assertIs<AuthResult.Valid>(result)
        assertEquals("user-42", result.userId)
    }

    @Test
    fun `expired token is rejected`() = runTest {
        val key = generateRsaKey("kid-1")
        val provider = StaticJwkSetProvider(JWKSet(key.toPublicJWK()))
        val verifier = SupabaseJwtAuthVerifier(provider, issuer = issuer, audience = audience, clock = clock)

        val expiredToken = sign(key, expiresAt = now.minusSeconds(1))
        val result = verifier.verify(expiredToken)

        assertIs<AuthResult.Invalid>(result)
    }

    @Test
    fun `token signed with an unknown key is rejected`() = runTest {
        val legitKey = generateRsaKey("kid-1")
        val attackerKey = generateRsaKey("kid-1") // 同じkidだが別の鍵ペア＝署名検証で弾かれるべき
        val provider = StaticJwkSetProvider(JWKSet(legitKey.toPublicJWK()))
        val verifier = SupabaseJwtAuthVerifier(provider, issuer = issuer, audience = audience, clock = clock)

        val forgedToken = sign(attackerKey)
        val result = verifier.verify(forgedToken)

        assertIs<AuthResult.Invalid>(result)
    }

    @Test
    fun `unknown kid triggers a forced jwks refresh`() = runTest {
        val key = generateRsaKey("kid-rotated")
        val provider = StaticJwkSetProvider(JWKSet(key.toPublicJWK()))
        val verifier = SupabaseJwtAuthVerifier(provider, issuer = issuer, audience = audience, clock = clock)

        // providerは常に同じ鍵を返すフェイクなので最終的には成功するが、
        // 「未知kidで強制再取得を試みる」経路が呼ばれたことを検証する。
        val token = sign(key)
        // 別kidを騙って最初のキャッシュ参照を空振りさせるため、一旦kidの無いプロバイダで試す
        val emptyProvider = StaticJwkSetProvider(JWKSet())
        val verifierWithEmptyThenFull = SupabaseJwtAuthVerifier(
            object : JwkSetProvider {
                override fun jwkSet(forceRefresh: Boolean): JWKSet =
                    if (forceRefresh) provider.jwkSet(true) else emptyProvider.jwkSet(false)
            },
            issuer = issuer,
            audience = audience,
            clock = clock,
        )

        val result = verifierWithEmptyThenFull.verify(token)
        assertIs<AuthResult.Valid>(result)
    }

    @Test
    fun `issuer mismatch is rejected`() = runTest {
        val key = generateRsaKey("kid-1")
        val provider = StaticJwkSetProvider(JWKSet(key.toPublicJWK()))
        val verifier = SupabaseJwtAuthVerifier(provider, issuer = issuer, audience = audience, clock = clock)

        val token = sign(key, issuedIssuer = "https://evil.example.com/auth/v1")
        val result = verifier.verify(token)

        assertIs<AuthResult.Invalid>(result)
    }

    @Test
    fun `audience mismatch is rejected`() = runTest {
        val key = generateRsaKey("kid-1")
        val provider = StaticJwkSetProvider(JWKSet(key.toPublicJWK()))
        val verifier = SupabaseJwtAuthVerifier(provider, issuer = issuer, audience = audience, clock = clock)

        val token = sign(key, issuedAudience = "anon")
        val result = verifier.verify(token)

        assertIs<AuthResult.Invalid>(result)
    }

    @Test
    fun `malformed token string is rejected`() = runTest {
        val key = generateRsaKey("kid-1")
        val provider = StaticJwkSetProvider(JWKSet(key.toPublicJWK()))
        val verifier = SupabaseJwtAuthVerifier(provider, issuer = issuer, audience = audience, clock = clock)

        val result = verifier.verify("not-a-jwt")
        assertIs<AuthResult.Invalid>(result)
    }

    @Test
    fun `extractBearerToken parses Bearer prefix only`() {
        assertEquals("abc123", extractBearerToken("Bearer abc123"))
        assertEquals(null, extractBearerToken("abc123"))
        assertEquals(null, extractBearerToken(null))
        assertEquals(null, extractBearerToken("Bearer "))
    }
}
