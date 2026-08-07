package dev.miyado.shogisupplement.server.worker.fakes

import dev.miyado.shogisupplement.server.worker.auth.TransferSession
import dev.miyado.shogisupplement.server.worker.auth.TransferSessionIssuer
import dev.miyado.shogisupplement.server.worker.ratelimit.IpRateLimiter
import dev.miyado.shogisupplement.server.worker.repo.TransferSecretRepository

/** hashBase64 -> userId の単純な対応表で照合する。 */
class FakeTransferSecretRepository(
    private val byHash: Map<String, String> = emptyMap(),
) : TransferSecretRepository {
    override suspend fun findUserId(keyAuthHashBase64: String): String? = byHash[keyAuthHashBase64]
}

/** userIdからの決定的な文字列でセッションを組み立てるだけのフェイク（値の中身自体は無意味）。 */
class FakeTransferSessionIssuer : TransferSessionIssuer {
    val issuedFor = mutableListOf<String>()

    override suspend fun issueSession(userId: String): TransferSession {
        issuedFor.add(userId)
        return TransferSession(accessToken = "access-for-$userId", refreshToken = "refresh-for-$userId")
    }
}

/** 常に許可 / 常に拒否のいずれかに固定できるフェイク。 */
class FakeIpRateLimiter(private val allow: Boolean = true) : IpRateLimiter {
    val requestedIps = mutableListOf<String>()

    override fun tryAcquire(ip: String): Boolean {
        requestedIps.add(ip)
        return allow
    }
}
