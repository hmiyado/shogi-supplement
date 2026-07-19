package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.ui.theme.LightInk
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors

// ─── データモデル ─────────────────────────────────────────────────────────────

/**
 * 盤面上の1コマ。
 * @param pieceChar 大文字の駒種記号 (P/L/N/S/G/B/R/K)
 * @param promoted  成り駒かどうか
 * @param isBlack   先手かどうか
 */
data class SfenPiece(
    val pieceChar: Char,
    val promoted: Boolean,
    val isBlack: Boolean,
)

/**
 * SFEN 文字列をパースした局面状態。
 *
 * @param boardPieces (file 1-9, rank 1-9) → SfenPiece のマップ
 * @param isBlackTurn 先手番かどうか
 * @param blackHand   先手の持ち駒 (大文字記号 → 枚数)
 * @param whiteHand   後手の持ち駒 (大文字記号 → 枚数)
 * @param moveNumber  手数
 */
data class SfenPosition(
    val boardPieces: Map<Pair<Int, Int>, SfenPiece>,
    val isBlackTurn: Boolean,
    val blackHand: Map<Char, Int>,
    val whiteHand: Map<Char, Int>,
    val moveNumber: Int,
) {
    companion object {

        private val PIECE_CHARS = setOf('P', 'L', 'N', 'S', 'G', 'B', 'R', 'K')

        /**
         * SFEN 文字列をパースする。
         * 例: "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1"
         */
        fun parse(sfen: String): SfenPosition {
            val parts = sfen.trim().split(" ")
            if (parts.size < 4) {
                return empty()
            }

            val boardStr = parts[0]
            val turnChar = parts[1]
            val handStr = parts[2]
            val moveNum = parts[3].toIntOrNull() ?: 1

            // ── 盤面パース ──────────────────────────────────────────────────
            val pieces = mutableMapOf<Pair<Int, Int>, SfenPiece>()
            val ranks = boardStr.split("/")
            for ((rankIdx, rankStr) in ranks.withIndex()) {
                val rank = rankIdx + 1 // rank 1..9
                var file = 9           // file 9..1 (left to right in SFEN)
                var i = 0
                while (i < rankStr.length && file >= 1) {
                    val c = rankStr[i]
                    when {
                        c == '+' -> {
                            // 成り駒: 次の文字が駒種記号
                            i++
                            if (i < rankStr.length) {
                                val nc = rankStr[i]
                                val isBlack = nc.isUpperCase()
                                val pc = nc.uppercaseChar()
                                if (pc in PIECE_CHARS) {
                                    pieces[file to rank] = SfenPiece(pc, promoted = true, isBlack = isBlack)
                                }
                                file--
                            }
                        }
                        c.isDigit() -> {
                            // 空きマス
                            file -= c.digitToInt()
                        }
                        c.uppercaseChar() in PIECE_CHARS -> {
                            val isBlack = c.isUpperCase()
                            pieces[file to rank] = SfenPiece(c.uppercaseChar(), promoted = false, isBlack = isBlack)
                            file--
                        }
                        else -> { /* 不明文字は無視 */ }
                    }
                    i++
                }
            }

            // ── 手番 ───────────────────────────────────────────────────────
            val isBlackTurn = turnChar == "b"

            // ── 持ち駒パース ───────────────────────────────────────────────
            val blackHand = mutableMapOf<Char, Int>()
            val whiteHand = mutableMapOf<Char, Int>()
            if (handStr != "-") {
                var pendingCount = 0
                for (c in handStr) {
                    when {
                        c.isDigit() -> pendingCount = pendingCount * 10 + c.digitToInt()
                        c.uppercaseChar() in PIECE_CHARS -> {
                            val cnt = if (pendingCount > 0) pendingCount else 1
                            pendingCount = 0
                            val pc = c.uppercaseChar()
                            if (c.isUpperCase()) {
                                blackHand[pc] = (blackHand[pc] ?: 0) + cnt
                            } else {
                                whiteHand[pc] = (whiteHand[pc] ?: 0) + cnt
                            }
                        }
                    }
                }
            }

            return SfenPosition(pieces, isBlackTurn, blackHand, whiteHand, moveNum)
        }

        fun empty(): SfenPosition = SfenPosition(emptyMap(), true, emptyMap(), emptyMap(), 1)
    }
}

// ─── 駒表示ヘルパー ──────────────────────────────────────────────────────────

