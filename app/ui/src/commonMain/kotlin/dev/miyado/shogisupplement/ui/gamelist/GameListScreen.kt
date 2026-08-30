package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameListFilter
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.filterGames
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.DeleteGameConfirmDialog
import dev.miyado.shogisupplement.ui.common.GameCard
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import dev.miyado.shogisupplement.upload.DeleteGameOutcome

/**
 * 棋譜一覧画面。絞り込み条件はボトムシートで編集し、条件チップの出し入れでも一覧側の
 * レイアウトは変えない（DESIGN.md No-jitter原則）。
 * 絞り込み状態はローカル state のみで保持し、画面を離れて戻ると必ず未フィルタから始まる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    games: List<GameRecord>,
    pendingUploadCount: Int = 0,
    isUploading: Boolean = false,
    uploadResult: String? = null,
    canDelete: Boolean = true,
    onBack: (() -> Unit)?,
    onGameClick: (GameRecord) -> Unit,
    topBarActions: @Composable RowScope.() -> Unit = {},
    onUpload: () -> Unit = {},
    onDeleteGame: (
        game: GameRecord,
        deleteServer: Boolean,
        onResult: (DeleteGameOutcome) -> Unit,
    ) -> Unit = { _, _, onResult -> onResult(DeleteGameOutcome.Success) },
) {
    // filter: 一覧に反映済みの条件。draftFilter: シート内で編集中の条件（「検索」タップまで
    // filterには反映しない。スワイプ/スクリムでシートを閉じた場合は draftFilter を破棄する）。
    var filter by remember { mutableStateOf(GameListFilter()) }
    var draftFilter by remember { mutableStateOf(GameListFilter()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var pendingDeleteGame by remember { mutableStateOf<GameRecord?>(null) }
    val filteredGames = games.filterGames(filter)

    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.GAME_LIST_TITLE) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = AppStrings.BACK,
                            )
                        }
                    }
                },
                actions = topBarActions,
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
                    GameListFilterHeader(
                        activeCount = filter.activeCount,
                        shownCount = filteredGames.size,
                        totalCount = games.size,
                        onOpenFilter = {
                            draftFilter = filter
                            showFilterSheet = true
                        },
                    )
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
            // key = game.id: 絞り込みで件数が変わってもComposeがカードの同一性を
            // game_idで追跡する（キー無しだと位置ベースの暗黙キーで、絞り込み適用時に
            // 別ゲームのコンポジション状態を誤って引き継ぎうるため）。
            items(filteredGames, key = { it.id }) { game ->
                GameCard(
                    game = game,
                    onClick = { onGameClick(game) },
                    onDelete = if (canDelete) { { pendingDeleteGame = game } } else null,
                )
            }
        }
    }

    if (showFilterSheet) {
        GameListFilterSheet(
            allGames = games,
            filter = draftFilter,
            onFilterChange = { draftFilter = it },
            onApply = {
                filter = draftFilter
                showFilterSheet = false
            },
            onClear = {
                filter = GameListFilter()
                draftFilter = GameListFilter()
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
    }

    DeleteGameConfirmDialog(
        show = pendingDeleteGame != null,
        canDeleteServer = pendingDeleteGame?.uploadedAt != null,
        onConfirm = { deleteServer, onResult ->
            pendingDeleteGame?.let { onDeleteGame(it, deleteServer, onResult) }
        },
        onDismiss = { pendingDeleteGame = null },
    )
}
