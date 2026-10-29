package dev.miyado.shogisupplement.webApp.report

import dev.miyado.shogisupplement.ui.text.LocalizedStrings

import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.kifu.KifuGame
import dev.miyado.shogisupplement.kifu.KifuParseException

/** KIF貼り付けをここへ正規化してから解析へ渡す。 */
data class ParsedInput(
    val baseSfenArg: String,
    val moves: List<String>,
    val headers: Map<String, String> = emptyMap(),
    val endReason: String? = null,
    val winner: String? = null,
    val kifText: String? = null,
)

sealed interface ParseOutcome {
    data class Ok(val input: ParsedInput) : ParseOutcome
    data class Error(val reason: ParseError, val detail: String? = null) : ParseOutcome
}

enum class ParseError { EMPTY, INVALID, NO_MOVES }

suspend fun ParseOutcome.Error.message(): String = detail ?: when (reason) {
    ParseError.EMPTY -> LocalizedStrings.KENTO_ERROR_EMPTY_INPUT()
    ParseError.INVALID -> LocalizedStrings.KENTO_ERROR_KIF_PARSE()
    ParseError.NO_MOVES -> LocalizedStrings.KENTO_ERROR_NO_MOVES()
}

fun parseKifInput(text: String): ParseOutcome {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParseOutcome.Error(ParseError.EMPTY)
    val game: KifuGame = try {
        KifParser().parse(trimmed)
    } catch (e: KifuParseException) {
        return ParseOutcome.Error(ParseError.INVALID, e.message)
    }
    if (game.moves.isEmpty()) return ParseOutcome.Error(ParseError.NO_MOVES)
    return ParseOutcome.Ok(
        ParsedInput(
            baseSfenArg = "startpos",
            moves = game.moves,
            headers = game.headers,
            endReason = game.endReason,
            winner = game.winner,
            kifText = trimmed,
        ),
    )
}