/** 駒種記号と成りフラグから表示用漢字を返す。 */
private fun pieceKanji(pieceChar: Char, promoted: Boolean): String = when {
    pieceChar == 'K' -> "王"
    promoted -> when (pieceChar) {
        'R' -> "龍"
        'B' -> "馬"
        'G' -> "金" // 金は成りなし（念のため）
        'S' -> "全"
        'N' -> "圭"
        'L' -> "杏"
        'P' -> "と"
        else -> "?"
    }
    else -> when (pieceChar) {
        'R' -> "飛"
        'B' -> "角"
        'G' -> "金"
        'S' -> "銀"
        'N' -> "桂"
        'L' -> "香"
        'P' -> "歩"
        else -> "?"
    }
}

/** SFEN 駒文字（大文字）→ PieceType（持ち駒タップ用）。 */
private fun charToPieceType(c: Char): PieceType? = when (c) {
    'P' -> PieceType.PAWN
    'L' -> PieceType.LANCE
    'N' -> PieceType.KNIGHT
    'S' -> PieceType.SILVER
    'G' -> PieceType.GOLD
    'B' -> PieceType.BISHOP
    'R' -> PieceType.ROOK
    else -> null
}

// ─── 共通ヘルパ ───────────────────────────────────────────────────────────────

// 座標ラベル（1-9・一〜九）と盤の間の余白を2dpに詰める。ラベル帯は cellSize に依存しない
// 固定トラック幅（CoordinateLabelTrack）を確保し、盤との間に CoordinateLabelGap だけ空ける。
val CoordinateLabelTrack = 18.dp
val CoordinateLabelGap = 2.dp

/**
 * 盤サイズ（cellSize）の共通計算ロジック。report・drill 両方の座標ラベル圧縮仕様を統一する。
 *
 * 総高さ: 筋ラベル帯(CoordinateLabelTrack) + ギャップ(CoordinateLabelGap) +
 *         盤9マス + 上下持駒行(各1 cell 相当) の合計。
 */
fun computeBoardCellSize(maxWidth: Dp, maxHeight: Dp): Dp {
    val labelBudget = CoordinateLabelTrack + CoordinateLabelGap
    val fromWidth = ((maxWidth - labelBudget) / 9).coerceAtMost(44.dp)
    val fromHeight: Dp = if (maxHeight < 2000.dp) {
        (maxHeight - 8.dp - labelBudget) / 11
    } else {
        fromWidth
    }
    return minOf(fromWidth, fromHeight)
}

// ─── Composable ──────────────────────────────────────────────────────────────

/**
 * SFEN 文字列を受け取って将棋盤を描画する Composable。
 *
 * MainActivity.kt の ReportBoardView・DrillScreen.kt の InteractiveBoardView の両方が使う
 * 「選択マス・合法手ドット・持駒タップ」UXをこの1実装で提供する。
 *
 * - 9x9 盤・文字駒（漢字）・成駒赤字
 * - 後手駒は 180° 回転表示
 * - 両者の持ち駒表示（盤の上下）
 * - 画面幅に追従（BoxWithConstraints 使用）
 * - flip=true で盤を 180° 反転（後手が下になる）
 * - lastMoveDest が指定された場合、その到達マスを卵黄ハイライトする
 * - selectedFrom / selectedDropType / legalDestinations: 検討・ドリルの選択状態UX
 * - onSquareTapped: マス単位のタップ通知（呼び出し側で「駒タップ」「空マスタップ→ナビ」等を判定する）
 * - onHandPieceTapped: 持ち駒タップ通知（手番側の駒のみ有効。呼び出し側で判定込み）
 *
 * @param sfen 局面の SFEN 文字列
 */
@Composable
fun ShogiBoardView(
    sfen: String,
    modifier: Modifier = Modifier,
    flip: Boolean = false,
    lastMoveDest: Pair<Int, Int>? = null,
    selectedFrom: ShogiSquare? = null,
    selectedDropType: PieceType? = null,
    legalDestinations: Set<ShogiSquare> = emptySet(),
    onSquareTapped: ((ShogiSquare) -> Unit)? = null,
    onHandPieceTapped: ((PieceType) -> Unit)? = null,
) {
    val position = remember(sfen) { SfenPosition.parse(sfen) }
    ShogiBoardContent(
        position = position,
        modifier = modifier,
        flip = flip,
        lastMoveDest = lastMoveDest,
        selectedFrom = selectedFrom,
        selectedDropType = selectedDropType,
        legalDestinations = legalDestinations,
        onSquareTapped = onSquareTapped,
        onHandPieceTapped = onHandPieceTapped,
    )
}

