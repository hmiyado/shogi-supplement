package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.notation.JapaneseNotation
import java.io.File

/**
 * 対応している戦型・囲いの一覧と各ページを生成する。
 *
 * 判定条件も手順の例も定義データから起こすため、判定を変えれば資料も変わる。
 */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: "docs/opening")
    outDir.mkdirs()
    outDir.listFiles { f -> f.extension == "md" }?.forEach { it.delete() }

    CASTLE_DEFS.forEach { File(outDir, "${it.slug}.md").writeText(placementPage(it, "囲い")) }
    PLACEMENT_STRATEGY_DEFS.forEach { File(outDir, "${it.slug}.md").writeText(placementPage(it, "戦型")) }
    EVENT_STRATEGY_DEFS.forEach { File(outDir, "${it.slug}.md").writeText(eventPage(it)) }
    File(outDir, "rook-style.md").writeText(rookStylePage())
    File(outDir, "index.md").writeText(indexPage())

    println("generated ${outDir.listFiles()?.size ?: 0} files -> ${outDir.path}")
}

private val KANJI_RANK = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")

private fun pieceName(type: PieceType): String = when (type) {
    PieceType.KING -> "玉"
    PieceType.ROOK -> "飛"
    PieceType.BISHOP -> "角"
    PieceType.GOLD -> "金"
    PieceType.SILVER -> "銀"
    PieceType.KNIGHT -> "桂"
    PieceType.LANCE -> "香"
    PieceType.PAWN -> "歩"
    PieceType.PROM_ROOK -> "龍"
    PieceType.PROM_BISHOP -> "馬"
    PieceType.PROM_SILVER -> "全"
    PieceType.PROM_KNIGHT -> "圭"
    PieceType.PROM_LANCE -> "杏"
    PieceType.PROM_PAWN -> "と"
}

private fun BfSquare.label(): String = "$file${KANJI_RANK[rank - 1]}"

private fun placementPage(def: PlacementDef, kindLabel: String): String = buildString {
    appendLine("# ${def.name}（$kindLabel）")
    appendLine()
    appendLine("[← 一覧へ戻る](./index.md)")
    appendLine()
    appendLine("## 判定条件")
    appendLine()
    appendLine("座標は自分視点（先手の8八＝後手の2二）。次をすべて満たした時点で成立し、")
    appendLine("以後その対局の記録として残る。")
    appendLine()
    def.required.forEach { appendLine("- ${it.square.label()}に自分の${pieceName(it.type)}がある") }
    def.empty.forEach { appendLine("- ${it.label()}が空いている") }
    def.forbidden.forEach { appendLine("- ${it.square.label()}に自分の${pieceName(it.type)}が**いない**") }
    appendLine("- ${def.plyCap}手以内に成立する")
    def.developsFrom?.let { appendLine("- ${it}の発展形（両方成立したときはこちらを表示する）") }
    def.conditions.forEach { appendLine("- ${it.describe()}") }
    appendLine()
    appendLine("出典: ${def.source}")
    appendLine()
    appendSamples(def.samples)
}

private fun eventPage(def: EventStrategyDef): String = buildString {
    appendLine("# ${def.name}（戦型）")
    appendLine()
    appendLine("[← 一覧へ戻る](./index.md)")
    appendLine()
    appendLine("## 判定条件")
    appendLine()
    appendLine("駒の配置ではなく、序盤に何が起きたかで決まる。次をすべて満たすと成立する。")
    appendLine()
    def.conditions.forEach { appendLine("- ${it.describe()}") }
    appendLine()
    appendLine(
        when (def.scope) {
            TagScope.BOTH_SIDES -> "対局単位で決まる戦型なので、成立したら両者に付く。"
            TagScope.MATCHING_SIDE -> "その側の指し方を指す戦型なので、条件を満たした側にだけ付く。"
        },
    )
    appendLine()
    appendLine("出典: ${def.source}")
    appendLine()
    appendSamples(def.samples)
}

