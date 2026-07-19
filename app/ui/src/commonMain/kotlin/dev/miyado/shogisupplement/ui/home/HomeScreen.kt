package dev.miyado.shogisupplement.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.GameCard
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShogiColors
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.TextStyleDataLarge
import dev.miyado.shogisupplement.ui.theme.shogiColors

// このファイルは HomeScreen 系（HomeScreen・StrengthCard）を持つ。
// GameCard は ui.common（GameListScreen・ErrorScreen とも共用）。
// GameListScreen・ErrorScreen・AnalyzingScreen・RatingSettingsDialog・UserSideDialog 等の
// KIF 取り込みフロー関連ダイアログは MainActivity.kt にある。
//
// タイトル左のアプリアイコン（R.drawable.ic_app_title_icon・androidx.compose.ui.res.
// painterResource）は commonMain が Android リソースを直接参照できないため、
// titleIcon: @Composable () -> Unit スロットへホイストしている（実装は MainActivity.kt 側）。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    pastGames: List<GameRecord>,
    isLoggedIn: Boolean = false,
    strengthCard: StrengthCardData? = null,
    todaysDrillHint: TodaysDrillHint? = null,
    onOpenKif: () -> Unit,
    onGameClick: (GameRecord) -> Unit,
    onStartDrill: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onViewAllGames: (() -> Unit)? = null,
    onOpenStrengthHelp: () -> Unit = {},
    /** タイトル左の小さなアプリアイコン（Android専用リソースのためホイスト。既定は非表示）。 */
    titleIcon: @Composable () -> Unit = {},
) {
    val shogiColors = MaterialTheme.shogiColors
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        titleIcon()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            AppStrings.APP_TITLE,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                },
                actions = {
                    // 設定画面への導線（⚙）
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = AppStrings.SETTINGS_TITLE,
                            tint = shogiColors.ink2,
                        )
                    }
                },
            )
        },
        bottomBar = {
            // 「棋譜を追加する」を最下部固定
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onOpenKif,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(AppStrings.HOME_OPEN_KIF)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 1. 推定棋力カード ─────────────────────────────────────────
            if (strengthCard != null) {
                item {
                    StrengthCard(
                        strengthCard = strengthCard,
                        shogiColors = shogiColors,
                        onHelpClick = onOpenStrengthHelp,
                    )
                }
            }

            // ── 2. 「今日の1問」カード（候補ゼロなら非表示のみ）──────────
            if (todaysDrillHint != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onStartDrill),
                        colors = CardDefaults.cardColors(
                            containerColor = shogiColors.highlightSoft,
                        ),
                        border = BorderStroke(1.dp, shogiColors.highlight),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                AppStrings.HOME_TODAYS_DRILL_TITLE,
                                style = MaterialTheme.typography.labelLarge,
                                color = shogiColors.ink2,
                            )
                            Spacer(Modifier.height(4.dp))
                            // ファイル名は表示しない（2026-07-15 miyadoさん指示。出典は手数のみ）
                            Text(
                                text = AppStrings.homeTodaysDrillPly(todaysDrillHint.ply),
                                style = TextStyleData,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                AppStrings.HOME_TODAYS_DRILL_TAP,
                                style = MaterialTheme.typography.bodySmall,
                                color = shogiColors.ink3,
                            )
                        }
                    }
                }
            }

            // ── 3. 直近の解析リスト（最大3局 + 「すべて見る」）──────
            if (pastGames.isEmpty()) {
                item {
                    Text(
                        AppStrings.HOME_NO_GAMES,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            AppStrings.HOME_RECENT_ANALYSES,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (pastGames.size > 3 && onViewAllGames != null) {
                            TextButton(onClick = onViewAllGames) {
                                Text(
                                    AppStrings.HOME_VIEW_ALL,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                items(pastGames.take(3)) { game ->
                    GameCard(
                        game = game,
                        onClick = { onGameClick(game) },
                    )
                }
            }
        }
    }
}


// ─── 強さ指標カード ──────────────────────────────────────────────────────────

@Composable
fun StrengthCard(
    strengthCard: StrengthCardData,
    shogiColors: ShogiColors,
    // 「?」タップはダイアログではなくヘルプ画面の推定棋力節へ遷移する
    onHelpClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // タイトル行: 「推定棋力」ラベル ＋ 小さな「?」アイコン
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = AppStrings.STRENGTH_CARD_TITLE,
                    style = MaterialTheme.typography.labelLarge,
                    color = shogiColors.ink2,
                    modifier = Modifier.weight(1f),
                )
                // ? アイコン（小さな TextButton。最小タップ領域確保）。
                // タップでダイアログではなくヘルプ画面の推定棋力節へ遷移する
                TextButton(
                    onClick = onHelpClick,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp),
                ) {
                    Text(
                        "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = shogiColors.ink3,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // "51 ±25" / "77+ ±22" / "30未満 ±27" → 値(Mono大) / 接尾語(Sans ink2) / 誤差幅(Mono小・ink3) に分解
            val match = remember(strengthCard.displayText) {
                Regex("""^(\d+\+?)([^±]*)(±\d+)?$""").find(strengthCard.displayText)
            }
            val valueText = match?.groupValues?.get(1) ?: strengthCard.displayText
            val suffixText = match?.groupValues?.get(2)?.trim().orEmpty()
            val marginText = match?.groupValues?.get(3).orEmpty()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = valueText,
                    style = TextStyleDataLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (suffixText.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = suffixText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = shogiColors.ink2,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (marginText.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = marginText,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = IbmPlexMonoFamily),
                        color = shogiColors.ink3,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = strengthCard.detailText,
                style = MaterialTheme.typography.labelMedium,
                color = shogiColors.ink3,
            )
            // 申告棋力（未設定なら非表示）
            if (strengthCard.declaredRankLine != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = strengthCard.declaredRankLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = shogiColors.ink3,
                )
            }
        }
    }
}
