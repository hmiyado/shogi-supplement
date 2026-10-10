package dev.miyado.shogisupplement.ui.report

import org.jetbrains.compose.resources.vectorResource

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.pipeline.ProgressiveReportState
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

/** 解析中の読み取り専用レポート画面。 @param titleHint 暫定タイトル。 @param moves 棋譜のUSI手列。 @param userSide ユーザーの先後。 @param progressive 現在の累積状態。 @param onBack 戻る処理。 */
@Composable
fun AnalyzingReportScreen(
    titleHint: String,
    moves: List<String>,
    userSide: String?,
    progressive: ProgressiveReportState,
    onBack: () -> Unit,
) {
    val flip = userSide == "gote"
    val maxPly = moves.size
    val latestPly = (progressive.confirmedThrough - 1).coerceAtLeast(0)

    val currentSfen = remember(moves, latestPly) { computeSfenAtStep(null, moves, latestPly) }
    val lastMoveDest = remember(moves, latestPly) {
        if (latestPly <= 0) {
            null
        } else {
            moves.getOrNull(latestPly - 1)?.let { usiStr ->
                runCatching {
                    val move = ShogiMove.fromUsi(usiStr)
                    move.to.file to move.to.rank
                }.getOrNull()
            }
        }
    }
    val evalGraphPoints = remember(progressive) {
        buildProgressiveEvalGraphPoints(progressive.revealedEvals, userIsGote = flip)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_auto_mirrored_filled_arrow_back),
                            contentDescription = stringResource(Res.string.back),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = titleHint,
                        style = TextStyle(
                            fontFamily = ShipporiMinchoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * 0.45f),
                ) {
                    ShogiBoardView(
                        sfen = currentSfen,
                        flip = flip,
                        lastMoveDest = lastMoveDest,
                        onSquareTapped = null,
                        onHandPieceTapped = null,
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                            .testTag("analyzing_board_scrim"),
                    )
                }

                ReportNavBannerRow(
                    text = UiStrings.analyzingProgress(latestPly, maxPly),
                    textColor = MaterialTheme.shogiColors.ink2,
                )

                HorizontalDivider(
                    color = MaterialTheme.shogiColors.line,
                    modifier = Modifier.testTag("report_divider"),
                )

                // 解析完了でReportScreenへ入れ替わる。同じ位置にタブ行を置かないと
                // その瞬間に本文が44dpずれる（DESIGN.md No-jitter原則）。
                ReportTabRow(
                    active = ReportBodyMode.SUMMARY,
                    onSelect = {},
                    isEnabled = { false },
                )
                Spacer(Modifier.height(4.dp))

                Column(modifier = Modifier.fillMaxSize()) {
                    EvalGraphCard(
                        points = evalGraphPoints,
                        maxPly = maxPly,
                        blunderPlies = progressive.revealedBlunderPlies,
                        analyzingThroughPly = progressive.confirmedThrough,
                        interactive = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    AnalyzingSummaryCard(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

/**
 * 解析中サマリーカード。悪手率・一致率・推定棋力は完了まで確定できないため
 * 常に「—」（[stringResource(Res.string.eval_unavailable)]）で固定表示し、悪手一覧ボタンは無効のまま出す
 * （watermarkの進行で行が増減するとNo-jitter原則に反するため、完了まで内容を変えない）。
 */
@Composable
private fun AnalyzingSummaryCard(modifier: Modifier = Modifier) {
    val shogiColors = MaterialTheme.shogiColors
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            StatLine(stringResource(Res.string.blunder_rate_label), stringResource(Res.string.eval_unavailable))
            Spacer(Modifier.height(2.dp))
            StatLine(stringResource(Res.string.match_rate_label), stringResource(Res.string.eval_unavailable))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.game_strength_prefix),
                    style = MaterialTheme.typography.labelSmall,
                    color = shogiColors.ink2,
                )
                Text(
                    stringResource(Res.string.eval_unavailable),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = IbmPlexMonoFamily),
                    color = shogiColors.ink2,
                )
            }
        }
    }
}
