package dev.miyado.shogisupplement.kifu

import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.util.sha256Hex

class GameImporter(private val repository: GameRepository) {

    sealed class Outcome {
        data class Imported(val gameId: Long, val alreadyExisted: Boolean) : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    fun importGame(
        kifContent: String,
        fileName: String,
        userSide: String?,
        ratingService: String? = null,
        ratingRaw: Long? = null,
        ratingRule: String? = null,
        contentHash: String? = null,
        sourcePlaceOverride: String? = null,
    ): Outcome = try {
        val effectiveHash = contentHash ?: sha256Hex(kifContent)
        repository.getByHash(effectiveHash)?.let {
            return Outcome.Imported(it, alreadyExisted = true)
        }
        val game = KifParser().parse(kifContent)
        val source = KifuDecomposer.classifySource(kifContent, game.headers["場所"], game.headers["棋戦"])
        val players = KifuDecomposer.resolvePlayers(source, game.headers)
        val gameId = repository.savePendingGame(
            fileName = fileName,
            contentHash = effectiveHash,
            moves = game.moves,
            headers = players.headers,
            kifText = kifContent,
            userSide = userSide,
            ratingService = ratingService,
            ratingRaw = ratingRaw,
            ratingRule = ratingRule,
            sourcePlace = sourcePlaceOverride ?: source.wireValue,
            gameWinner = game.winner,
            endReason = game.endReason,
            senteRating = players.senteRating,
            goteRating = players.goteRating,
        )
        Outcome.Imported(gameId, alreadyExisted = false)
    } catch (e: Exception) {
        Outcome.Failed(e.message ?: AppStrings.UNKNOWN_ERROR)
    }
}
