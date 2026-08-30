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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameListFilter
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.filterGames
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.DeleteGameConfirmDialog
import dev.miyado.shogisupplement.ui.common.GameCard
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import dev.miyado.shogisupplement.ui.theme.shogiColors
import dev.miyado.shogisupplement.upload.DeleteGameOutcome
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    // selectedIdsから都度導出しない: 絞り込み変更やダイアログを閉じずに再確定した場合の
    // games/selectedIdsの変化が、確認中の対象・件数・見出しへ即座に反映されないようにする。
    var dialogTargets by remember { mutableStateOf(emptyList<GameRecord>()) }
    val filteredGames = games.filterGames(filter)
    val scope = rememberCoroutineScope()
    val shogiColors = MaterialTheme.shogiColors

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) {
                            AppStrings.gameListSelectedCount(selectedIds.size)
                        } else {
                            AppStrings.GAME_LIST_TITLE
                        },
                    )
                },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = AppStrings.GAME_LIST_EXIT_SELECTION_ICON_DESC,
                            )
                        }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = AppStrings.BACK,
                            )
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                // filteredGamesから作る: 画面に見えている棋譜だけを削除対象にする
                                // 不変条件をここで直接担保する（絞り込み変更時のselectedIdsの
                                // 絞り込みとは別に、消費側でも局所的に保証する）。
                                dialogTargets = filteredGames.filter { it.id in selectedIds }
                                showBulkDeleteDialog = true
                            },
                            enabled = selectedIds.isNotEmpty(),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = shogiColors.loss),
                            modifier = Modifier.testTag("game_list_confirm_delete_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = AppStrings.GAME_DELETE_ICON_DESC,
                            )
                        }
                    } else {
                        if (canDelete && games.isNotEmpty()) {
                            IconButton(
                                onClick = { selectionMode = true },
                                enabled = filteredGames.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = shogiColors.loss),
                                modifier = Modifier.testTag("game_list_select_mode_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = AppStrings.GAME_LIST_SELECT_TO_DELETE_ICON_DESC,
                                )
                            }
                        }
                        topBarActions()
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
                    onClick = {
                        if (selectionMode) {
                            selectedIds = if (game.id in selectedIds) {
                                selectedIds - game.id
                            } else {
                                selectedIds + game.id
                            }
                        } else {
                            onGameClick(game)
                        }
                    },
                    selectable = selectionMode,
                    selected = game.id in selectedIds,
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
                // 選択中の棋譜が絞り込みで一覧から消えると、見えていないのに削除対象へ
                // 残ってしまう（画面に見えている棋譜だけを削除対象にする）。
                selectedIds = selectedIds.intersect(games.filterGames(draftFilter).map { it.id }.toSet())
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
        show = showBulkDeleteDialog,
        canDeleteServer = dialogTargets.any { it.uploadedAt != null },
        count = dialogTargets.size,
        onConfirm = { deleteServer, onResult ->
            val targets = dialogTargets
            scope.launch {
                val result = deleteSelectedGames(targets, deleteServer, onDeleteGame)
                if (result.outcome == DeleteGameOutcome.ServerFailed) {
                    selectedIds = result.remainingIds
                    // ダイアログを閉じずに再確定された場合、失敗分だけを再送するようにする
                    // （dialogTargetsを開いた時点のままにすると、成功済みの分まで再送してしまう）。
                    dialogTargets = targets.filter { it.id in result.remainingIds }
                } else {
                    exitSelectionMode()
                }
                onResult(result.outcome)
            }
        },
        onDismiss = { showBulkDeleteDialog = false },
    )
}

internal data class BulkDeleteResult(val remainingIds: Set<Long>, val outcome: DeleteGameOutcome)

/**
 * 選択された棋譜を順番に削除する。サーバー削除に失敗した棋譜だけ[BulkDeleteResult.remainingIds]
 * へ残し、再確定で失敗分だけ再試行できるようにする（[GameDeleter][dev.miyado.shogisupplement.upload.GameDeleter]
 * はサーバー削除失敗時にローカルも残すため、失敗＝その棋譜は未削除のまま）。
 */
internal suspend fun deleteSelectedGames(
    targets: List<GameRecord>,
    deleteServer: Boolean,
    onDeleteGame: (
        game: GameRecord,
        deleteServer: Boolean,
        onResult: (DeleteGameOutcome) -> Unit,
    ) -> Unit,
): BulkDeleteResult {
    val remaining = mutableSetOf<Long>()
    var anyFailed = false
    for (target in targets) {
        val outcome = suspendCancellableCoroutine<DeleteGameOutcome> { cont ->
            onDeleteGame(target, deleteServer) {
                if (cont.isActive) cont.resumeWith(Result.success(it))
            }
        }
        if (outcome == DeleteGameOutcome.ServerFailed) {
            anyFailed = true
            remaining += target.id
        }
    }
    return BulkDeleteResult(
        remainingIds = remaining,
        outcome = if (anyFailed) DeleteGameOutcome.ServerFailed else DeleteGameOutcome.Success,
    )
}
