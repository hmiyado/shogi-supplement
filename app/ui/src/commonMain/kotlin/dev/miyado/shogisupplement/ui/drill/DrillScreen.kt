package dev.miyado.shogisupplement.ui.drill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.TextStyleDataMove
import dev.miyado.shogisupplement.ui.theme.shogiColors
import dev.miyado.shogisupplement.blunder.DisplayWinProb
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.classify.BlunderCategoryLabels
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.common.formatFixed1
import kotlin.math.abs

// このファイルは DrillQuestionContent / DrillResultContent とその Preview 群のみを持つ。
// トップの DrillScreen(onBack, vm: DrillViewModel = viewModel()) Composable は androidApp 側
// にある。DrillViewModel は androidx.lifecycle.AndroidViewModel であり、
// androidx.lifecycle.viewmodel.compose.viewModel() も Android 専用APIで、MainActivity.kt の
// 呼び出しがデフォルト引数の vm に依存する構造上、この関数を :ui に置くことはできない。
// Loading / NoCandidates / Judging の各分岐UIもトップ Composable内で完結する小さなブロック
// のため、androidApp 側にある。

// ─── 出題画面 ────────────────────────────────────────────────────────────────

@Composable
fun DrillQuestionContent(
    state: DrillUiState.Question,
    onSquareTapped: (ShogiSquare) -> Unit,
    onHandPieceTapped: (PieceType) -> Unit,
    onPromoteDecision: (Boolean) -> Unit,
    onSurrender: () -> Unit,
) {
    // 成り選択ダイアログ
    if (state.showPromoteDialog) {
        AlertDialog(
            onDismissRequest = { onPromoteDecision(false) },
            title = { Text(AppStrings.DRILL_PROMOTE_TITLE) },
            confirmButton = {
                TextButton(onClick = { onPromoteDecision(true) }) { Text(AppStrings.DRILL_PROMOTE_YES) }
            },
            dismissButton = {
                TextButton(onClick = { onPromoteDecision(false) }) { Text(AppStrings.DRILL_PROMOTE_NO) }
            },
        )
    }

    // 盤+操作UIは固定し、下部のみスクロールさせる。レポートビューアと同じ基準
    // （Scaffold 内 BoxWithConstraints の maxHeight × 0.45）で盤の高さ制約を計算し、
    // 両画面の盤サイズを統一する。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
    Column(
        // 水平paddingをColumn全体から外している。盤はレポートと同じ全幅（=同じ駒サイズ）にし、
        // スクロール領域側にのみ水平paddingを適用する。
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── 固定エリア（持駒行 + 盤） ─────────────────────────────────────────

        // インタラクティブ盤面。レポートビューアと同等の高さ制約を適用して盤サイズを統一する。
        ShogiBoardView(
            sfen = state.sfenCurrent,
            selectedFrom = state.selectedFrom,
            selectedDropType = state.selectedDropType,
            legalDestinations = state.legalDestinations,
            onSquareTapped = onSquareTapped,
            onHandPieceTapped = onHandPieceTapped,
            flip = state.flip,
            modifier = Modifier.heightIn(max = screenHeight * 0.45f),
        )

        // ── スクロールエリア（出題情報・降参ボタン）──────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            // 周回情報
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = AppStrings.drillAttemptCount(state.attemptCount),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = AppStrings.drillTotalCount(state.totalCandidates),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onSurrender,
                modifier = Modifier.fillMaxWidth(0.6f),
            ) {
                Text(AppStrings.DRILL_GIVE_UP)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    } // BoxWithConstraints
}

// ─── 結果画面 ─────────────────────────────────────────────────────────────────

