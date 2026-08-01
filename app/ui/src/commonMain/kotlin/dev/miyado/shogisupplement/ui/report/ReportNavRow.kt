package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.NavIcons
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.shogiColors

@Composable
internal fun ReportNavRow(
    studyState: StudyState?,
    studySenteToMove: Boolean,
    onStudyStepBack: () -> Unit,
    onStudyExit: () -> Unit,
    navLabelAnnotated: AnnotatedString,
    onLabelClick: () -> Unit,
    canGoFirst: Boolean,
    onFirst: () -> Unit,
    canGoPrev: Boolean,
    onPrev: () -> Unit,
    canGoNext: Boolean,
    onNext: () -> Unit,
    showExtendIndicator: Boolean,
    canTriggerExtend: Boolean,
    canGoLast: Boolean,
    onLast: () -> Unit,
) {
    val shogiColors = MaterialTheme.shogiColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (studyState != null) {
                    Modifier.background(shogiColors.primarySoft)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (studyState != null) {
            // ── 検討中: ◀=1手戻し／中央=「検討N手目」（手番ヒント時は差替）／
            //    ▶=無効（プレースホルダー）／終了 ──
            // ▶（進む）を出さないのは、検討中の「進む」先＝分岐の選択はチップ列
            // （検討パネル）側の役割にしたため（ナビ行はシークのみ）。
            TextButton(
                onClick = onStudyStepBack,
                enabled = studyState.moves.isNotEmpty(),
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "1手戻る") }
            Text(
                text = when {
                    studyState.showTurnHint -> AppStrings.studyTurnHint(studySenteToMove)
                    studyState.moves.isEmpty() -> AppStrings.STUDY_START_POSITION
                    else -> AppStrings.studyPlyLabel(studyState.moves.size)
                },
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            TextButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "1手進む") }
            TextButton(
                onClick = onStudyExit,
                modifier = Modifier.height(36.dp),
            ) { Text(AppStrings.STUDY_END) }
        } else {
            // ── 非検討: |◀ ◀ 現在手（形勢）▾ ▶/▶+ ▶| ──────────────────
            // ボタン実効幅を48dp→36dpに圧縮し、中央ラベルの幅を拡幅する
            // （手数表示の見切れ対策）。
            TextButton(
                onClick = onFirst,
                enabled = canGoFirst,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Icon(NavIcons.FirstPage, contentDescription = "最初へ") }
            TextButton(
                onClick = onPrev,
                enabled = canGoPrev,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "1手戻る") }
            Text(
                text = navLabelAnnotated,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onLabelClick),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            TextButton(
                onClick = onNext,
                enabled = canGoNext,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                if (showExtendIndicator) {
                    val extendColor = if (canTriggerExtend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "1手進む",
                            tint = extendColor,
                        )
                        Text("+", color = extendColor)
                    }
                } else {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "1手進む")
                }
            }
            TextButton(
                onClick = onLast,
                enabled = canGoLast,
                modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Icon(NavIcons.LastPage, contentDescription = "最後へ") }
        }
    }
}

internal data class ReportNavInfo(
    val navLabelAnnotated: AnnotatedString,
    val studyOrigin: StudyOrigin,
    val studyOriginAbsolutePly: Int,
    val showExtendIndicator: Boolean,
    val canTriggerExtend: Boolean,
)

