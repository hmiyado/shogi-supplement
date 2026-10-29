package dev.miyado.shogisupplement.ui.report

sealed interface GameShareParse {
    data object Absent : GameShareParse
    data class Valid(val id: String) : GameShareParse
    data object Invalid : GameShareParse
}

object GameShareLink {
    const val WEB_STUDY_URL = "https://shogi-supplement.miyado.dev/kento.html"

    private const val VERSION = "v1"
    private const val KEY = "game"
    private const val MAX_FRAGMENT_LENGTH = 512
    private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{16,64}$")

    fun urlFor(id: String, baseUrl: String = WEB_STUDY_URL): String? {
        if (!ID_PATTERN.matches(id)) return null
        val base = baseUrl.substringBefore('#')
        val separator = if ('?' in base) "&" else "?"
        return "$base$separator$KEY=$VERSION&id=$id"
    }

    fun parseQuery(query: String?): GameShareParse = parseFragment(query?.removePrefix("?"))

    fun parseFragment(fragment: String?): GameShareParse {
        val raw = fragment?.removePrefix("#").orEmpty()
        if (raw.isEmpty()) return GameShareParse.Absent
        if (raw.length > MAX_FRAGMENT_LENGTH) return GameShareParse.Invalid

        var version: String? = null
        var id: String? = null
        var hasGame = false
        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val separator = part.indexOf('=')
            if (separator <= 0) {
                if (part == KEY) hasGame = true
                continue
            }
            val key = part.substring(0, separator)
            val value = part.substring(separator + 1)
            when (key) {
                KEY -> {
                    if (hasGame) return GameShareParse.Invalid
                    hasGame = true
                    version = value
                }
                "id" -> {
                    if (id != null) return GameShareParse.Invalid
                    id = value
                }
                "position", "sfen", "flip" -> Unit
            }
        }

        if (!hasGame) return GameShareParse.Absent
        if (version != VERSION) return GameShareParse.Invalid
        val parsedId = id ?: return GameShareParse.Invalid
        if (!ID_PATTERN.matches(parsedId)) return GameShareParse.Invalid
        return GameShareParse.Valid(parsedId)
    }
}