@Composable
private fun ShogiBoardContent(
    position: SfenPosition,
    modifier: Modifier = Modifier,
    flip: Boolean = false,
    lastMoveDest: Pair<Int, Int>? = null,
    selectedFrom: ShogiSquare? = null,
    selectedDropType: PieceType? = null,
    legalDestinations: Set<ShogiSquare> = emptySet(),
    onSquareTapped: ((ShogiSquare) -> Unit)? = null,
    onHandPieceTapped: ((PieceType) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        TopBottomBoardLayout(
                position = position,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                flip = flip,
                lastMoveDest = lastMoveDest,
                selectedFrom = selectedFrom,
                selectedDropType = selectedDropType,
                legalDestinations = legalDestinations,
                onSquareTapped = onSquareTapped,
                onHandPieceTapped = onHandPieceTapped,
        )
    }
}

@Composable
private fun TopBottomBoardLayout(
    position: SfenPosition,
    maxWidth: Dp,
    maxHeight: Dp,
    flip: Boolean,
    lastMoveDest: Pair<Int, Int>?,
    selectedFrom: ShogiSquare?,
    selectedDropType: PieceType?,
    legalDestinations: Set<ShogiSquare>,
    onSquareTapped: ((ShogiSquare) -> Unit)?,
    onHandPieceTapped: ((PieceType) -> Unit)?,
) {
    val cellSize: Dp = computeBoardCellSize(maxWidth, maxHeight)
    val boardWidth = cellSize * 9 + CoordinateLabelTrack + CoordinateLabelGap

    // 盤を水平中央に寄せる
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(boardWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 上部: flip=false → 後手持駒、flip=true → 先手持駒
            HandRow(
                hand = if (flip) position.blackHand else position.whiteHand,
                isBlack = flip,
                isCurrentTurn = if (flip) position.isBlackTurn else !position.isBlackTurn,
                selectedDropType = if ((if (flip) position.isBlackTurn else !position.isBlackTurn)) selectedDropType else null,
                cellSize = cellSize,
                onHandPieceTapped = onHandPieceTapped,
            )

            // ── 盤面 ─────────────────────────────────────────────────────
            BoardGrid(
                pieces = position.boardPieces,
                cellSize = cellSize,
                flip = flip,
                lastMoveDest = lastMoveDest,
                selectedFrom = selectedFrom,
                legalDestinations = legalDestinations,
                onSquareTapped = onSquareTapped,
            )

            // 下部: flip=false → 先手持駒、flip=true → 後手持駒
            HandRow(
                hand = if (flip) position.whiteHand else position.blackHand,
                isBlack = !flip,
                isCurrentTurn = if (flip) !position.isBlackTurn else position.isBlackTurn,
                selectedDropType = if ((if (flip) !position.isBlackTurn else position.isBlackTurn)) selectedDropType else null,
                cellSize = cellSize,
                onHandPieceTapped = onHandPieceTapped,
            )
        }
    }
}

