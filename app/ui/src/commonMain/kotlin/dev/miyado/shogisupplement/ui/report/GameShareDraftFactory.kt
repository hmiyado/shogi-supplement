package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.gameshare.GameShareBranch
import dev.miyado.shogisupplement.gameshare.GameShareDraft

object GameShareDraftFactory {
    fun from(
        game: GameRecord,
        displayedPly: Int,
        studyState: StudyState?,
        fallbackFlip: Boolean,
    ): GameShareDraft? {
        val userSide = game.userSide?.takeIf { it == "sente" || it == "gote" } ?: return null
        val mainline = game.movesUsi
        val path = studyState?.let {
            val start = game.movesUsi.take(it.originAbsolutePly)
            start + it.moves
        } ?: mainline.take(displayedPly.coerceIn(0, mainline.size))

        var common = 0
        while (common < path.size && common < mainline.size && path[common] == mainline[common]) {
            common++
        }
        val branch = if (common < path.size) {
            GameShareBranch(originPly = common, moves = path.drop(common))
        } else {
            null
        }

        return GameShareDraft(
            gameKey = game.contentHash.ifBlank { "local-${game.id}" },
            initialSfen = ShogiBoard().toSfen(),
            mainline = mainline,
            selectedPly = path.size,
            branch = branch,
            userSide = userSide,
            flip = studyState?.flip ?: fallbackFlip,
            winner = game.gameWinner,
            endReason = game.endReason,
        )
    }
}
