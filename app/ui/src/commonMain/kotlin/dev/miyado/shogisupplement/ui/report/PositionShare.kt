package dev.miyado.shogisupplement.ui.report

/** 共有URLへ載せる局面。SFENが手番・持ち駒・手数を含む。 */
data class SharedPosition(val sfen: String, val flip: Boolean) {
    val moveNumber: Int
        get() = sfen.split(' ').getOrNull(3)?.toIntOrNull() ?: 1
}

sealed interface PositionShareParse {
    data object Absent : PositionShareParse
    data class Valid(val position: SharedPosition) : PositionShareParse
    data object Invalid : PositionShareParse
}

/** 局面フラグメントはクエリやサーバーへ送らず、ブラウザ内で検証する。 */
object PositionShare {
    private const val VERSION = "v1"
    private const val FRAGMENT_PREFIX = "position"
    private const val MAX_FRAGMENT_LENGTH = 2_048
    private const val MAX_SFEN_LENGTH = 256
    private const val MAX_HAND_COUNT = 999

    private val boardPieces = setOf('P', 'L', 'N', 'S', 'G', 'B', 'R', 'K')
    private val promotablePieces = setOf('P', 'L', 'N', 'S', 'B', 'R')
    private val handPieces = setOf('P', 'L', 'N', 'S', 'G', 'B', 'R')
    private val pieceLimits = mapOf(
        'P' to 18,
        'L' to 4,
        'N' to 4,
        'S' to 4,
        'G' to 4,
        'B' to 2,
        'R' to 2,
    )

    fun position(sfen: String, flip: Boolean): SharedPosition? =
        canonicalSfen(sfen)?.let { SharedPosition(it, flip) }

    fun parseFragment(fragment: String?): PositionShareParse {
        val raw = fragment?.removePrefix("#").orEmpty()
        if (raw.isEmpty()) return PositionShareParse.Absent
        if (raw.length > MAX_FRAGMENT_LENGTH) return PositionShareParse.Invalid

        var version: String? = null
        var sfen: String? = null
        var flip: String? = null
        var hasPosition = false

        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            if (separator <= 0) return PositionShareParse.Invalid
            val key = percentDecode(part.substring(0, separator)) ?: return PositionShareParse.Invalid
            val value = percentDecode(part.substring(separator + 1)) ?: return PositionShareParse.Invalid
            when (key) {
                FRAGMENT_PREFIX -> {
                    if (hasPosition) return PositionShareParse.Invalid
                    hasPosition = true
                    version = value
                }
                "sfen" -> {
                    if (sfen != null) return PositionShareParse.Invalid
                    sfen = value
                }
                "flip" -> {
                    if (flip != null) return PositionShareParse.Invalid
                    flip = value
                }
            }
        }

        if (!hasPosition) return PositionShareParse.Absent
        if (version != VERSION) return PositionShareParse.Invalid
        val parsedSfen = sfen ?: return PositionShareParse.Invalid
        val parsedFlip = when (flip) {
            null, "0", "false" -> false
            "1", "true" -> true
            else -> return PositionShareParse.Invalid
        }
        val position = position(parsedSfen, parsedFlip) ?: return PositionShareParse.Invalid
        return PositionShareParse.Valid(position)
    }

    private fun canonicalSfen(sfen: String): String? {
        if (sfen.length > MAX_SFEN_LENGTH) return null
        val parts = sfen.trim().split(Regex("\\s+"))
        if (parts.size != 4) return null
        val boardCounts = boardPieceCounts(parts[0]) ?: return null
        if (parts[1] != "b" && parts[1] != "w") return null
        val handCounts = handPieceCounts(parts[2]) ?: return null
        for ((piece, limit) in pieceLimits) {
            if ((boardCounts[piece] ?: 0) + (handCounts[piece] ?: 0) > limit) return null
        }
        val moveNumber = parts[3].toIntOrNull() ?: return null
        if (moveNumber !in 1..999_999) return null
        return parts.joinToString(" ")
    }

    private fun boardPieceCounts(board: String): Map<Char, Int>? {
        val boardRanks = board.split('/')
        if (boardRanks.size != 9) return null

        val counts = mutableMapOf<Char, Int>()
        var blackKings = 0
        var whiteKings = 0

        fun add(piece: Char) {
            counts[piece] = (counts[piece] ?: 0) + 1
        }

        for (rank in boardRanks) {
            if (rank.isEmpty()) return null
            var files = 0
            var index = 0
            while (index < rank.length) {
                val current = rank[index]
                when {
                    current == '+' -> {
                        if (index + 1 >= rank.length) return null
                        val promoted = rank[index + 1].uppercaseChar()
                        if (promoted !in promotablePieces) return null
                        add(promoted)
                        files++
                        index += 2
                    }
                    current in '1'..'9' -> {
                        files += current.digitToInt()
                        index++
                    }
                    else -> {
                        val piece = current.uppercaseChar()
                        if (piece !in boardPieces) return null
                        if (piece == 'K') {
                            if (current.isUpperCase()) blackKings++ else whiteKings++
                        }
                        add(piece)
                        files++
                        index++
                    }
                }
                if (files > 9) return null
            }
            if (files != 9) return null
        }

        if (blackKings != 1 || whiteKings != 1) return null
        return counts
    }

    private fun handPieceCounts(hands: String): Map<Char, Int>? {
        val counts = mutableMapOf<Char, Int>()
        if (hands == "-") return counts
        if (hands.isEmpty()) return null

        var index = 0
        var hasPiece = false
        while (index < hands.length) {
            var count = 0
            var hasDigits = false
            while (index < hands.length && hands[index].isDigit()) {
                count = count * 10 + hands[index].digitToInt()
                if (count > MAX_HAND_COUNT) return null
                hasDigits = true
                index++
            }
            if (index >= hands.length) return null
            if (hasDigits && count == 0) return null
            if (!hasDigits) count = 1
            val piece = hands[index].uppercaseChar()
            if (piece !in handPieces) return null
            counts[piece] = (counts[piece] ?: 0) + count
            hasPiece = true
            index++
        }
        return if (hasPiece) counts else null
    }

    private fun percentDecode(value: String): String? {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '%') {
                if (index + 2 >= value.length) return null
                val high = hexValue(value[index + 1]) ?: return null
                val low = hexValue(value[index + 2]) ?: return null
                bytes.add(((high shl 4) or low).toByte())
                index += 3
            } else {
                val end = if (current.isHighSurrogate() && index + 1 < value.length) index + 2 else index + 1
                for (byte in value.substring(index, end).encodeToByteArray()) {
                    bytes.add(byte)
                }
                index = end
            }
        }
        return try {
            bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
        } catch (_: Exception) {
            null
        }
    }

    private fun hexValue(char: Char): Int? = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> null
    }
}
