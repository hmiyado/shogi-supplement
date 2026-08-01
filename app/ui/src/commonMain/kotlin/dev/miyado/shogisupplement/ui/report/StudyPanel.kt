package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.LightInk
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import dev.miyado.shogisupplement.ui.theme.TextStyleDataMove
import dev.miyado.shogisupplement.ui.theme.shogiColors

/**
 * 検討モードのパネル（罫線から下の「グラフ＋サマリー」領域を検討中はこれに丸ごと入れ替える）。
 *
 * 外形は呼び出し側の `Modifier.fillMaxSize()` で非検討時の SUMMARY 領域と同じ高さになる
 * （高さを個別に計算して揃えているのではなく、同じ排他スロットを共有することで構造的に
 * 一致させている。DESIGN.md No-jitter原則）。
 *
 * 内部は上から: 見出し（「検討中」。終了ボタンはナビ行側にのみ置く——実機確認で
 * 「終了ボタンが2つある」との指摘があり撤去した）／分岐元行／手順チップ列（横スクロール・
 * 固定高さ）／可変の空白／評価スロット（下端固定・固定高さ）。手順チップ列を横スクロール
 * にしているのは、折り返し（FlowRow）だと分岐の増減や手数でパネルの外形自体が変わって
 * しまうため（「パネル外形は不変」要件を満たすには行数が変わらないことが必須）。
 */
@Composable
internal fun StudyPanel(
    studyState: StudyState,
    onChipTapped: (Int) -> Unit,
    onBranchChipTapped: (Int) -> Unit,
    onBranchPopupDismiss: () -> Unit,
    onBranchOptionSelected: (depth: Int, moveUsi: String) -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shogiColors = MaterialTheme.shogiColors
    val notations = remember(studyState.baseSfen, studyState.displayLine) {
        buildMoveNotations(studyState.baseSfen, studyState.displayLine)
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = AppStrings.STUDY_PANEL_TITLE,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = ShipporiMinchoFamily,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // ── 分岐元行: 「42手目 ▲３四飛（−320）から分岐」 ──
            Text(
                text = AppStrings.studyOriginLine(studyState.origin.label),
                style = MaterialTheme.typography.bodySmall,
                color = shogiColors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            // displayLine 全体を描画する（moves より先＝まだ進んでいない手も淡色で表示し
            // 続ける。実機確認: 「戻ると先のチップが消える」対応。現在手は highlight 背景）。
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                studyState.displayLine.forEachIndexed { depth, moveUsi ->
                    val isCurrent = depth == studyState.moves.size - 1
                    val isFuture = depth >= studyState.moves.size
                    val hasBranch = studyState.branchFlags.getOrNull(depth) == true
                    val evalSuffix = (studyState.chipEvalStates.getOrNull(depth) as? StudyEvalState.Value)
                        ?.let { AppStrings.studyChipEvalSuffix(it.label.text) }
                    Box {
                        StudyMoveChip(
                            label = notations.getOrElse(depth) { moveUsi },
                            evalSuffix = evalSuffix,
                            isCurrent = isCurrent,
                            isFuture = isFuture,
                            hasBranch = hasBranch,
                            onClick = {
                                if (hasBranch) onBranchChipTapped(depth) else onChipTapped(depth + 1)
                            },
                        )
                        if (studyState.openBranchPopupDepth == depth) {
                            // ポップの兄弟変化も棋譜表記で出す（このdepthに至る直前の局面は
                            // 全兄弟で共通なので、盤面を1回だけ組み立てて使い回す）。
                            val popupBoard = remember(studyState.baseSfen, studyState.displayLine, depth) {
                                runCatching {
                                    ShogiBoard.fromSfen(sfenAfterMoves(studyState.baseSfen, studyState.displayLine, depth))
                                }.getOrNull()
                            }
                            DropdownMenu(
                                expanded = true,
                                onDismissRequest = onBranchPopupDismiss,
                            ) {
                                studyState.branchPopupOptions.forEach { option ->
                                    val optionNotation = popupBoard?.let { board ->
                                        runCatching { JapaneseNotation.format(option.moveUsi, board) }.getOrNull()
                                    } ?: option.moveUsi
                                    val optionEvalText = (option.evalState as? StudyEvalState.Value)
                                        ?.label?.text ?: AppStrings.STUDY_BRANCH_EVAL_UNKNOWN
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "$optionNotation  $optionEvalText" +
                                                    if (option.isCurrent) AppStrings.STUDY_BRANCH_CURRENT_SUFFIX else "",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        },
                                        onClick = {
                                            if (!option.isCurrent) onBranchOptionSelected(depth, option.moveUsi)
                                            onBranchPopupDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 可変の空白: 評価スロットを下端に固定するための伸縮領域（margin-top:autoに相当）。
            Spacer(Modifier.weight(1f))

            HorizontalDivider(color = shogiColors.line)

            // 手ごとの評価値はチップ側に併記するため、スロットは数値を持たない。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (val es = studyState.evalState) {
                    StudyEvalState.None, StudyEvalState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (es is StudyEvalState.Error) {
                                Text(
                                    AppStrings.evalSuffix(AppStrings.EVAL_UNAVAILABLE),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = shogiColors.ink2,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            AnalyzeButton(enabled = studyState.moves.isNotEmpty(), onClick = onAnalyze)
                        }
                    }
                    StudyEvalState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.EVAL_LOADING, style = MaterialTheme.typography.bodySmall, color = shogiColors.ink2)
                        }
                    }
                    is StudyEvalState.Value -> {
                        Text(
                            AppStrings.STUDY_EVAL_ANALYZED,
                            style = MaterialTheme.typography.bodyMedium,
                            color = shogiColors.ink2,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 検討パネルの「解析」ボタン。「進む」の意匠は文字（▶等）ではなく
 * KeyboardArrowRight アイコン＋「+」で表す（miyadoさん実機確認: 生の「▶」文字がiOSで
 * 絵文字レンダリングされてしまうため。読み筋延長ボタンと同じ表現に統一）。
 */
@Composable
private fun AnalyzeButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(AppStrings.STUDY_ANALYZE_LABEL)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text("+")
    }
}

/**
 * 手順チップ1つ（現在手=highlight背景・濃墨文字、分岐あり=primary枠＋末尾に下向き
 * チェブロン、先の手（まだ進んでいない）=ink3の淡色）。
 *
 * 分岐マークは絵文字/記号（⑂等）ではなく Material の KeyboardArrowDown アイコンを使う
 * （miyadoさん指定: フォントカバレッジの差でiOS/Androidの見た目が割れるのを避けるため）。
 * ▽▼は後手記号と衝突するため使わない。
 *
 * 現在手のチップは highlight（卵黄）背景になるが、卵黄は面専用の色で文字色には使えない。
 * ダークテーマの highlight は中明度の黄土色で、通常の（テーマ追従の）文字色を乗せると
 * コントラスト不足になるため、現在手チップの文字はテーマによらず常に LightInk
 * （濃墨の固定色）を使う（miyadoさん実機確認）。
 */
@Composable
private fun StudyMoveChip(
    label: String,
    evalSuffix: String?,
    isCurrent: Boolean,
    isFuture: Boolean,
    hasBranch: Boolean,
    onClick: () -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
    val textColor = when {
        isCurrent -> LightInk
        isFuture -> shogiColors.ink3
        else -> Color.Unspecified
    }
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (isCurrent) shogiColors.highlight else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp),
        border = if (hasBranch) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (evalSuffix != null) "$label$evalSuffix" else label,
                style = TextStyleDataMove,
                color = textColor,
                maxLines = 1,
            )
            if (hasBranch) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = AppStrings.STUDY_BRANCH_ICON_DESC,
                    tint = if (isCurrent) LightInk else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp).padding(start = 2.dp),
                )
            }
        }
    }
}

