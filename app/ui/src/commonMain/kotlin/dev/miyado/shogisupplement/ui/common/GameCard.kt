package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameAnalysisStatus
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.pipeline.InProgressAnalysis
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.shogiColors

// HomeScreen・GameListScreen・ErrorScreen（KIF取り込みフローの重複局面ダイアログ）から共用。
// 日付表示は java.text.SimpleDateFormat が commonMain で使えないため、
// ReportPlatform.kt の formatDateTime（expect/actual）を使う。

@Composable
fun GameCard(
    game: GameRecord,
    onClick: () -> Unit,
) {
    // sourcePlace をタイトルとして優先表示（正規化コードのまま出さず表示ラベルへ変換する。
    // ReportScreenのトップバーと同じ変換で、ホームとレポートの表記を一致させる）
    val sourcePlaceLabel = AppStrings.sourcePlaceLabel(game.sourcePlace)
    val displayTitle = sourcePlaceLabel ?: game.fileName
    // 勝敗バッジ（userSide & gameWinner が揃っている場合のみ）
    val resultLabel: String? = when {
        game.userSide != null && game.gameWinner != null ->
            if (game.gameWinner == game.userSide) AppStrings.GAME_RESULT_WIN
            else AppStrings.GAME_RESULT_LOSS
        else -> null
    }
    val shogiColors = MaterialTheme.shogiColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                val badgeLabel = if (game.analysisStatus == GameAnalysisStatus.PENDING) {
                    AppStrings.PENDING_ANALYSIS_BADGE
                } else {
                    resultLabel
                }
                if (badgeLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    val isLoss = badgeLabel == AppStrings.GAME_RESULT_LOSS
                    Box(
                        modifier = Modifier
                            .background(
                                if (isLoss) shogiColors.lossSoft else shogiColors.primarySoft,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLoss) shogiColors.loss else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    AppStrings.gameMoveCount(game.moveCount),
                    style = TextStyleData,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDateTime(game.analyzedAt),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (game.senteName != null || game.goteName != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    AppStrings.playersLine(game.senteName, game.goteName),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // sourcePlace をタイトルに使った場合は fileName をサブテキストで表示
            if (sourcePlaceLabel != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    game.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = shogiColors.ink3,
                )
            }
        }
    }
}

/** DB保存前の未確定情報を出さず、ファイル名と進捗だけを表示する。 */
@Composable
fun AnalyzingGameCard(
    session: InProgressAnalysis,
    onClick: () -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
    val currentMove = (session.progressive.confirmedThrough - 1).coerceAtLeast(0)
    val totalMoves = session.progressive.moves.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    session.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // DESIGN.mdの意味色: 卵黄(highlight)は面専用で文字色に使用禁止・
                // 悪手/勝敗色ではない中立のバッジは「info=primary-soft面＋ink」に従う。
                Box(
                    modifier = Modifier
                        .background(shogiColors.primarySoft, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = AppStrings.ANALYZING_BADGE,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                AppStrings.analyzingProgress(currentMove, totalMoves),
                style = TextStyleData,
            )
        }
    }
}