private fun rookStylePage(): String = buildString {
    appendLine("# 飛車の筋による大分類")
    appendLine()
    appendLine("[← 一覧へ戻る](./index.md)")
    appendLine()
    appendLine("## 判定条件")
    appendLine()
    appendLine("飛車が同じ筋に${OpeningClassifier.SETTLE_PLY_THRESHOLD}手留まったら、その筋へ定着したとみなす。")
    appendLine("一時的に浮いただけの飛車を戦型として拾わないため。")
    appendLine("${OpeningClassifier.STYLE_PLY_CAP}手を過ぎてからの飛車回りは新たな定着として数えない（中盤の攻めのため）。")
    appendLine()
    appendLine("| 定着した筋（自分視点） | 表示 |")
    appendLine("| --- | --- |")
    appendLine("| 2筋（初期の筋のまま） | 居飛車 |")
    OpeningClassifier.FURIBISHA_LABELS_BY_FILE.forEach { (file, label) ->
        appendLine("| ${file}筋 | $label |")
    }
    appendLine("| それ以外 | 振り飛車（その他） |")
    appendLine()
    appendLine("名前の付いた筋への最後の定着を優先する。1・3・4・9筋への定着は、")
    appendLine("本来の戦型へ移る途中の一時停止であることが多いため。")
    appendLine()
    appendLine("双方が振り飛車なら相振り飛車、序盤に角交換した振り飛車なら角交換振り飛車が付く。")
}

private fun StringBuilder.appendSamples(samples: List<OpeningSample>) {
    if (samples.isEmpty()) return
    appendLine("## 手順の例")
    appendLine()
    appendLine("テストが実際に判定に通している手順。先手側で判定した結果を載せている。")
    samples.forEach { sample ->
        appendLine()
        appendLine("### ${sample.label}")
        appendLine()
        appendLine("```")
        appendLine(japaneseMoves(sample.usiMoves))
        appendLine("```")
        appendLine()
        appendLine("最終局面:")
        appendLine()
        appendLine("```")
        append(boardDiagram(finalBoard(sample.usiMoves)))
        appendLine("```")
    }
}

private fun japaneseMoves(usiMoves: List<String>): String {
    val board = ShogiBoard()
    var prevTo: ShogiSquare? = null
    val texts = usiMoves.mapIndexed { index, usi ->
        val move = ShogiMove.fromUsi(usi)
        val text = JapaneseNotation.format(move, board, prevTo)
        board.push(move)
        prevTo = move.to
        "${index + 1}. $text"
    }
    return texts.chunked(6).joinToString("\n") { it.joinToString("  ") }
}

private fun finalBoard(usiMoves: List<String>): ShogiBoard {
    val board = ShogiBoard()
    usiMoves.forEach { board.push(ShogiMove.fromUsi(it)) }
    return board
}

private fun boardDiagram(board: ShogiBoard): String = buildString {
    appendLine("  ９ ８ ７ ６ ５ ４ ３ ２ １")
    appendLine("+---------------------------+")
    for (rank in 1..9) {
        append("|")
        for (file in 9 downTo 1) {
            val piece = board.pieceAt(ShogiSquare(file, rank))
            if (piece == null) {
                append(" ・")
            } else {
                append(if (piece.side == Side.BLACK) " " else "v")
                append(pieceName(piece.type))
            }
        }
        appendLine("|${KANJI_RANK[rank - 1]}")
    }
    appendLine("+---------------------------+")
}

private fun indexPage(): String = buildString {
    appendLine("# 対応している戦型・囲い")
    appendLine()
    appendLine("アプリが棋譜から判定できる戦型と囲いの一覧。各ページに判定条件と、")
    appendLine("テストが実際に通している手順の例を載せている。")
    appendLine()
    appendLine("このファイルと各ページは `./gradlew generateOpeningDocs` が定義データから生成する。")
    appendLine("直接編集しても次の生成で消える。")
    appendLine()
    appendLine("## 戦型")
    appendLine()
    appendLine("### 飛車の筋で決まるもの")
    appendLine()
    appendLine("- [飛車の筋による大分類](./rook-style.md) — 居飛車・中飛車・四間飛車・三間飛車・向かい飛車")
    appendLine()
    appendLine("### 序盤の出来事で決まるもの")
    appendLine()
    EVENT_STRATEGY_DEFS.forEach {
        appendLine("- [${it.name}](./${it.slug}.md) — ${it.conditions.first().describe()}")
    }
    appendLine()
    appendLine("### 駒の配置で決まるもの")
    appendLine()
    PLACEMENT_STRATEGY_DEFS.forEach { appendLine("- [${it.name}](./${it.slug}.md) — ${summary(it)}") }
    appendLine()
    appendLine("## 囲い")
    appendLine()
    CASTLE_DEFS.forEach { appendLine("- [${it.name}](./${it.slug}.md) — ${summary(it)}") }
}

private fun summary(def: PlacementDef): String =
    def.required.joinToString("・") { "${it.square.label()}${pieceName(it.type)}" }
