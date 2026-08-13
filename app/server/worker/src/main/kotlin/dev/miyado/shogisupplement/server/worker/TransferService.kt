package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.transfer.TransferRequest
import dev.miyado.shogisupplement.api.transfer.TransferSessionJson
import dev.miyado.shogisupplement.server.worker.auth.AppCheckResult
import dev.miyado.shogisupplement.server.worker.auth.AppCheckVerifier
import dev.miyado.shogisupplement.server.worker.auth.TransferSessionIssuer
import dev.miyado.shogisupplement.server.worker.ratelimit.IpRateLimiter
import dev.miyado.shogisupplement.server.worker.repo.AppPolicyGate
import dev.miyado.shogisupplement.server.worker.repo.TransferSecretRepository
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

sealed class TransferOutcome {
    data class Success(val session: TransferSessionJson) : TransferOutcome()

    /** k_authが不正（base64でない等）。 */
    data object BadRequest : TransferOutcome()

    /** App Check検証失敗。 */
    data class Unauthorized(val reason: String) : TransferOutcome()

    /** アプリ版情報のbuildがapp_policy.min_build未満。 */
    data object UpgradeRequired : TransferOutcome()

    /** IPレート制限超過。 */
    data object RateLimited : TransferOutcome()

    /** k_authに一致するuser_transfer_secretsの行が無い。理由を漏らさない汎用404として扱う。 */
    data object NotFound : TransferOutcome()
}

// 認可の順序（不変条件）: 強制アップデート検証（ヘッダ欠如はfail-open）→
// App Check検証（有効時のみ）→ IPレート制限 → リクエストボディのk_authをbase64デコード →
// SHA-256(k_auth)でuser_transfer_secretsを照合 → 一致すればセッション発行。
// JWT検証は行わない（未ログイン端末から叩く前提のエンドポイントのため）。
@OptIn(ExperimentalEncodingApi::class)
class TransferService(
    private val transferSecretRepository: TransferSecretRepository,
    private val sessionIssuer: TransferSessionIssuer,
    private val rateLimiter: IpRateLimiter,
    private val appCheckVerifier: AppCheckVerifier? = null,
    private val appPolicyGate: AppPolicyGate = AppPolicyGate.AlwaysAllow,
) {
    suspend fun handle(
        request: TransferRequest,
        callerIp: String,
        appCheckHeader: String?,
        platformHeader: String?,
        buildHeader: String?,
    ): TransferOutcome {
        if (platformHeader != null && buildHeader != null) {
            val build = buildHeader.toIntOrNull()
            if (build != null && appPolicyGate.isBlocked(platformHeader, build)) {
                return TransferOutcome.UpgradeRequired
            }
        }

        if (appCheckVerifier != null) {
            if (appCheckHeader == null) {
                return TransferOutcome.Unauthorized("missing app check token")
            }
            when (val appCheck = appCheckVerifier.verify(appCheckHeader)) {
                is AppCheckResult.Invalid -> return TransferOutcome.Unauthorized(appCheck.reason)
                AppCheckResult.Valid -> Unit
            }
        }

        if (!rateLimiter.tryAcquire(callerIp)) {
            return TransferOutcome.RateLimited
        }

        // k_auth自体はログに出さない。デコード失敗はここで弾き、以降の処理には
        // 生のk_authバイト列をローカル変数の外へ持ち出さない。
        val kAuthBytes = runCatching { Base64.decode(request.kAuth) }.getOrNull()
            ?: return TransferOutcome.BadRequest
        if (kAuthBytes.isEmpty()) return TransferOutcome.BadRequest

        val hashBase64 = Base64.encode(MessageDigest.getInstance("SHA-256").digest(kAuthBytes))
        val userId = transferSecretRepository.findUserId(hashBase64) ?: return TransferOutcome.NotFound

        val session = sessionIssuer.issueSession(userId)
        return TransferOutcome.Success(
            TransferSessionJson(accessToken = session.accessToken, refreshToken = session.refreshToken),
        )
    }
}
