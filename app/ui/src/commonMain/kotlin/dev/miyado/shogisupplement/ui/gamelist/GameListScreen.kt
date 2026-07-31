package dev.miyado.shogisupplement.ui.gamelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import dev.miyado.shogisupplement.ui.common.GameCard

/**
 * 棋譜一覧画面。VM/Android 非依存の純Composable。GameRecord/GameCard/AppStrings のみに依存し、
 * MainViewModel・Android API への依存はない。GameCard は ui.common（HomeScreen とも共用）。
 *
 * 絞り込み条件はボトムシートで編集する。一覧側は常に絞り込みボタンと件数表示だけの
 * 固定レイアウトで、条件チップの出し入れによる一覧側のレイアウト変化は起きない
 * （DESIGN.md No-jitter原則）。
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
    // filter: 一覧に反映済みの条件。draftFilter: シート内で編集中の条件（「検索」タップまで
    // filterには反映しない。スワイプ/スクリムでシートを閉じた場合は draftFilter を破棄する）。
    var filter by remember { mutableStateOf(GameListFilter()) }
    var draftFilter by remember { mutableStateOf(GameListFilter()) }
    var showFilterSheet by remember { mutableStateOf(false) }
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
            // インデックスではなくgame_idで追跡できるようにする（キー無し=位置ベースの
            // 暗黙キーだと、絞り込み適用時に別ゲームのコンポジション状態を誤って
            // 引き継ぎうる。件数表示の切り替えと合わせてガタつきの一因だったため付与する）。
            items(filteredGames, key = { it.id }) { game ->
                GameCard(
                    game = game,
                    onClick = { onGameClick(game) },
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
}
