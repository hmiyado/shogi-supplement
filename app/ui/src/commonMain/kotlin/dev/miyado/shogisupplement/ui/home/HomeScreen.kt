package dev.miyado.shogisupplement.ui.home


import dev.miyado.shogisupplement.ui.text.title

import org.jetbrains.compose.resources.vectorResource

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import dev.miyado.shogisupplement.ui.common.scaffoldTopAndSides
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.pipeline.InProgressAnalysis
import dev.miyado.shogisupplement.ui.common.AnalyzingGameCard
import dev.miyado.shogisupplement.ui.common.AppTitleIcon
import dev.miyado.shogisupplement.ui.common.GameCard
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShogiColors
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.TextStyleDataLarge
import dev.miyado.shogisupplement.ui.theme.shogiColors

// HomeScreen・StrengthCard を持つ（GameCard は ui.common で共用）。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    pastGames: List<GameRecord>,
    isLoggedIn: Boolean = false,
    strengthCard: StrengthCardData? = null,
    todaysDrillHint: TodaysDrillHint? = null,
    drillRecordCard: DrillRecordCardData? = null,
    /** 解析中セッション（完了・失敗すると一覧から消え、pastGames側の通常カードに置き換わる）。 */
    analyzingSessions: List<InProgressAnalysis> = emptyList(),
    onOpenKif: () -> Unit,
    onGameClick: (GameRecord) -> Unit,
    onAnalyzingClick: (InProgressAnalysis) -> Unit = {},
    onStartDrill: () -> Unit = {},
    onStartMateDrill: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenRepertoire: (() -> Unit)? = null,
    onViewAllGames: (() -> Unit)? = null,
    onOpenStrengthHelp: () -> Unit = {},
    /** 推定棋力カードのタップ（「?」アイコン部分を除く）。推定棋力詳細画面へ遷移する。 */
    onOpenStrengthDetail: () -> Unit = {},
    onOpenDrillRecordDetail: () -> Unit = {},
) {
    val labelHomeRepertoire = stringResource(Res.string.home_repertoire)

    val shogiColors = MaterialTheme.shogiColors
    val rootTabHost = dev.miyado.shogisupplement.ui.navigation.LocalRootTabHost.current
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = if (dev.miyado.shogisupplement.ui.navigation.rootTabsOverlayContent) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surface),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppTitleIcon()
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(Res.string.app_title),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                },
                actions = {
                    // 設定画面への導線（⚙）
                    if (dev.miyado.shogisupplement.ui.navigation.rootTabsOverlayContent) {
                        dev.miyado.shogisupplement.ui.navigation.PlatformGlassAction("gearshape", stringResource(Res.string.settings_title), vectorResource(Res.drawable.ic_outlined_settings), onOpenSettings)
                    } else {
                        IconButton(onClick = onOpenSettings) {
                            Icon(vectorResource(Res.drawable.ic_outlined_settings), contentDescription = stringResource(Res.string.settings_title), tint = shogiColors.ink2)
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!dev.miyado.shogisupplement.ui.navigation.rootTabsOverlayContent) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dev.miyado.shogisupplement.ui.navigation.LocalRootTabBottomPadding.current),
            ) {
                // 余白を最大幅の内側に置く: 外側だと本文のカードより16dpずつ広いボタンになる。
                Box(
                    modifier = Modifier
                        .adaptiveContentWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = onOpenKif,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.home_open_kif))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scaffoldTopAndSides(padding)
                .adaptiveContentWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onOpenRepertoire != null && !rootTabHost) {
                item { TextButton(onClick = onOpenRepertoire) { Text(labelHomeRepertoire) } }
            }
            if (strengthCard != null) {
                item {
                    StrengthCard(
                        strengthCard = strengthCard,
                        shogiColors = shogiColors,
                        onHelpClick = onOpenStrengthHelp,
                        onCardClick = onOpenStrengthDetail,
                    )
                }
            }

            if (drillRecordCard != null) {
                item {
                    DrillRecordCard(
                        drillRecordCard = drillRecordCard,
                        onCardClick = onOpenDrillRecordDetail,
                        shogiColors = shogiColors,
                    )
                }
            }

            if (todaysDrillHint != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        border = BorderStroke(1.dp, shogiColors.line),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(Res.string.home_todays_drill_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = shogiColors.ink2,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.home_drill_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = onStartDrill, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                                    Text(stringResource(Res.string.drill_title))
                                }
                                OutlinedButton(
                                    onClick = onStartMateDrill,
                                    enabled = todaysDrillHint.hasMateDrill,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                    border = BorderStroke(1.dp, if (todaysDrillHint.hasMateDrill) MaterialTheme.colorScheme.primary else shogiColors.line),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(Res.string.drill_mate_title))
                                }
                            }
                            Text(
                                stringResource(if (todaysDrillHint.hasMateDrill) Res.string.drill_mate_description else Res.string.drill_mate_empty_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = shogiColors.ink3,
                            )
                        }
                    }
                }
            }

            if (pastGames.isEmpty() && analyzingSessions.isEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.home_no_games),
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
                            stringResource(Res.string.home_recent_analyses),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        // 絞り込みが棋譜一覧固有の機能のため、件数によらず棋譜が1件でもあれば導線を出す
                        // （解析中セッションのみで保存済み棋譜が0件のときは一覧に見せるものが無いため出さない）
                        if (pastGames.isNotEmpty() && onViewAllGames != null) {
                            TextButton(onClick = onViewAllGames) {
                                Text(
                                    stringResource(Res.string.home_view_all),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                // 解析中セッションは3局の枠外・先頭に出す（完了済みの3局を押し出さない）
                items(analyzingSessions, key = { "analyzing-${it.id}" }) { session ->
                    AnalyzingGameCard(
                        session = session,
                        onClick = { onAnalyzingClick(session) },
                    )
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


@Composable
fun StrengthCard(
    strengthCard: StrengthCardData,
    shogiColors: ShogiColors,
    // 「?」タップはダイアログではなくヘルプ画面の推定棋力節へ遷移する
    onHelpClick: () -> Unit = {},
    // カード本体タップ（「?」の領域を除く）は推定棋力詳細画面へ遷移する
    onCardClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCardClick),
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
                    text = stringResource(Res.string.strength_card_title),
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
                Regex("""^(-?\d+(?:\.\d+)?\+?)([^±]*)(±\d+(?:\.\d+)?)$""").find(strengthCard.displayText)
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

@Composable
fun DrillRecordCard(
    drillRecordCard: DrillRecordCardData,
    shogiColors: ShogiColors,
    onCardClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.drill_record_card_title),
                style = MaterialTheme.typography.labelLarge,
                color = shogiColors.ink2,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = drillRecordCard.activeDaysInWindow.toString(),
                    style = TextStyleDataLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.drill_record_active_days_suffix),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.drill_record_window_prefix))
                        withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) {
                            append(drillRecordCard.windowDays.toString())
                        }
                        append(stringResource(Res.string.drill_record_window_suffix))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = shogiColors.ink2,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.drill_record_total_prefix))
                    withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) {
                        append(drillRecordCard.totalAttempts.toString())
                    }
                    append(stringResource(Res.string.drill_record_total_suffix))
                    // 未達成（0回）は0を見せるより丸ごと省く方が素直なので条件付きで足す
                    if (drillRecordCard.weekStreakCount > 0) {
                        append(" ・ ")
                        append(stringResource(Res.string.drill_record_week_streak_prefix))
                        withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily)) {
                            append(drillRecordCard.weekStreakCount.toString())
                        }
                        append(stringResource(Res.string.drill_record_week_streak_suffix))
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = shogiColors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
