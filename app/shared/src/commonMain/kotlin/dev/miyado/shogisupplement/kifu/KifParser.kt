package dev.miyado.shogisupplement.kifu

/**
 * lishogi / ShogiGUI 系 KIF パーサ。
 *
 * - 手数行（`手数----指手---------消費時間--` 以降）をパースし USI 手列へ変換する
 * - KIF は移動元が `(77)` 形式で明示されるため盤面追跡なしで変換できる
 * - 「同　歩」は直前の移動先で解決、「打」は `P*` 形式、末尾「成」は USI `+`
 * - 消費時間 `(mm:ss/h:mm:ss)` はあれば保持、無ければ null
 * - 手合割が平手以外なら [KifuParseException]
 * - 投了・中断・千日手等の終局語で打ち切り（そこまでの手列を返す）
 */
class KifParser : KifuParser {

    override fun parse(text: String): KifuGame {
        val headers = mutableMapOf<String, String>()
        val moves = mutableListOf<String>()
        val times = mutableListOf<Int?>()
        var prevDest: Square? = null
        var finished = false
        var endReason: String? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            val trimmed = line.trim()
            // コメント・棋譜コメント・しおり
            if (trimmed.startsWith("*") || trimmed.startsWith("#") || trimmed.startsWith("&")) continue
            // 変化手順は対象外（本譜のみ）
            if (trimmed.startsWith("変化")) break
            // ヘッダ行（全角コロン区切り）
            val colon = trimmed.indexOf('：')
            if (colon > 0 && !trimmed.first().isAsciiDigit()) {
                val key = trimmed.substring(0, colon)
                val value = trimmed.substring(colon + 1)
                headers[key] = value
                if (key == "手合割" && value.trim() != "平手") {
                    throw KifuParseException("平手以外の手合割には未対応です (手合割=${value.trim()})")
                }
                continue
            }
            // 手数ヘッダ行
            if (trimmed.startsWith("手数--")) continue
            if (finished) continue

            // 指し手行: "  12 ７六歩(77)  ( 0:02/00:00:12)"
            val moveLine = parseMoveLine(trimmed) ?: continue
            if (moveLine.terminal) {
                finished = true
                // 終局語を記録（terminalWords のうち先頭一致したものを取る）
                endReason = terminalWords.firstOrNull { moveLine.moveText.startsWith(it) }
                continue
            }
            val (usi, dest) = convertMove(moveLine.moveText, prevDest, trimmed)
            moves.add(usi)
            times.add(moveLine.timeSeconds)
            prevDest = dest
        }

        // 勝者判定: 投了・切れ負け・時間切れ・詰みは手数パリティで確定。引き分け系はnull。
        // 次に指す番の側（= moves.size が偶数なら sente が次）が負けた場合に勝者を算出。
        val winner: String? = if (endReason != null &&
            endReason in setOf("投了", "切れ負け", "時間切れ", "反則負け", "詰み", "反則")
        ) {
            // moves.size 手目まで指された後、次は (moves.size % 2 == 0) なら sente (1手目が sente)
            if (moves.size % 2 == 0) "gote" else "sente"
        } else {
            null
        }

