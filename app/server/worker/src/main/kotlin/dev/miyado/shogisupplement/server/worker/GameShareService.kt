package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.gameshare.*
import dev.miyado.shogisupplement.server.worker.auth.*
import dev.miyado.shogisupplement.server.worker.repo.AppPolicyGate
import dev.miyado.shogisupplement.server.worker.repo.StaticGameShareRepository
import java.security.SecureRandom
import java.util.Base64

sealed interface CreateGameShareOutcome {
    data class Success(val snapshot: GameShareSnapshotJson) : CreateGameShareOutcome
    data object Invalid : CreateGameShareOutcome
    data object SourceMissing : CreateGameShareOutcome
    data class Unauthorized(val reason: String) : CreateGameShareOutcome
    data object UpgradeRequired : CreateGameShareOutcome
}

class GameShareService(
    private val repository: StaticGameShareRepository,
    private val authVerifier: AuthVerifier,
    private val appCheckVerifier: AppCheckVerifier? = null,
    private val appPolicyGate: AppPolicyGate = AppPolicyGate.AlwaysAllow,
    private val shareIdGenerator: () -> String = {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    },
) {
    suspend fun create(
        request: CreateGameShareRequest,
        authorizationHeader: String?,
        appCheckHeader: String?,
        platformHeader: String?,
        buildHeader: String?,
    ): CreateGameShareOutcome {
        val owner = when (val result = authorize(authorizationHeader, appCheckHeader, platformHeader, buildHeader)) {
            is AuthGuard.Valid -> result.userId
            is AuthGuard.Unauthorized -> return CreateGameShareOutcome.Unauthorized(result.reason)
            AuthGuard.UpgradeRequired -> return CreateGameShareOutcome.UpgradeRequired
        }
        val share = GameShareSnapshotValidator.validate(request) ?: return CreateGameShareOutcome.Invalid
        val id = shareIdGenerator()
        if (!repository.reserve(id, owner, share.gameKey)) return CreateGameShareOutcome.SourceMissing
        val snapshot = GameShareSnapshotJson(
            id = id, version = 1, initialSfen = share.initialSfen, mainline = share.mainline,
            selectedPly = share.selectedPly, branch = share.branch, userSide = share.userSide,
            flip = share.flip, winner = share.winner,
            endReason = share.endReason?.takeIf { it in setOf("投了", "詰み", "時間切れ", "切れ負け", "反則負け", "千日手", "持将棋", "中断") },
        )
        repository.upload(snapshot)
        if (!repository.finish(id, owner)) return CreateGameShareOutcome.SourceMissing
        return CreateGameShareOutcome.Success(snapshot)
    }

    private sealed interface AuthGuard {
        data class Valid(val userId: String) : AuthGuard
        data class Unauthorized(val reason: String) : AuthGuard
        data object UpgradeRequired : AuthGuard
    }

    private suspend fun authorize(
        authorizationHeader: String?,
        appCheckHeader: String?,
        platformHeader: String?,
        buildHeader: String?,
    ): AuthGuard {
        if (platformHeader != null && buildHeader != null) {
            val build = buildHeader.toIntOrNull()
            if (build != null && appPolicyGate.isBlocked(platformHeader, build)) {
                return AuthGuard.UpgradeRequired
            }
        }
        if (appCheckVerifier != null) {
            if (appCheckHeader == null) return AuthGuard.Unauthorized("missing app check token")
            when (val result = appCheckVerifier.verify(appCheckHeader)) {
                is AppCheckResult.Invalid -> return AuthGuard.Unauthorized(result.reason)
                AppCheckResult.Valid -> Unit
            }
        }
        val token = extractBearerToken(authorizationHeader)
            ?: return AuthGuard.Unauthorized("missing bearer token")
        val userId = when (val result = authVerifier.verify(token)) {
            is AuthResult.Invalid -> return AuthGuard.Unauthorized(result.reason)
            is AuthResult.Valid -> result.userId
        }
        return AuthGuard.Valid(userId)
    }

}
