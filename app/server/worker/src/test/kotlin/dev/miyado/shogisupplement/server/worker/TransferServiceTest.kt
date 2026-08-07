package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.transfer.TransferRequest
import dev.miyado.shogisupplement.server.worker.fakes.FakeAppCheckVerifier
import dev.miyado.shogisupplement.server.worker.fakes.FakeAppPolicyGate
import dev.miyado.shogisupplement.server.worker.fakes.FakeIpRateLimiter
import dev.miyado.shogisupplement.server.worker.fakes.FakeTransferSecretRepository
import dev.miyado.shogisupplement.server.worker.fakes.FakeTransferSessionIssuer
import kotlinx.coroutines.test.runTest
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class TransferServiceTest {

    // クライアント側（TransferSecretKeys.authKeyHash相当）と同じ手順で
    // 「登録済みハッシュ」と「登録時に送ったk_auth」のペアを用意する。
    private val rawKAuth = ByteArray(32) { it.toByte() }
    private val kAuthBase64 = Base64.encode(rawKAuth)
    private val kAuthHashBase64 = Base64.encode(MessageDigest.getInstance("SHA-256").digest(rawKAuth))

    private fun buildService(
        byHash: Map<String, String> = mapOf(kAuthHashBase64 to "user-1"),
        rateLimiter: FakeIpRateLimiter = FakeIpRateLimiter(allow = true),
        appCheckVerifier: FakeAppCheckVerifier? = null,
        appPolicyGate: FakeAppPolicyGate = FakeAppPolicyGate(),
        sessionIssuer: FakeTransferSessionIssuer = FakeTransferSessionIssuer(),
    ) = TransferService(
        transferSecretRepository = FakeTransferSecretRepository(byHash),
        sessionIssuer = sessionIssuer,
        rateLimiter = rateLimiter,
        appCheckVerifier = appCheckVerifier,
        appPolicyGate = appPolicyGate,
    )

    @Test
    fun `登録済みk_authなら旧user_idのセッションを発行する`() = runTest {
        val issuer = FakeTransferSessionIssuer()
        val service = buildService(sessionIssuer = issuer)

        val outcome = service.handle(
            TransferRequest(kAuth = kAuthBase64),
            callerIp = "203.0.113.1",
            appCheckHeader = null,
            platformHeader = null,
            buildHeader = null,
        )

        val success = outcome as? TransferOutcome.Success
        assertTrue(success != null, "expected Success but was $outcome")
        assertEquals("access-for-user-1", success.session.accessToken)
        assertEquals("refresh-for-user-1", success.session.refreshToken)
        assertEquals(listOf("user-1"), issuer.issuedFor)
    }

    @Test
    fun `一致しないk_authは理由を出し分けずNotFoundを返す`() = runTest {
        val unknownKAuth = Base64.encode(ByteArray(32) { (it + 1).toByte() })
        val service = buildService()

        val outcome = service.handle(
            TransferRequest(kAuth = unknownKAuth),
            callerIp = "203.0.113.1",
            appCheckHeader = null,
            platformHeader = null,
            buildHeader = null,
        )

        assertEquals(TransferOutcome.NotFound, outcome)
    }

    @Test
    fun `base64として不正なk_authはBadRequest`() = runTest {
        val service = buildService()

        val outcome = service.handle(
            TransferRequest(kAuth = "!!!not-base64!!!"),
            callerIp = "203.0.113.1",
            appCheckHeader = null,
            platformHeader = null,
            buildHeader = null,
        )

        assertEquals(TransferOutcome.BadRequest, outcome)
    }

    @Test
    fun `App Check有効時にヘッダが無ければUnauthorizedでレート制限まで到達しない`() = runTest {
        val rateLimiter = FakeIpRateLimiter(allow = true)
        val service = buildService(
            appCheckVerifier = FakeAppCheckVerifier(setOf("valid-app-check-token")),
            rateLimiter = rateLimiter,
        )

        val outcome = service.handle(
            TransferRequest(kAuth = kAuthBase64),
            callerIp = "203.0.113.1",
            appCheckHeader = null,
            platformHeader = null,
            buildHeader = null,
        )

        assertTrue(outcome is TransferOutcome.Unauthorized)
        assertTrue(rateLimiter.requestedIps.isEmpty(), "App Check失敗時点でレート制限には到達しないはず")
    }

    @Test
    fun `App Check有効時に不正トークンはUnauthorized`() = runTest {
        val service = buildService(appCheckVerifier = FakeAppCheckVerifier(setOf("valid-app-check-token")))

        val outcome = service.handle(
            TransferRequest(kAuth = kAuthBase64),
            callerIp = "203.0.113.1",
            appCheckHeader = "wrong-token",
            platformHeader = null,
            buildHeader = null,
        )

        assertTrue(outcome is TransferOutcome.Unauthorized)
    }

    @Test
    fun `ブロック対象のplatform_buildはUpgradeRequired`() = runTest {
        val service = buildService(appPolicyGate = FakeAppPolicyGate(setOf("ios")))

        val outcome = service.handle(
            TransferRequest(kAuth = kAuthBase64),
            callerIp = "203.0.113.1",
            appCheckHeader = null,
            platformHeader = "ios",
            buildHeader = "1",
        )

        assertEquals(TransferOutcome.UpgradeRequired, outcome)
    }

    @Test
    fun `platform_buildヘッダのどちらかが無ければ強制アップデート検証をスキップする`() = runTest {
        val service = buildService(appPolicyGate = FakeAppPolicyGate(setOf("ios")))

        val outcome = service.handle(
            TransferRequest(kAuth = kAuthBase64),
            callerIp = "203.0.113.1",
            appCheckHeader = null,
            platformHeader = "ios",
            buildHeader = null,
        )

        assertTrue(outcome is TransferOutcome.Success, "ヘッダが片方欠けている旧クライアント互換ケース")
    }

    @Test
    fun `IPレート制限を超えるとRateLimited`() = runTest {
        val service = buildService(rateLimiter = FakeIpRateLimiter(allow = false))

        val outcome = service.handle(
            TransferRequest(kAuth = kAuthBase64),
            callerIp = "203.0.113.1",
            appCheckHeader = null,
            platformHeader = null,
            buildHeader = null,
        )

        assertEquals(TransferOutcome.RateLimited, outcome)
    }

    @Test
    fun `レート制限は呼び出し元IPで判定する`() = runTest {
        val rateLimiter = FakeIpRateLimiter(allow = true)
        val service = buildService(rateLimiter = rateLimiter)

        service.handle(
            TransferRequest(kAuth = kAuthBase64),
            callerIp = "198.51.100.7",
            appCheckHeader = null,
            platformHeader = null,
            buildHeader = null,
        )

        assertEquals(listOf("198.51.100.7"), rateLimiter.requestedIps)
    }
}
