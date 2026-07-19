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
import dev.miyado.shogisupplement.db.GameRecord
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
    // sourcePlace をタイトルとして優先表示
    val displayTitle = game.sourcePlace ?: game.fileName
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
                if (resultLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    val isWin = resultLabel == AppStrings.GAME_RESULT_WIN
                    Box(
                        modifier = Modifier
                            .background(
                                if (isWin) shogiColors.primarySoft else shogiColors.lossSoft,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = resultLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isWin) MaterialTheme.colorScheme.primary
                                    else shogiColors.loss,
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
            if (game.sourcePlace != null) {
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