@Composable
private fun SidesHandColumn(
    hand: Map<Char, Int>,
    isBlack: Boolean,
    isCurrentTurn: Boolean,
    selectedDropType: PieceType?,
    cellSize: Dp,
    columnWidth: Dp,
    stackFromBottom: Boolean,
    onHandPieceTapped: ((PieceType) -> Unit)?,
) {
    val handOrder = listOf('R', 'B', 'G', 'S', 'N', 'L', 'P')
    val handItems = handOrder.mapNotNull { pc -> (hand[pc] ?: 0).takeIf { it > 0 }?.let { pc to it } }
    val shogiColors = MaterialTheme.shogiColors

    Column(
        modifier = Modifier
            .width(columnWidth)
            .wrapContentHeight(),
        verticalArrangement = if (stackFromBottom) {
            androidx.compose.foundation.layout.Arrangement.Bottom
        } else {
            androidx.compose.foundation.layout.Arrangement.Top
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 枚数バッジ・縦書き対局者ラベル用のオプションslot（今のところ未使用。将来拡張用）。
        for ((pc, cnt) in handItems) {
            val pt = charToPieceType(pc)
            val kanji = pieceKanji(pc, promoted = false)
            val label = if (cnt > 1) "$kanji×$cnt" else kanji
            val isSelectedPiece = pt != null && selectedDropType == pt && isCurrentTurn
            Box(
                modifier = Modifier
                    .padding(vertical = 1.dp)
                    .widthIn(min = cellSize * 0.9f)
                    .height(cellSize * 0.7f)
                    .border(
                        if (isSelectedPiece) 1.dp else 0.5.dp,
                        if (isSelectedPiece) shogiColors.highlight else shogiColors.line,
                    )
                    .background(if (isSelectedPiece) shogiColors.highlightSoft else Color.Transparent)
                    .then(
                        if (pt != null && isCurrentTurn && onHandPieceTapped != null) {
                            Modifier.clickable { onHandPieceTapped(pt) }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = (cellSize.value * 0.4).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (handItems.isEmpty()) {
            Box(modifier = Modifier.height(cellSize * 0.7f), contentAlignment = Alignment.Center) {
                Text("−", style = MaterialTheme.typography.labelSmall, color = shogiColors.ink3)
            }
        }
    }
}

@Composable
private fun BoardGrid(
    pieces: Map<Pair<Int, Int>, SfenPiece>,
    cellSize: Dp,
    flip: Boolean = false,
    lastMoveDest: Pair<Int, Int>? = null,
    selectedFrom: ShogiSquare? = null,
    legalDestinations: Set<ShogiSquare> = emptySet(),
    onSquareTapped: ((ShogiSquare) -> Unit)? = null,
) {
    val ranks = if (flip) (9 downTo 1).toList() else (1..9).toList()
    val files = if (flip) (1..9).toList() else (9 downTo 1).toList()

    // 筋ラベル（上辺）: 先手=9→1（左→右）、後手=1→9（左→右）
    val fileLabels = files.map { it.toString() }

    // 段ラベル（右辺）: 先手=一〜九（上→下）、後手=九〜一（上→下）
    val rankKanji = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
    val rankLabels = if (flip) rankKanji.reversed() else rankKanji

    val shogiColors = MaterialTheme.shogiColors
    // ラベル帯は cellSize に依存しない固定トラック幅なので、フォントサイズも
    // そのトラックに収まる固定比率で決める（report/drill 版と同一）。
    val labelFontSize = (CoordinateLabelTrack.value * 0.62f).sp
    val labelColor = shogiColors.ink2

    // 選択マス・直前手=卵黄（同トークン）、移動先候補=紺青（DESIGN.md トークン）
    val highlightColor = shogiColors.highlight
    val legalDestOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val legalDestDot = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

    Column {
        // ── 筋ラベル行（上辺）。ラベル帯を圧縮し、盤との間に2dpだけ空ける ──
        Row(modifier = Modifier.height(CoordinateLabelTrack)) {
            for (label in fileLabels) {
                Box(
                    modifier = Modifier
                        .width(cellSize)
                        .height(CoordinateLabelTrack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = labelFontSize, color = labelColor, textAlign = TextAlign.Center)
                }
            }
            // 右端コーナー（段ラベル列との幅合わせ）
            Box(
                Modifier.size(
                    width = CoordinateLabelTrack + CoordinateLabelGap,
                    height = CoordinateLabelTrack,
                ),
            )
        }
        Spacer(Modifier.height(CoordinateLabelGap))

        // ── 盤面 + 段ラベル ───────────────────────────────────────────────
        Row {
            Column(
                modifier = Modifier
                    .background(shogiColors.board)
                    .border(1.dp, shogiColors.boardLine),
            ) {
                for (rank in ranks) {
                    Row {
                        for (file in files) {
                            val sq = ShogiSquare(file, rank)
                            val piece = pieces[file to rank]
                            val isSelected = selectedFrom?.let { it.file == file && it.rank == rank } == true
                            val isLegalDest = sq in legalDestinations
                            val isLastMove = lastMoveDest?.let { it.first == file && it.second == rank } == true

                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .border(0.5.dp, shogiColors.boardLine)
                                    .background(
                                        when {
                                            isSelected -> highlightColor
                                            isLegalDest -> legalDestOverlay
                                            isLastMove -> highlightColor
                                            else -> Color.Transparent
                                        },
                                    )
                                    .then(
                                        if (onSquareTapped != null) {
                                            Modifier.clickable { onSquareTapped(sq) }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    // interactionテスト用のタグ（board_sq_<file>_<rank>）。
                                    .testTag("board_sq_${file}_$rank"),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (piece != null) {
                                    val kanji = pieceKanji(piece.pieceChar, piece.promoted)
                                    // 盤は Light/Dark とも榧色のため、駒文字は常に濃墨
                                    val color = if (piece.promoted) shogiColors.loss else LightInk
                                    val shouldRotate = if (flip) piece.isBlack else !piece.isBlack
                                    Text(
                                        text = kanji,
                                        fontSize = (cellSize.value * 0.55).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                        textAlign = TextAlign.Center,
                                        modifier = if (shouldRotate) Modifier.rotate(180f) else Modifier,
                                    )
                                }
                                // 合法目的マスのドット（紺青・駒が無い場合のみ）
                                if (isLegalDest && piece == null) {
                                    Box(
                                        modifier = Modifier
                                            .size(cellSize * 0.32f)
                                            .background(legalDestDot, CircleShape),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 段ラベル列（右辺）。固定トラック幅 + 2dpギャップに圧縮。
            Spacer(Modifier.width(CoordinateLabelGap))
            Column {
                for (label in rankLabels) {
                    Box(
                        modifier = Modifier
                            .width(CoordinateLabelTrack)
                            .height(cellSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = labelFontSize, color = labelColor, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandRow(
    hand: Map<Char, Int>,
    isBlack: Boolean,
    cellSize: Dp,
    isCurrentTurn: Boolean = false,
    selectedDropType: PieceType? = null,
    onHandPieceTapped: ((PieceType) -> Unit)? = null,
) {
    // 持ち駒の表示順: 飛,角,金,銀,桂,香,歩
    val handOrder = listOf('R', 'B', 'G', 'S', 'N', 'L', 'P')
    val handItems = handOrder.mapNotNull { pc ->
        val cnt = hand[pc] ?: 0
        if (cnt > 0) pc to cnt else null
    }
    val shogiColors = MaterialTheme.shogiColors

    // FlowRow で折返し可能にし「×N」が見切れない構造にする。
    // height固定をやめ wrapContentHeight で複数行に対応する。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.height(cellSize),
            contentAlignment = Alignment.CenterStart,
        ) {
            HandRowLabel(isBlack = isBlack)
        }
        Spacer(Modifier.width(6.dp))
        if (handItems.isEmpty()) {
            Box(
                modifier = Modifier.height(cellSize),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "なし",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        } else {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            ) {
                for ((pc, cnt) in handItems) {
                    val pt = charToPieceType(pc)
                    val kanji = pieceKanji(pc, promoted = false)
                    val label = if (cnt > 1) "$kanji×$cnt" else kanji
                    val isSelectedPiece = pt != null && selectedDropType == pt && isCurrentTurn
                    // FlowRow 内で折返し。widthIn(min) で最小幅確保。
                    Box(
                        modifier = Modifier
                            .height(cellSize)
                            .widthIn(min = cellSize * 0.75f)
                            .border(
                                if (isSelectedPiece) 1.dp else 0.5.dp,
                                if (isSelectedPiece) shogiColors.highlight else shogiColors.line,
                            )
                            .background(if (isSelectedPiece) shogiColors.highlightSoft else Color.Transparent)
                            .padding(horizontal = 2.dp, vertical = 1.dp)
                            .then(
                                if (pt != null && isCurrentTurn && onHandPieceTapped != null) {
                                    Modifier.clickable { onHandPieceTapped(pt) }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 持駒の文字は常に正立表示にする（flip 時も回転しない）。盤上の駒は
                        // 手番側に応じて180度回転させるが、持駒は読みやすさのため向きを固定する。
                        Text(
                            text = label,
                            fontSize = (cellSize.value * 0.45).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        // 段ラベル列分のスペーサー（盤と幅を合わせる）
        Spacer(Modifier.width(CoordinateLabelTrack + CoordinateLabelGap))
    }
}

/**
 * 持駒行のサイドラベル。「☗持駒」「☖持駒」形式で1行に収める。
 */
@Composable
fun HandRowLabel(isBlack: Boolean) {
    val sideLabel = if (isBlack) "☗持駒" else "☖持駒"
    Text(
        text = sideLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.shogiColors.ink2,
        maxLines = 1,
    )
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewInitialPosition() {
    ShogiTheme {
        Surface {
            ShogiBoardView(
                sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
            )
        }
    }
}

@Preview
@Composable
private fun PreviewMidgamePosition() {
    ShogiTheme {
        Surface {
            // miyado_game1.kif の41手目直前（仕様書指定）
            ShogiBoardView(
                sfen = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
                lastMoveDest = 3 to 4, // 最新手ハイライトサンプル
            )
        }
    }
}

@Preview
@Composable
private fun PreviewFlippedPosition() {
    ShogiTheme {
        Surface {
            ShogiBoardView(
                sfen = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL b - 1",
                flip = true,
            )
        }
    }
}