@Composable
internal fun rememberReportNavInfo(
    viewerMode: ViewerMode,
    clampedPly: Int,
    maxPly: Int,
    movesInMode: List<String>,
    prevSfen: String?,
    reports: List<BlunderRecord>,
    selectedBlunder: BlunderRecord?,
    positionEvals: List<PositionEvalRow>,
    evalDisplay: String,
    game: GameRecord,
    pvExtState: Map<Long, PvExtState>,
    pvExtensionEnabled: Boolean,
): ReportNavInfo {
    val shogiColors = MaterialTheme.shogiColors

    // ラベルは「N手目 ▲同　銀成」のみ（最大12文字設計）。悪手の手は文字色を loss（朱）で示す。
    // 計器行（評価値行・「この変化の形勢」行・▶ヒント行・スピナー/エラー行）は持たず、
    // 形勢をナビラベルの末尾に「（+120）」のようなサフィックスとして統合する
    // （No-jitter・DESIGN.md Layout節）。
    val currentMoveLabelState = remember(viewerMode, clampedPly, selectedBlunder, prevSfen) {
        buildCurrentMoveLabel(
            mode = viewerMode,
            plyIndex = clampedPly,
            movesInMode = movesInMode,
            prevSfen = prevSfen,
            reports = reports,
            selectedBlunder = selectedBlunder,
        )
    }

    // 本譜タブの形勢サフィックス（position_eval がある局のみ）。
    // score_cp は先手視点保存なのでユーザーが後手なら符号反転する。
    val mainlineEvalLabel = remember(clampedPly, evalDisplay, positionEvals) {
        positionEvals.firstOrNull { it.ply == clampedPly }?.let { row ->
            PositionEvalDisplay.format(
                scoreCp = row.scoreCp,
                mateIn = row.mateIn,
                userIsGote = game.userSide == "gote",
                evalDisplay = evalDisplay,
                ply = clampedPly,
            )
        }
    }
    // mainlineEvalLabel と同じ局面の自分視点 cp（検討パネルの分岐元との「差」計算に
    // 使う）。詰み局面（mateIn != null）は差の対象外とする（null のまま。評価スロットは
    // 詰み絡みの分岐元では「差」を省く単純化。cp軸の差が意味を持つのは通常局面のみのため）。
    val mainlineEvalUserCp = remember(clampedPly, positionEvals, game.userSide) {
        positionEvals.firstOrNull { it.ply == clampedPly }
            ?.takeIf { it.mateIn == null }
            ?.scoreCp
            ?.let { cp -> if (game.userSide == "gote") -cp else cp }
    }

    // 最善の変化タブの形勢サフィックス（分岐点の cp_before を全plyで固定表示）。
    // cp_before は「手番側（悪手を指した側）視点」で保存されているため、
    // position_eval と同じ先手視点に正規化してから PositionEvalDisplay.format に渡す。
    val bestPvEvalLabel = remember(selectedBlunder, evalDisplay, game.userSide) {
        if (selectedBlunder == null) return@remember null
        val moverIsGote = selectedBlunder.side == "gote"
        val userIsGote = game.userSide == "gote"
        val cpBefore = selectedBlunder.cpBefore?.toInt()
        if (cpBefore != null) {
            val senteCp = if (moverIsGote) -cpBefore else cpBefore
            val userCp = if (userIsGote) -senteCp else senteCp
            // 詰み絡み（|cp| >= 29_000）は生数字ではなく悪手カードと同じ規約表示。
            // 閾値 29_000 は詰み見逃し系の cp_before が ±(30000-|n|) にエンコードされる
            // ことに合わせた値。
            when {
                userCp >= 29_000 -> PositionEvalDisplay.EvalLabel(
                    text = AppStrings.BLUNDER_LOSS_MATE,
                    sign = 1,
                )
                userCp <= -29_000 -> PositionEvalDisplay.EvalLabel(
                    text = AppStrings.BLUNDER_AFTER_MATED,
                    sign = -1,
                )
                else -> PositionEvalDisplay.format(
                    scoreCp = senteCp,
                    mateIn = null,
                    userIsGote = userIsGote,
                    evalDisplay = evalDisplay,
                )
            }
        } else {
            val missedMateIn = selectedBlunder.missedMateIn?.toInt()
            if (missedMateIn != null) {
                PositionEvalDisplay.format(
                    scoreCp = null,
                    mateIn = if (moverIsGote) -missedMateIn else missedMateIn,
                    userIsGote = userIsGote,
                    evalDisplay = evalDisplay,
                )
            } else {
                null
            }
        }
    }
    // bestPvEvalLabel と同じ局面の自分視点 cp。詰み絡み（|cp|>=29_000
    // 相当）は対象外（null。mainlineEvalUserCp と同じ単純化）。
    val bestPvEvalUserCp = remember(selectedBlunder, game.userSide) {
        if (selectedBlunder == null) return@remember null
        val cpBefore = selectedBlunder.cpBefore?.toInt() ?: return@remember null
        val moverIsGote = selectedBlunder.side == "gote"
        val senteCp = if (moverIsGote) -cpBefore else cpBefore
        val userCp = if (game.userSide == "gote") -senteCp else senteCp
        userCp.takeIf { it in -29_000..29_000 }
    }

    // ▶延長の可視化: BEST_PV タブでライン末尾に到達しているとき、
    // ▶ボタンを「▶+」（primary色）にして延長トリガーであることを示す。
    // Loading中も「▶+」のまま無効化。エラー時はナビラベルに「（—）」を出し、
    // 「▶+」は有効なまま（再試行可）。
    val extState = selectedBlunder?.let { pvExtState[it.id] }
    val showExtendIndicator = pvExtensionEnabled &&
        viewerMode == ViewerMode.BEST_PV &&
        clampedPly >= maxPly && selectedBlunder != null
    val pvLoading = extState is PvExtState.Loading
    val canTriggerExtend = showExtendIndicator && !pvLoading
    val bestPvSuffixText = if (showExtendIndicator && extState is PvExtState.Error) {
        AppStrings.evalSuffix(AppStrings.EVAL_UNAVAILABLE)
    } else {
        bestPvEvalLabel?.let { AppStrings.evalSuffix(it.text) }
    }
    val bestPvSuffixSign = if (showExtendIndicator && extState is PvExtState.Error) {
        0
    } else {
        bestPvEvalLabel?.sign ?: 0
    }

    val navSuffixText = when (viewerMode) {
        ViewerMode.MAINLINE -> mainlineEvalLabel?.let { AppStrings.evalSuffix(it.text) }
        ViewerMode.BEST_PV -> bestPvSuffixText
    }
    val navSuffixSign = when (viewerMode) {
        ViewerMode.MAINLINE -> mainlineEvalLabel?.sign ?: 0
        ViewerMode.BEST_PV -> bestPvSuffixSign
    }
    val navSuffixColor = when {
        navSuffixSign > 0 -> MaterialTheme.colorScheme.primary
        navSuffixSign < 0 -> shogiColors.loss
        else -> MaterialTheme.colorScheme.onSurface
    }
    val navLabelAnnotated = buildAnnotatedString {
        withStyle(
            SpanStyle(color = if (currentMoveLabelState.isBlunder) shogiColors.loss else Color.Unspecified),
        ) {
            append(currentMoveLabelState.text)
        }
        if (navSuffixText != null) {
            append(" ")
            withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily, color = navSuffixColor)) {
                append(navSuffixText)
            }
        }
        // 棋譜リストシートへのタップ導線ヒント（常時付与。数字ではないのでMono無し）。
        withStyle(SpanStyle(color = shogiColors.ink2)) {
            append(AppStrings.MOVE_LIST_DROPDOWN_HINT)
        }
    }

    // 検討開始局面の絶対手数（MAINLINE=現ply、BEST_PV=blunder.ply+現ply-1）。
    // buildCurrentMoveLabel の gamePly と同じ式。検討開始（onStartStudy）に使う。
    val studyOriginAbsolutePly = when (viewerMode) {
        ViewerMode.MAINLINE -> clampedPly
        ViewerMode.BEST_PV -> (selectedBlunder?.ply?.toInt() ?: 0) + clampedPly - 1
    }

    // 検討モードの分岐元表示。現在のタブ/現在手のラベル＋形勢を再利用する
    // （検討パネルの分岐元行に「42手目 ▲３四飛（−320）」のように出す。ナビ行が検討開始
    // 直前に表示していたのと同じ内容になる）。
    val studyOriginUserCp = when (viewerMode) {
        ViewerMode.MAINLINE -> mainlineEvalUserCp
        ViewerMode.BEST_PV -> bestPvEvalUserCp
    }
    val studyOrigin = StudyOrigin(
        label = currentMoveLabelState.text + (navSuffixText?.let { " $it" } ?: ""),
        userCp = studyOriginUserCp,
    )

    return ReportNavInfo(
        navLabelAnnotated = navLabelAnnotated,
        studyOrigin = studyOrigin,
        studyOriginAbsolutePly = studyOriginAbsolutePly,
        showExtendIndicator = showExtendIndicator,
        canTriggerExtend = canTriggerExtend,
    )
}

