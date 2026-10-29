package dev.miyado.shogisupplement.api.gameshare

import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove

object GameShareLimits {
    const val MAX_BODY_BYTES = 64 * 1024
    const val MAX_MAINLINE_MOVES = 512
    const val MAX_BRANCH_MOVES = 256
    const val MAX_SFEN_LENGTH = 256
    const val MAX_GAME_KEY_LENGTH = 128
    const val MAX_END_REASON_LENGTH = 64
}

data class ValidatedGameShare(
    val gameKey: String,
    val initialSfen: String,
    val mainline: List<String>,
    val selectedPly: Int,
    val branch: GameShareBranchJson?,
    val userSide: String,
    val flip: Boolean,
    val winner: String?,
    val endReason: String?,
) {
    fun toSnapshot(id: String): GameShareSnapshotJson = GameShareSnapshotJson(
        id = id,
        version = 1,
        initialSfen = initialSfen,
        mainline = mainline,
        selectedPly = selectedPly,
        branch = branch,
        userSide = userSide,
        flip = flip,
        winner = winner,
        endReason = endReason,
    )
}

object GameShareSnapshotValidator {
    private const val VERSION = 1
    private val SIDES = setOf("sente", "gote")
    private val GAME_KEY = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val WHITESPACE = Regex("\\s+")
    private val BOARD_PIECES = setOf('P', 'L', 'N', 'S', 'G', 'B', 'R', 'K')
    private val PROMOTABLE = setOf('P', 'L', 'N', 'S', 'B', 'R')
    private val HAND_PIECES = setOf('P', 'L', 'N', 'S', 'G', 'B', 'R')
    private val PIECE_LIMITS = mapOf(
        'P' to 18,
        'L' to 4,
        'N' to 4,
        'S' to 4,
        'G' to 4,
        'B' to 2,
        'R' to 2,
    )

    fun validate(request: CreateGameShareRequest): ValidatedGameShare? {
        if (request.version != VERSION) return null
        val gameKey = request.gameKey.trim()
        if (!GAME_KEY.matches(gameKey)) return null

        val initialSfen = canonicalSfen(request.initialSfen) ?: return null
        if (request.mainline.size > GameShareLimits.MAX_MAINLINE_MOVES) return null
        val mainline = request.mainline.toList()
        val board = ShogiBoard.fromSfen(initialSfen)
        for (move in mainline) {
            if (!applyLegal(board, move)) return null
        }

        val branch = request.branch?.let { value ->
            if (value.moves.isEmpty()) return null
            if (value.moves.size > GameShareLimits.MAX_BRANCH_MOVES) return null
            if (value.originPly !in 0..mainline.size) return null
            val branchBoard = ShogiBoard.fromSfen(initialSfen)
            for (index in 0 until value.originPly) {
                if (!applyLegal(branchBoard, mainline[index])) return null
            }
            for (move in value.moves) {
                if (!applyLegal(branchBoard, move)) return null
            }
            GameShareBranchJson(originPly = value.originPly, moves = value.moves.toList())
        }

        val maxPly = if (branch == null) mainline.size else branch.originPly + branch.moves.size
        val minPly = if (branch == null) 0 else branch.originPly
        if (request.selectedPly !in minPly..maxPly) return null

        if (request.userSide !in SIDES) return null
        if (request.winner != null && request.winner !in SIDES) return null
        val endReason = request.endReason?.trim()?.takeIf { it.isNotEmpty() }
        if (endReason != null && endReason.length > GameShareLimits.MAX_END_REASON_LENGTH) return null

        return ValidatedGameShare(
            gameKey = gameKey,
            initialSfen = initialSfen,
            mainline = mainline,
            selectedPly = request.selectedPly,
            branch = branch,
            userSide = request.userSide,
            flip = request.flip,
            winner = request.winner,
            endReason = endReason,
        )
    }

    fun isValidShareId(id: String): Boolean = id.length in 16..64 && GAME_KEY.matches(id)

    private fun applyLegal(board: ShogiBoard, usi: String): Boolean {
        val move = runCatching { ShogiMove.fromUsi(usi) }.getOrNull() ?: return false
        if (board.legalMoves().none { it == move }) return false
        board.push(move)
        return true
    }

    private fun canonicalSfen(sfen: String): String? {
        val trimmed = sfen.trim()
        if (trimmed.length > GameShareLimits.MAX_SFEN_LENGTH) return null
        val parts = trimmed.split(WHITESPACE)
        if (parts.size != 4) return null
        val boardPieces = boardInventory(parts[0]) ?: return null
        if (parts[1] != "b" && parts[1] != "w") return null
        val handPieces = handInventory(parts[2]) ?: return null
        for ((piece, limit) in PIECE_LIMITS) {
            if ((boardPieces[piece] ?: 0) + (handPieces[piece] ?: 0) > limit) return null
        }
        val moveNumber = parts[3].toIntOrNull() ?: return null
        if (moveNumber !in 1..999_999) return null
        val canonical = parts.joinToString(" ")
        val board = runCatching { ShogiBoard.fromSfen(canonical) }.getOrNull() ?: return null
        return canonical.takeIf { board.toSfen() == it }
    }

    private fun boardInventory(board: String): Map<Char, Int>? {
        val ranks = board.split('/')
        if (ranks.size != 9) return null
        var blackKings = 0
        var whiteKings = 0
        val counts = mutableMapOf<Char, Int>()
        for (rank in ranks) {
            var files = 0
            var index = 0
            while (index < rank.length) {
                val current = rank[index]
                when {
                    current == '+' -> {
                        if (index + 1 >= rank.length) return null
                        val piece = rank[index + 1].uppercaseChar()
                        if (piece !in PROMOTABLE) return null
                        counts[piece] = (counts[piece] ?: 0) + 1
                        files++
                        index += 2
                    }
                    current in '1'..'9' -> {
                        files += current.digitToInt()
                        index++
                    }
                    current.uppercaseChar() in BOARD_PIECES -> {
                        if (current == 'K') blackKings++
                        if (current == 'k') whiteKings++
                        val piece = current.uppercaseChar()
                        if (piece != 'K') counts[piece] = (counts[piece] ?: 0) + 1
                        files++
                        index++
                    }
                    else -> return null
                }
                if (files > 9) return null
            }
            if (files != 9) return null
        }
        if (blackKings != 1 || whiteKings != 1) return null
        return counts
    }

    private fun handInventory(hands: String): Map<Char, Int>? {
        if (hands == "-") return emptyMap()
        if (hands.isEmpty()) return null
        val counts = mutableMapOf<Char, Int>()
        var index = 0
        while (index < hands.length) {
            var count = 0
            var hasDigits = false
            while (index < hands.length && hands[index].isDigit()) {
                count = count * 10 + hands[index].digitToInt()
                if (count > 999) return null
                hasDigits = true
                index++
            }
            if (index >= hands.length) return null
            if (hasDigits && count == 0) return null
            if (!hasDigits) count = 1
            val piece = hands[index].uppercaseChar()
            if (piece !in HAND_PIECES) return null
            counts[piece] = (counts[piece] ?: 0) + count
            index++
        }
        return counts.takeIf { it.isNotEmpty() }
    }
}
