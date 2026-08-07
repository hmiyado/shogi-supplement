package dev.miyado.shogisupplement.ui.report

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

/**
 * 解析中レポート画面。解析開始と同時にこの画面へ遷移し、完了したら [ReportScreen]
 * （GameRecord/BlunderRecordがDBに揃った状態）へ差し替わる。
 *
 * [ReportScreen] と違い、悪手選択・検討モード・棋譜リストシート・KIFコピー等は持たない
 * （盤・グラフともタップ/ドラッグ無効。反映済み最新手を表示するだけの読み取り専用画面）。
 *
 * @param titleHint トップバーの暫定タイトル
 * @param moves 棋譜のUSI手列（KIFパース直後に確定済み）
 * @param userSide ユーザーの側。null可（先後未確定のインポート経路はflip=falseのまま）
 * @param progressive アキュムレータの現在の状態
 * @param onBack トップバーの戻る
 */
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.BACK,
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
                    text = AppStrings.analyzingProgress(latestPly, maxPly),
                    textColor = MaterialTheme.shogiColors.ink2,
                )

                HorizontalDivider(
                    color = MaterialTheme.shogiColors.line,
                    modifier = Modifier.testTag("report_divider"),
                )

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
 * 常に「—」（[AppStrings.EVAL_UNAVAILABLE]）で固定表示し、悪手一覧ボタンは無効のまま出す
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
            StatLine(AppStrings.BLUNDER_RATE_LABEL, AppStrings.EVAL_UNAVAILABLE)
            Spacer(Modifier.height(2.dp))
            StatLine(AppStrings.MATCH_RATE_LABEL, AppStrings.EVAL_UNAVAILABLE)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AppStrings.GAME_STRENGTH_PREFIX,
                    style = MaterialTheme.typography.labelSmall,
                    color = shogiColors.ink2,
                )
                Text(
                    AppStrings.EVAL_UNAVAILABLE,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = IbmPlexMonoFamily),
                    color = shogiColors.ink2,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(AppStrings.VIEW_BLUNDER_LIST, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