/**
 * baseSfen から moves を順に指したときの各手の日本語表記を返す（moves と同じ長さ）。
 * 画面内の他の手表記と同じ変換（JapaneseNotation.format）を手順全体に適用する。
 *
 * Why not 1つの ShogiBoard を使い回して逐次 push しないか: 途中の1手で notation 変換や
 * push が失敗すると、以降の手すべてが USI 表記へ道連れでフォールバックしてしまう
 * （実機確認: 分岐の手がすべて USI 表記のまま出ていた不具合の原因）。
 * 「depth ごとに baseSfen から独立に組み立て直す」方式にすることで、1手の失敗を
 * その手だけに閉じ込める（手数は小さいため計算コストは無視できる）。
 */
private fun buildMoveNotations(baseSfen: String, moves: List<String>): List<String> =
    moves.indices.map { depth ->
        val prevSfen = sfenAfterMoves(baseSfen, moves, depth)
        runCatching { JapaneseNotation.format(moves[depth], ShogiBoard.fromSfen(prevSfen)) }
            .getOrElse { moves[depth] }
    }

/** baseSfen から moves を steps 手だけ進めた局面の SFEN を返す（1手でも失敗したらそこで打ち切る）。 */
private fun sfenAfterMoves(baseSfen: String, moves: List<String>, steps: Int): String {
    val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrElse { ShogiBoard() }
    for (i in 0 until steps) {
        runCatching { board.push(ShogiMove.fromUsi(moves[i])) }.onFailure { return board.toSfen() }
    }
    return board.toSfen()
}
