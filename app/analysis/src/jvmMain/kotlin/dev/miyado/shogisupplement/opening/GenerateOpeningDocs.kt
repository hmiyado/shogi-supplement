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
    def.conditions.forEach { appendLine("- ${it.describe()}") }
    appendLine()
    def.developsFrom?.let {
        appendLine("成立の判定は${it}と独立している（${it}を経由していなくても、")
        appendLine("この形になれば成立する）。両方成立したときの表示だけこちらを優先する。")
        appendLine()
    }
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
    appendLine("# 飛車を振った筋による戦型")
    appendLine()
    appendLine("[← 一覧へ戻る](./index.md)")
    appendLine()
    appendLine("## 判定条件")
    appendLine()
    appendLine("飛車をその筋へ振った時点で成立する。${OpeningClassifier.ROOK_STYLE_PLY_CAP}手を過ぎてからの")
    appendLine("飛車回りは数えない（序盤に決めた戦型を、中盤の攻めで塗り替えないため）。")
    appendLine()
    appendLine("| 振った筋（自分視点） | 戦型 |")
    appendLine("| --- | --- |")
    OpeningClassifier.ROOK_FILE_LABELS.toSortedMap().forEach { (file, label) ->
        appendLine("| ${file}筋 | $label |")
    }
    appendLine()
    appendLine("数えるのは飛車の初期段（自分視点の${KANJI_RANK[OpeningClassifier.ROOK_HOME_RANK - 1]}段）へ")
    appendLine("動かす手だけ。浮き飛車が敵陣寄りの段を横へ動く手（横歩取りの2四飛→3四飛など）は")
    appendLine("振ったとみなさない。")
    appendLine()
    appendLine("最初に振った筋で確定し、その後どこへ回しても変えない。")
    appendLine("初期の筋（2筋）のままの対局は判定しない。")
    appendLine()
    appendLine("双方が振り飛車なら[相振り飛車](./ai-furibisha.md)、序盤に角交換していれば")
    appendLine("[角交換振り飛車](./kakukoukan-furibisha.md)が付く（袖飛車は振り飛車に含めない）。")
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
    appendLine("### 飛車を振った筋で決まるもの")
    appendLine()
    appendLine(
        "- [飛車を振った筋による戦型](./rook-style.md) — " +
            OpeningClassifier.ROOK_FILE_LABELS.values.joinToString("・"),
    )
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
    appendLine("## 画面に出す代表の戦型")
    appendLine()
    appendLine("1局に複数の戦型が成立する（角換わりで棒銀に出るなど）。対局情報では成立した")
    appendLine("ものをすべて並べ、棋譜一覧の絞り込みもそのどれでも引ける。カードなど1つしか")
    appendLine("置けない場所では次の順で先にあるものを選ぶ。")
    appendLine()
    OpeningClassifier.PRIMARY_STYLE_PRIORITY.forEachIndexed { i, name ->
        appendLine("${i + 1}. $name")
    }
    appendLine()
    appendLine("この並びに無い戦型（攻めの形など）は代表にはせず、並記と絞り込みにだけ出る。")
    appendLine()
    appendLine("## 囲い")
    appendLine()
    CASTLE_DEFS.forEach { appendLine("- [${it.name}](./${it.slug}.md) — ${summary(it)}") }
}

private fun summary(def: PlacementDef): String =
    def.required.joinToString("・") { "${it.square.label()}${pieceName(it.type)}" }
