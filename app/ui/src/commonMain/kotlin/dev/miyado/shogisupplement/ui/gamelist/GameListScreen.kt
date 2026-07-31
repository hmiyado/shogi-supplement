package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameListFilter
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.filterGames
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.GameCard

/**
 * 棋譜一覧画面。VM/Android 非依存の純Composable。GameRecord/GameCard/AppStrings のみに依存し、
 * MainViewModel・Android API への依存はない。GameCard は ui.common（HomeScreen とも共用）。
 *
 * 絞り込み状態はこの画面のローカル state で保持し、画面再訪時には保持しない。
 * 画面を離れて戻ると必ず未フィルタから始まる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    games: List<GameRecord>,
    pendingUploadCount: Int = 0,
    isUploading: Boolean = false,
    uploadResult: String? = null,
    onBack: () -> Unit,
    onGameClick: (GameRecord) -> Unit,
    onUpload: () -> Unit = {},
) {
    var filter by remember { mutableStateOf(GameListFilter()) }
    val filteredGames = games.filterGames(filter)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.GAME_LIST_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.BACK,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (games.isNotEmpty()) {
                item {
                    GameListFilterBar(
                        allGames = games,
                        filter = filter,
                        onFilterChange = { filter = it },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameListCountText(shownCount = filteredGames.size, totalCount = games.size)
                        if (filter.isActive) {
                            TextButton(onClick = { filter = GameListFilter() }) {
                                Text(AppStrings.GAME_LIST_FILTER_CLEAR)
                            }
                        }
                    }
                }
            }
            // 未アップロード一括アップロードボタン（提供有効＋未アップロードあり時のみ表示）
            if (pendingUploadCount > 0 || uploadResult != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = onUpload,
                            enabled = !isUploading && pendingUploadCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(AppStrings.accountManualUploadButton(pendingUploadCount))
                            }
                        }
                        if (uploadResult != null) {
                            Text(
                                uploadResult,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(filteredGames) { game ->
                GameCard(
                    game = game,
                    onClick = { onGameClick(game) },
                )
            }
        }
    }
}