/**
 * 現在手の表示ラベルと悪手フラグを返す。
 *
 * ラベルフォーマット: 「N手目 ▲同　銀成」（最大約12文字）。
 * 悪手の括弧書き「（悪手・勝率−N%）」は表示しない（同情報が下の悪手カードにあるため）。
 * 悪手かどうかは isBlunder フラグで返し、呼び出し側で文字色（朱）を切り替える。
 */
private data class CurrentMoveLabelState(val text: String, val isBlunder: Boolean)

private fun buildCurrentMoveLabel(
    mode: ViewerMode,
    plyIndex: Int,
    movesInMode: List<String>,
    prevSfen: String?,
    reports: List<BlunderRecord>,
    selectedBlunder: BlunderRecord?,
): CurrentMoveLabelState {
    if (plyIndex == 0) {
        // 固定文字列のみ表示する（動的サフィックスは付けない。タブ選択状態と現在手ラベルで
        // 起点は伝わる）
        return CurrentMoveLabelState(AppStrings.VIEWER_START_POSITION, isBlunder = false)
    }

    val moveUsi = movesInMode.getOrNull(plyIndex - 1)
        ?: return CurrentMoveLabelState(AppStrings.viewerPlyLabel(plyIndex), isBlunder = false)

    // 日本語表記（直前局面から）
    val notation = if (prevSfen != null) {
        runCatching {
            JapaneseNotation.format(moveUsi, ShogiBoard.fromSfen(prevSfen))
        }.getOrElse { moveUsi }
    } else {
        moveUsi
    }

    // 本譜での絶対手数
    val gamePly = when (mode) {
        ViewerMode.MAINLINE -> plyIndex
        ViewerMode.BEST_PV -> (selectedBlunder?.ply?.toInt() ?: 0) + plyIndex - 1
    }

    // 本譜モードの場合、この手が悪手かどうかをチェック
    val isBlunder = mode == ViewerMode.MAINLINE &&
        reports.any { it.ply.toInt() == plyIndex }

    return CurrentMoveLabelState(
        text = "${AppStrings.viewerPlyLabel(gamePly)} $notation",
        isBlunder = isBlunder,
    )
}