@Composable
fun DrillResultContent(
    result: DrillJudge.DrillResult,
    blunder: BlunderRecord,
    sfenBefore: String = blunder.sfenBefore,
    flip: Boolean = false,
    /** 形勢の表示単位（"cp" or "wp"）。 */
    evalDisplay: String = "cp",
    /** VRT用: 初期表示手数（ReportScreen の initialPlyIndex と同じ用途）。 */
    initialPlyIndex: Int = 0,
    /** VRT用: 初期表示タブ（0=あなたの手・1=最善。本番呼び出しでは常に未指定＝0）。 */
    initialActiveLineIdx: Int = 0,
    /** 読み筋オンデマンド延長の状態 Map（blunderId → PvExtState）。DrillViewModel.pvExtState を渡す。 */
    pvExtState: Map<Long, PvExtState> = emptyMap(),
    /** 読み筋延長コールバック（最善タブ末尾局面の SFEN を渡す）。DrillViewModel::extendBestPv 相当。 */
    onExtendBestPv: (sfenAtLineEnd: String) -> Unit = {},
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    // 盤固定 + 下部スクロール構造。レポートビューアと同じ基準
    // （BoxWithConstraints maxHeight × 0.45）で盤サイズを統一する。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight

    // ── KifuLineViewer 用ライン構築 ──────────────────────────────
    val yourMoves = remember(result, blunder) {
        when (result.reason) {
            DrillJudge.Reason.MATCH_BEST ->
                blunder.bestPv?.split(" ")?.filter { it.isNotBlank() }
                    ?: listOf(result.userMoveUsi)
            DrillJudge.Reason.MATCH_ACTUAL_BLUNDER -> {
                val punish = blunder.punishPv?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
                listOf(blunder.moveUsi) + punish
            }
            DrillJudge.Reason.ENGINE_EVAL -> {
                if (result.userMoveUsi == "[降参]") emptyList()
                else {
                    val pv = result.pv?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
                    listOf(result.userMoveUsi) + pv
                }
            }
        }
    }
    val bestMoves = remember(blunder) {
        blunder.bestPv?.split(" ")?.filter { it.isNotBlank() }
            ?: blunder.bestUsi?.let { listOf(it) }
            ?: emptyList()
    }
    val kifuLines = listOf(
        KifuLine(AppStrings.DRILL_VIEWER_TAB_YOUR, yourMoves),
        KifuLine(AppStrings.DRILL_VIEWER_TAB_BEST, bestMoves),
    )
    var activeLineIdx by remember { mutableIntStateOf(initialActiveLineIdx) }
    var plyIndex by remember { mutableIntStateOf(initialPlyIndex) }
    // ▶+で読み筋延長をトリガーした後、延長成功で自動的に1手進めるためのフラグ
    // （ReportScreen の pendingExtendAdvance と同じUX）。
    var pendingExtendAdvance by remember { mutableStateOf(false) }

    // ── ナビ行ラベル（「N手目 ▲notation」＋形勢サフィックスを1行に統合）────
    // レポートの「最善の変化」タブと同一規約の形勢データ（そのドリル問題の元
    // BlunderRecord.cpBefore / missedMateIn。手送り・タブ切替では値が変わらない固定
    // 表示のため remember キーに ply/line は含めない）を、既存ナビラベルの末尾に
    // 半角スペース＋全角括弧（AppStrings.evalSuffix）で連結する。
    // cpBefore→ユーザー視点の符号変換・詰み規約（|cp|>=29_000）は MainActivity.kt の
    // BEST_PV 形勢行実装と同一。「ユーザー視点」の判定は本来
    // game.userSide == "gote" だが、ドリルの flip は DrillViewModel.loadNextQuestion() で
    // `repository.getGameById(blunder.gameId)?.userSide == "gote"` として計算されており
    // 定義が同一なので、そのまま userIsGote として再利用する。
    // No-jitter原則（DESIGN.mdのLayout節を参照）: 別行は追加しない。既存ナビ行
    // （高さ40dp・maxLines=1・overflow=Clip）に内容を連結するだけなので、
    // cpBefore/missedMateIn の有無や手送りで行の高さは変わらない。
    val bestPvEvalLabel = remember(blunder, evalDisplay, flip) {
        val moverIsGote = blunder.side == "gote"
        val userIsGote = flip
        val cpBeforeValue = blunder.cpBefore?.toInt()
        if (cpBeforeValue != null) {
            val senteCp = if (moverIsGote) -cpBeforeValue else cpBeforeValue
            val userCp = if (userIsGote) -senteCp else senteCp
            // 詰み絡み（|cp| >= 29_000）は生数字ではなく悪手カードと同じ規約表示。
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
            val missedMateInValue = blunder.missedMateIn?.toInt()
            if (missedMateInValue != null) {
                PositionEvalDisplay.format(
                    scoreCp = null,
                    mateIn = if (moverIsGote) -missedMateInValue else missedMateInValue,
                    userIsGote = userIsGote,
                    evalDisplay = evalDisplay,
                )
            } else {
                null
            }
        }
    }
    val evalSuffixText = bestPvEvalLabel?.let { AppStrings.evalSuffix(it.text) }
    val evalSuffixSign = bestPvEvalLabel?.sign ?: 0

    // ナビ行の基底ラベル「N手目 ▲notation」（開始局面は VIEWER_START_POSITION）。
    // MainActivity.kt の buildCurrentMoveLabel / studyNavLabel と同じ組み立て方。
    val activeMoves = kifuLines.getOrNull(activeLineIdx)?.moves ?: emptyList()
    val navClampedPly = plyIndex.coerceIn(0, activeMoves.size)
    val navLabelBase = remember(sfenBefore, activeLineIdx, navClampedPly, kifuLines) {
        if (navClampedPly == 0) {
            AppStrings.VIEWER_START_POSITION
        } else {
            val prevBoard = runCatching { ShogiBoard.fromSfen(sfenBefore) }.getOrNull()
            if (prevBoard != null) {
                for (i in 0 until navClampedPly - 1) {
                    runCatching { prevBoard.push(ShogiMove.fromUsi(activeMoves[i])) }
                }
            }
            val notation = if (prevBoard != null) {
                runCatching { JapaneseNotation.format(activeMoves[navClampedPly - 1], prevBoard) }
                    .getOrElse { activeMoves[navClampedPly - 1] }
            } else {
                activeMoves[navClampedPly - 1]
            }
            "${AppStrings.viewerPlyLabel(navClampedPly)} $notation"
        }
    }

    // ▶延長: 「最善」タブ（インデックス1）のみ延長可能（ReportScreen の「最善の変化」タブと
    // 同じ位置づけ）。「あなたの手」タブ（インデックス0）は延長対象にしない。
    val extState = pvExtState[blunder.id] ?: PvExtState.Idle

    Column(
        // 盤は全幅にし、水平paddingは下の結果スクロール領域にのみ適用する。
        modifier = Modifier.fillMaxSize(),
    ) {
        // ── 固定エリア（KifuLineViewer: 盤 + タブ + ナビ）────────────────────
        KifuLineViewer(
            startSfen = sfenBefore,
            lines = kifuLines,
            activeLineIdx = activeLineIdx,
            plyIndex = plyIndex,
            flip = flip,
            boardMaxHeight = screenHeight * 0.45f,
            onLineSelected = { idx ->
                activeLineIdx = idx
                plyIndex = 0
            },
            onNavigate = { ply ->
                val maxPly = kifuLines.getOrNull(activeLineIdx)?.moves?.size ?: 0
                plyIndex = ply.coerceIn(0, maxPly)
            },
            currentMoveLabel = navLabelBase,
            evalSuffixText = evalSuffixText,
            evalSuffixSign = evalSuffixSign,
            extendableLineIdx = 1,
            extendState = extState,
            onExtendRequested = { sfenAtLineEnd ->
                pendingExtendAdvance = true
                onExtendBestPv(sfenAtLineEnd)
            },
        )

        // ▶+で延長トリガー後、延長成功（最善タブの手列が伸びる）で自動的に1手進める
        // （ReportScreen と同じUX）。
        LaunchedEffect(bestMoves.size) {
            if (pendingExtendAdvance) {
                plyIndex = navClampedPly + 1
                pendingExtendAdvance = false
            }
        }
        // 延長エラー時はフラグを下ろす（▶+での再試行を妨げないため）。
        LaunchedEffect(extState) {
            if (pendingExtendAdvance && extState is PvExtState.Error) {
                pendingExtendAdvance = false
            }
        }

        // ── スクロールエリア（結果バナー・解説・ナビゲーション）────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))

            // 正誤バナー（静かなスタイル: 背景は淡い面色、テキストに色を乗せる）
            val isCorrect = result.isCorrect
            val shogiColors = MaterialTheme.shogiColors
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isCorrect) shogiColors.primarySoft else shogiColors.lossSoft,
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isCorrect) AppStrings.DRILL_CORRECT else AppStrings.DRILL_INCORRECT,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isCorrect) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))

            // あなたの手（和式表記）
            val boardForNotation = runCatching { ShogiBoard.fromSfen(blunder.sfenBefore) }.getOrNull()
            val userMoveDisplay = if (boardForNotation != null && result.userMoveUsi != "[降参]") {
                runCatching { JapaneseNotation.format(result.userMoveUsi, boardForNotation) }
                    .getOrElse { result.userMoveUsi }
            } else {
                result.userMoveUsi
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    AppStrings.DRILL_YOUR_MOVE,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.shogiColors.ink3,
                )
                Text(
                    userMoveDisplay,
                    style = TextStyleDataMove,
                    color = if (isCorrect) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))

            // 最善手（和式表記）
            val bestMoveUsi = result.bestMoveUsi
            if (bestMoveUsi != null) {
                val bestDisplay = if (boardForNotation != null) {
                    runCatching { JapaneseNotation.format(bestMoveUsi, boardForNotation) }
                        .getOrElse { bestMoveUsi }
                } else {
                    bestMoveUsi
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        AppStrings.DRILL_BEST_MOVE,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.shogiColors.ink3,
                    )
                    Text(
                        bestDisplay,
                        style = TextStyleDataMove,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // 損失表示（cp モード or 勝率モード）
            run {
                val cpBefore = blunder.cpBefore
                val cpAfter = blunder.cpAfter
                val lossText: String? = when {
                    evalDisplay == "cp" && cpBefore != null && cpAfter != null -> {
                        val isMate = abs(cpBefore) >= 29_000 || abs(cpAfter) >= 29_000
                        if (isMate) {
                            AppStrings.BLUNDER_LOSS_MATE
                        } else {
                            // blunderLossCp は無条件で「−」を前置する契約（DESIGN.md の
                            // 符号規約）なので、渡す値は損失の絶対値でなければならない。
                            // (cpBefore + cpAfter) は理論上まれに負（技術的な改善方向）に
                            // 振れることがあり、abs() を外すと「−」の後に符号付き負数が続き
                            // 「−-150」（二重マイナス）になる。
                            val cpLoss = abs((cpBefore + cpAfter).toInt())
                            AppStrings.blunderLossCp(cpLoss)
                        }
                    }
                    evalDisplay == "wp" && cpBefore != null && cpAfter != null -> {
                        val displayLoss = DisplayWinProb.lossWp(cpBefore.toInt(), cpAfter.toInt())
                        "−${formatFixed1(displayLoss * 100)}%"
                    }
                    !result.lossWp.isNaN() && result.lossWp > 0.0 -> {
                        // 旧レコード（cp未保存）: 保存済み loss_wp にフォールバック
                        val pct = formatFixed1(result.lossWp * 100)
                        AppStrings.drillLossPct(pct)
                    }
                    else -> null
                }
                if (lossText != null) {
                    Text(
                        lossText,
                        style = dev.miyado.shogisupplement.ui.theme.TextStyleData,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 不正解時: 実戦の悪手情報
            if (!isCorrect) {
                val actualMoveDisplay = if (boardForNotation != null) {
                    runCatching { JapaneseNotation.format(blunder.moveUsi, boardForNotation) }
                        .getOrElse { blunder.moveUsi }
                } else {
                    blunder.moveUsi
                }
                Text(AppStrings.drillActualMove(actualMoveDisplay), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                val categoryLabel = BlunderCategoryLabels.of(blunder.category)
                Text(AppStrings.drillCategory(categoryLabel.label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(AppStrings.drillNote(blunder.note), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))

            // ナビゲーションボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(AppStrings.DRILL_GO_HOME)
                }
                Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text(AppStrings.DRILL_NEXT)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    } // BoxWithConstraints
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewDrillQuestion() {
    ShogiTheme {
        Surface {
            DrillQuestionContent(
                state = DrillUiState.Question(
                    blunder = sampleBlunderRecord(),
                    sfenCurrent = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
                ),
                onSquareTapped = {},
                onHandPieceTapped = {},
                onPromoteDecision = {},
                onSurrender = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewDrillResultCorrect() {
    ShogiTheme {
        Surface {
            DrillResultContent(
                result = DrillJudge.DrillResult(
                    isCorrect = true,
                    lossWp = 0.0,
                    userMoveUsi = "2f6f",
                    bestMoveUsi = "2f6f",
                    reason = DrillJudge.Reason.MATCH_BEST,
                ),
                blunder = sampleBlunderRecord(),
                sfenBefore = sampleBlunderRecord().sfenBefore,
                onNext = {},
                onBack = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewDrillResultIncorrect() {
    ShogiTheme {
        Surface {
            DrillResultContent(
                result = DrillJudge.DrillResult(
                    isCorrect = false,
                    lossWp = 0.225,
                    userMoveUsi = "B*3d",
                    bestMoveUsi = "2f6f",
                    reason = DrillJudge.Reason.MATCH_ACTUAL_BLUNDER,
                ),
                blunder = sampleBlunderRecord(),
                sfenBefore = sampleBlunderRecord().sfenBefore,
                onNext = {},
                onBack = {},
            )
        }
    }
}

private fun sampleBlunderRecord() = BlunderRecord(
    id = 1L,
    gameId = 1L,
    ply = 41L,
    side = "sente",
    moveUsi = "B*3d",
    bestUsi = "2f6f",
    lossWp = 0.225,
    sfenBefore = "ln2g3l/2ks1s3/1pppppnr1/p7p/5gpp1/P1P4RP/1PSPPSP2/1KGG5/LN5NL b BPbp 41",
    category = "駒損（タクティクス）",
    diffMaterial = -11L,
    punishChecks = 0L,
    tookMovedPiece = false,
    missedMateIn = null,
    verdict = "○ 出題対象",
    note = "自帯6.3件/1000手 (上帯5.2件)。帯として典型的なミス",
    problemType = "手筋 (両取り・素抜き) の問題",
    priority = 2.9978349024480666,
)
