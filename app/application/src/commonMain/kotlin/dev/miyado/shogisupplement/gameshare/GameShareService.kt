package dev.miyado.shogisupplement.gameshare

import dev.miyado.shogisupplement.auth.AuthUser
import kotlinx.coroutines.flow.StateFlow

data class GameShareBranch(
    val originPly: Int,
    val moves: List<String>,
)

data class GameShareDraft(
    val gameKey: String,
    val initialSfen: String,
    val mainline: List<String>,
    val selectedPly: Int,
    val branch: GameShareBranch?,
    val userSide: String,
    val flip: Boolean,
    val winner: String?,
    val endReason: String?,
)

data class GameShareSnapshot(
    val id: String,
    val initialSfen: String,
    val mainline: List<String>,
    val selectedPly: Int,
    val branch: GameShareBranch?,
    val userSide: String,
    val flip: Boolean,
    val winner: String?,
    val endReason: String?,
)

data class GameShareSummary(
    val id: String,
)

sealed interface GameShareResult<out T> {
    data class Success<T>(val value: T) : GameShareResult<T>
    data object Invalid : GameShareResult<Nothing>
    data object Unauthorized : GameShareResult<Nothing>
    data object NotFound : GameShareResult<Nothing>
    data object SourceMissing : GameShareResult<Nothing>
    data object UpgradeRequired : GameShareResult<Nothing>
    data object RateLimited : GameShareResult<Nothing>
    data class Failure(val message: String) : GameShareResult<Nothing>
}

interface GameShareReader {
    suspend fun get(id: String): GameShareResult<GameShareSnapshot>
}

interface GameShareService {
    val sharePageUrl: String? get() = null
    val currentOwner: StateFlow<AuthUser?>
    suspend fun create(draft: GameShareDraft): GameShareResult<GameShareSummary>
}