        return KifuGame(moves, times, headers, endReason, winner)
    }

    // ---- 内部表現 ----

    internal data class Square(val file: Int, val rank: Int) {
        fun toUsi(): String = "$file${'a' + (rank - 1)}"
    }

    private data class MoveLine(val moveText: String, val timeSeconds: Int?, val terminal: Boolean)

    private val terminalWords = setOf(
        "投了", "中断", "千日手", "持将棋", "入玉勝ち", "入玉宣言", "宣言勝ち",
        "切れ負け", "反則勝ち", "反則負け", "詰み", "不戦勝", "不戦敗", "封じ手", "パス",
    )

    /** 手数行を「手数 / 指し手テキスト / 消費時間」に分解する。手数で始まらない行は null。 */
    private fun parseMoveLine(line: String): MoveLine? {
        var i = 0
        // 手数（半角数字）
        val numStart = i
        while (i < line.length && line[i].isAsciiDigit()) i++
        if (i == numStart) return null
        // 手数の後は空白
        if (i >= line.length || !line[i].isWhitespace()) return null
        while (i < line.length && line[i].isWhitespace()) i++
        val rest = line.substring(i)

        // 消費時間 "( 0:02/00:00:12)" を末尾から探す（移動元 "(77)" と区別: コロンを含む）
        var moveText = rest
        var timeSeconds: Int? = null
        val timeParen = rest.lastIndexOf('(')
        if (timeParen >= 0) {
            val closing = rest.indexOf(')', timeParen)
            if (closing > timeParen) {
                val inside = rest.substring(timeParen + 1, closing)
                if (inside.contains(':')) {
                    timeSeconds = parseMoveTime(inside)
                    moveText = rest.substring(0, timeParen).trim()
                }
            }
        }
        moveText = moveText.trim()
        val head = moveText.takeWhile { !it.isWhitespace() && it != '(' }
        if (terminalWords.any { moveText.startsWith(it) }) {
            return MoveLine(moveText, timeSeconds, terminal = true)
        }
        if (head.isEmpty()) return null
        return MoveLine(moveText, timeSeconds, terminal = false)
    }

    /** "mm:ss/h:mm:ss" または "mm:ss" の前半（この手の消費時間）を秒に変換。 */
    private fun parseMoveTime(inside: String): Int? {
        val first = inside.substringBefore('/').trim()
        val parts = first.split(':').map { it.trim() }
        if (parts.any { it.isEmpty() || it.any { c -> !c.isAsciiDigit() } }) return null
        return when (parts.size) {
            2 -> parts[0].toInt() * 60 + parts[1].toInt()               // mm:ss
            3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt() // h:mm:ss
            else -> null
        }
    }

    /** KIF指し手テキスト → USI。移動先を返して「同」解決に使う。 */
    private fun convertMove(moveText: String, prevDest: Square?, line: String): Pair<String, Square> {
        var i = 0
        val dest: Square
        if (moveText.startsWith("同")) {
            dest = prevDest ?: throw KifuParseException("「同」の解決先がありません", line)
            i = 1
            // 「同　歩」の全角空白・半角空白をスキップ
            while (i < moveText.length && (moveText[i] == '　' || moveText[i] == ' ')) i++
        } else {
            if (moveText.length < i + 2) throw KifuParseException("指し手の座標を読めません", line)
            val file = parseFileChar(moveText[i]) ?: throw KifuParseException("筋を読めません", line)
            val rank = parseRankChar(moveText[i + 1]) ?: throw KifuParseException("段を読めません", line)
            dest = Square(file, rank)
            i += 2
        }

        // 駒名（成香・成桂・成銀は2文字）
        val piece: String
        if (moveText.startsWith("成", i) && i + 1 < moveText.length && moveText[i + 1] in "香桂銀") {
            piece = moveText.substring(i, i + 2)
            i += 2
        } else {
            if (i >= moveText.length) throw KifuParseException("駒名を読めません", line)
            piece = moveText[i].toString()
            if (piece[0] !in "歩香桂銀金角飛玉王と馬龍竜") {
                throw KifuParseException("未知の駒名です", line)
            }
            i++
        }

        var promote = false
        if (moveText.startsWith("不成", i)) {
            i += 2
        } else if (moveText.startsWith("成", i)) {
            promote = true
            i++
        }

        if (moveText.startsWith("打", i)) {
            val dropLetter = dropPieceLetter[piece]
                ?: throw KifuParseException("打てない駒です ($piece)", line)
            return "$dropLetter*${dest.toUsi()}" to dest
        }

        // 移動元 "(77)"
        val open = moveText.indexOf('(', i)
        val close = if (open >= 0) moveText.indexOf(')', open) else -1
        if (open < 0 || close < 0 || close - open != 3) {
            throw KifuParseException("移動元 (77) 形式を読めません", line)
        }
        val srcFile = moveText[open + 1].digitToIntOrNull()
        val srcRank = moveText[open + 2].digitToIntOrNull()
        if (srcFile == null || srcRank == null || srcFile !in 1..9 || srcRank !in 1..9) {
            throw KifuParseException("移動元の座標が不正です", line)
        }
        val src = Square(srcFile, srcRank)
        return "${src.toUsi()}${dest.toUsi()}${if (promote) "+" else ""}" to dest
    }

    companion object {
        private val dropPieceLetter = mapOf(
            "歩" to "P", "香" to "L", "桂" to "N", "銀" to "S", "金" to "G", "角" to "B", "飛" to "R",
        )

        /** 筋: 全角数字・半角数字・漢数字を許容。 */
        internal fun parseFileChar(c: Char): Int? = when (c) {
            in '1'..'9' -> c - '0'
            in '１'..'９' -> c - '１' + 1
            else -> kanjiDigit(c)
        }

        /** 段: 漢数字が標準。全角・半角数字も許容。 */
        internal fun parseRankChar(c: Char): Int? = when (c) {
            in '1'..'9' -> c - '0'
            in '１'..'９' -> c - '１' + 1
            else -> kanjiDigit(c)
        }

        private fun kanjiDigit(c: Char): Int? = when (c) {
            '一' -> 1; '二' -> 2; '三' -> 3; '四' -> 4; '五' -> 5
            '六' -> 6; '七' -> 7; '八' -> 8; '九' -> 9
            else -> null
        }
    }
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
