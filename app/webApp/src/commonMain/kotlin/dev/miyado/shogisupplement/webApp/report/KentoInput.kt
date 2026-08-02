package dev.miyado.shogisupplement.webApp.report

import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.kifu.KifuGame
import dev.miyado.shogisupplement.kifu.KifuParseException
import dev.miyado.shogisupplement.text.AppStrings

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
    data class Error(val message: String) : ParseOutcome
}

fun parseKifInput(text: String): ParseOutcome {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ParseOutcome.Error(AppStrings.KENTO_ERROR_EMPTY_INPUT)
    val game: KifuGame = try {
        KifParser().parse(trimmed)
    } catch (e: KifuParseException) {
        return ParseOutcome.Error(e.message ?: AppStrings.KENTO_ERROR_KIF_PARSE)
    }
    if (game.moves.isEmpty()) return ParseOutcome.Error(AppStrings.KENTO_ERROR_NO_MOVES)
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
