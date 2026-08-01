package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.ReportBackHandler
import dev.miyado.shogisupplement.ui.common.SfenPosition
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlinx.coroutines.launch

/**
 * 棋譜ビューア型レポート画面。
 *
 * Android専用APIには依存せず、以下は expect/actual またはホイストで抽象化している:
 * - BackHandler（androidx.activity.compose） → ReportBackHandler（expect/actual。
 *   ReportPlatform.kt）。
 * - KIFコピー（ClipboardManager/Context） → onCopyKif コールバックへホイスト
 *   （クリップボード書き込みは呼び出し側=Android で実装。snackbar表示自体は共通コード側）。
 * - 棋譜リストシートの最大高さ計算は LocalConfiguration.screenHeightDp を使わず、
 *   画面全体を1つの BoxWithConstraints で包んで screenHeight を共有する形で行う
 *   （既存の盤高さ計算と同じ仕組み）。
 * - GameInfoDialog の解析日時表示（java.text.SimpleDateFormat） →
 *   formatDateTime（expect/actual。ReportPlatform.kt。GameCard 等 :ui 内の共通フォーマッタ）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    game: GameRecord,
    reports: List<BlunderRecord>,
    flip: Boolean = false,
    strengthDisplayText: String? = null,
    /** 形勢の表示単位（"cp" or "wp"）。 */
    evalDisplay: String = "cp",
    /** 全局面評価値（先手視点 cp・ply昇順）。空 = 評価値表示なし。 */
    positionEvals: List<PositionEvalRow> = emptyList(),
    /** エンジン一致率の値表示（例:「62%(31/50)」）。null = 非表示（データ不足時など）。 */
    matchRateDisplayText: String? = null,
    /** 悪手率の値表示（例:「12%(3/25)」）。一致率と同じ分母nを使う。null = 非表示。 */
    blunderRateDisplayText: String? = null,
    onBack: () -> Unit,
    /** 読み筋延長の状態 Map（blunderId → PvExtState）。 */
    pvExtState: Map<Long, PvExtState> = emptyMap(),
    /**
     * 読み筋延長UI導線（「▶+」表示・タップでの延長トリガー）を出すか。
     * false でも機能自体（onExtendBestPv・PvExtState）は変わらず、ライン末尾では通常の
     * 「1手進む」矢印（無効状態）になるだけ。iOSでは導線自体を非表示にする決定のため
     * false を渡す（MainViewController.kt参照）。Androidは既定値trueのまま変更なし。
     */
    pvExtensionEnabled: Boolean = true,
    /** 読み筋延長コールバック（blunderId, sfenAtLineEnd, currentPvStr）。 */
    onExtendBestPv: (blunderId: Long, sfenAtLineEnd: String, currentPvStr: String?) -> Unit = { _, _, _ -> },
    /** 検討モード状態（null = 検討していない）。VRTでは表示状態を直接注入できる。 */
    studyState: StudyState? = null,
    /**
     * 検討モード開始（盤上の駒タップまたは持ち駒タップ時に呼ぶ）。
     * タップしたマス／持ち駒種別のどちらか一方を渡す（即選択用・互いに排他）。
     * origin はナビ行が現在表示している内容（現在手の日本語表記＋形勢）から
     * 組み立てて渡す。
     */
    onStartStudy: (
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        origin: StudyOrigin,
        tappedSquare: ShogiSquare?,
        tappedHandPieceType: PieceType?,
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    /** 検討モードの盤上マスタップ。 */
    onStudySquareTapped: (ShogiSquare) -> Unit = {},
    /** 検討モードの持ち駒タップ。 */
    onStudyHandPieceTapped: (PieceType) -> Unit = {},
    /** 検討モードの成り選択決定。 */
    onStudyPromoteDecision: (Boolean) -> Unit = {},
    /** 検討の1手戻し。 */
    onStudyStepBack: () -> Unit = {},
    /** 検討開始局面へ戻す。 */
    onStudyResetToStart: () -> Unit = {},
    /** 検討モード終了（呼び出し側でエンジンquit・状態破棄）。検討木は破棄されない。 */
    onStudyEnd: () -> Unit = {},
    /** 検討パネルの手順チップタップ（現在ライン内のシーク）。 */
    onStudyChipTapped: (Int) -> Unit = {},
    /** 検討パネルの分岐（下向きチェブロン付き）チップタップ（兄弟変化ポップを開く）。 */
    onStudyBranchChipTapped: (Int) -> Unit = {},
    /** 検討パネルの兄弟変化ポップを閉じる。 */
    onStudyBranchPopupDismiss: () -> Unit = {},
    /** 検討パネルの兄弟変化ポップで別ラインを選ぶ。 */
    onStudyBranchOptionSelected: (depth: Int, moveUsi: String) -> Unit = { _, _ -> },
    /** 検討パネルの「解析」ボタン（現在局面のオンデマンド解析）。 */
    onStudyAnalyze: () -> Unit = {},
    /**
     * KIFコピー（トップバー⧉アイコン）のホイスト先。呼び出し側（Android）で
     * クリップボード書き込みを実装する。null でない kifText が渡される
     * （game.kifText != null のときのみアイコンが表示されるため）。
     * snackbar表示（コピー完了メッセージ）はこの共通コード側で行う。
     */
    onCopyKif: (String) -> Unit = {},
    /** VRT用: 初期選択の悪手インデックス（本番呼び出しでは常に未指定＝null）。 */
    initialSelectedIndex: Int? = null,
    /** VRT用: 初期表示を「最善の変化」タブにする（本番呼び出しでは常に未指定＝false）。 */
    initialViewerModeBestPv: Boolean = false,
    /** VRT用: 初期 plyIndex。ライン末尾状態を再現する場合に指定（本番呼び出しでは常に未指定＝0）。 */
    initialPlyIndex: Int = 0,
    /** VRT用: 対局情報ダイアログを開いた状態から始める（本番呼び出しでは常に未指定＝false）。 */
    initialShowGameInfoDialog: Boolean = false,
    /**
     * VRT用: 初期表示を悪手一覧モードにする（本番呼び出しでは常に未指定＝false）。
     * initialSelectedIndex を指定した場合は自動的に一覧モードになるため、選択なしで
     * 一覧モードだけを再現したい場合（「悪手一覧を見る」導線）にのみ指定する。
     */
    initialBodyModeList: Boolean = false,
    /**
     * 駒台配置（実機評価用デバッグトグル）。DEBUGビルドの設定画面から
     * 変更できる（本番リリースビルドでは常に TOP_BOTTOM）。
     */
) {
    // ── ビューア状態 ────────────────────────────────────────────────────────
    var viewerMode by remember {
        mutableStateOf(if (initialViewerModeBestPv) ViewerMode.BEST_PV else ViewerMode.MAINLINE)
    }
    var plyIndex by remember { mutableIntStateOf(initialPlyIndex) }
    var selectedIdx by remember { mutableStateOf(initialSelectedIndex) }
    // 悪手を選んだ（グラフの朱マーカータップ・カードタップ）か「悪手一覧を見る」を押すと
    // LIST に切り替わる。initialSelectedIndex 指定時は選択済み状態を再現するため自動的に LIST。
    var bodyMode by remember {
        mutableStateOf(
            if (initialBodyModeList || initialSelectedIndex != null) ReportBodyMode.LIST else ReportBodyMode.SUMMARY,
        )
    }
    var showMoveList by remember { mutableStateOf(false) }
    // ▶で読み筋延長をトリガーした後、延長成功で自動的に1手進めるためのフラグ。
    var pendingExtendAdvance by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selectedBlunder = selectedIdx?.let { reports.getOrNull(it) }
    // タブ（本譜/最善の変化。ReportBodyMode.LIST 側）・ナビ行どちらからも参照するため
    // トップレベルで算出する。
    val hasBestPv = selectedBlunder?.bestPv != null

    // 評価値グラフ用データ（本譜/最善の変化タブの切替とは独立。対局全体のplyで固定）。
    // 自分視点に統一する（game.userSide=="gote" のとき符号反転。他の表示と同じ規約）。
    val evalGraphPoints = remember(positionEvals, game.userSide) {
        buildEvalGraphPoints(positionEvals, userIsGote = game.userSide == "gote")
    }
    val blunderPlies = remember(reports) { reports.map { it.ply.toInt() }.toSet() }

    // ── 検討モードの終了処理（呼び出し側でエンジンquit・状態破棄した上で、
    //    元のタブ/plyIndex/選択悪手インデックスに完全復帰する）────────────────────
    val exitStudy: () -> Unit = exit@{
        val s = studyState ?: return@exit
        onStudyEnd()
        viewerMode = if (s.originIsBestPv) ViewerMode.BEST_PV else ViewerMode.MAINLINE
        plyIndex = s.originPlyIndex
        selectedIdx = s.originSelectedIdx
    }
    // 検討モード中はシステムバックも「終了」扱いにする（enabled=検討中のときだけ、
    // 外側（MainActivity側）の loadHome() より優先して消費される）。
    ReportBackHandler(enabled = studyState != null) { exitStudy() }

    // 現モードでの手列と開始 SFEN
    val (movesInMode, startSfen) = remember(viewerMode, selectedBlunder) {
        when (viewerMode) {
            ViewerMode.MAINLINE -> game.movesUsi to null
            ViewerMode.BEST_PV -> {
                val pv = selectedBlunder?.bestPv
                    ?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
                pv to selectedBlunder?.sfenBefore
            }
        }
    }

    val maxPly = movesInMode.size
    val clampedPly = plyIndex.coerceIn(0, maxPly)

    // 現局面の SFEN（plyIndex 手後）
    val currentSfen = remember(viewerMode, clampedPly, selectedBlunder) {
        computeSfenAtStep(startSfen, movesInMode, clampedPly)
    }

    // 直前局面の SFEN（現在手の日本語表記用）
    val prevSfen = remember(viewerMode, clampedPly, selectedBlunder) {
        if (clampedPly == 0) null
        else computeSfenAtStep(startSfen, movesInMode, clampedPly - 1)
    }

    // 最新手ハイライト: plyIndex > 0 のとき直前の指し手の到達マスを取得
    val lastMoveDest = remember(viewerMode, clampedPly, selectedBlunder) {
        if (clampedPly <= 0) null
        else {
            movesInMode.getOrNull(clampedPly - 1)?.let { usiStr ->
                runCatching {
                    val move = ShogiMove.fromUsi(usiStr)
                    move.to.file to move.to.rank
                }.getOrNull()
            }
        }
    }

    // 対局者名（▲先手 △後手 + 自分側に「（あなた）」）。トップバーの2行目と
    // 対局情報ダイアログの両方で使うため、ここで一度だけ計算する。
    val senteSuffix = if (game.userSide == "sente") AppStrings.PLAYER_YOU else ""
    val goteSuffix = if (game.userSide == "gote") AppStrings.PLAYER_YOU else ""
    val senteName = game.senteName ?: AppStrings.PLAYER_UNKNOWN
    val goteName = game.goteName ?: AppStrings.PLAYER_UNKNOWN
    val playersLine = "▲$senteName$senteSuffix　△$goteName$goteSuffix"
    // 対局情報ダイアログの表示状態。
    var showGameInfoDialog by remember { mutableStateOf(initialShowGameInfoDialog) }

    // ナビゲーション + 現在手表示・検討開始時に渡す分岐元情報（studyOrigin）をまとめて
    // 算出する。ここより下（盤面表示側）で onStartStudy を呼ぶ必要があるため、
    // BoxWithConstraints より前で計算しておく。
    val navInfo = rememberReportNavInfo(
        viewerMode = viewerMode,
        clampedPly = clampedPly,
        maxPly = maxPly,
        movesInMode = movesInMode,
        prevSfen = prevSfen,
        reports = reports,
        selectedBlunder = selectedBlunder,
        positionEvals = positionEvals,
        evalDisplay = evalDisplay,
        game = game,
        pvExtState = pvExtState,
        pvExtensionEnabled = pvExtensionEnabled,
    )

    // 棋譜リストシートの最大高さ計算と盤の最大高さ計算は、画面全体を包む単一の
    // BoxWithConstraints から得る screenHeight を共有する（Android専用APIを使わない）。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight

        // 棋譜リスト ModalBottomSheet。
        // 最大高さを画面の約55%に制限する。シート内コンテンツを heightIn(max) で制限し、
        // ドラッグしてもコンテンツ高以上には展開されない（=全画面化を抑止）。
        // 56.dp はドラッグハンドル+上下余白の概算（シート全体で約55%に収めるための控除）。
        MoveListBottomSheet(
            show = showMoveList,
            onDismiss = { showMoveList = false },
            sheetMaxHeight = screenHeight * 0.55f - 56.dp,
            moves = game.movesUsi,
            currentPly = plyIndex.coerceIn(0, game.movesUsi.size),
            positionEvals = positionEvals,
            evalDisplay = evalDisplay,
            userIsGote = game.userSide == "gote",
            onSelectPly = { ply ->
                viewerMode = ViewerMode.MAINLINE
                plyIndex = ply
                showMoveList = false
            },
        )

        // TopAppBar（64dp）は使わず、32dpのインライン情報行を使う。
        // Scaffold topBar は使わず、Column先頭の固定Rowとして実装する
        // （システムバーインセットは Scaffold の contentWindowInsets が topBar の有無に
        // 関わらず content の PaddingValues に反映するため、下の `.padding(padding)` で
        // 正しく処理される）。
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {

                // ── 案1b: トップバーのインライン情報行化（32dp）─────────────────────
                // ←戻る・棋戦名（Shippori Mincho・タイトル用途）・対局者（縮小・省略可）・
                // ⓘ対局情報・⧉KIFコピーを1行に収める。
                ReportTopBar(
                    title = AppStrings.sourcePlaceLabel(game.sourcePlace) ?: game.fileName,
                    onBack = onBack,
                    onInfoClick = { showGameInfoDialog = true },
                    kifText = game.kifText,
                    onCopyKifClick = {
                        val kifText = game.kifText
                        if (kifText != null) {
                            onCopyKif(kifText)
                            scope.launch {
                                snackbarHostState.showSnackbar(AppStrings.KIF_COPIED_MESSAGE)
                            }
                        }
                    },
                )

                // ── 固定エリア（盤 + 操作列） ──────────────────────────────────
                // studyOriginAbsolutePly・studyOrigin は onStartStudy 配線より前（BoxWithConstraints
                // の外）で計算済み（navInfo）。

                // 検討中の現局面 SFEN・直前手ハイライト。
                val studyCurrentSfen = remember(studyState) {
                    studyState?.let { computeSfenAtStep(it.baseSfen, it.moves, it.moves.size) }
                }
                val studyLastMoveDest = remember(studyState) {
                    val s = studyState ?: return@remember null
                    if (s.moves.isEmpty()) null
                    else s.moves.lastOrNull()?.let { usiStr ->
                        runCatching { ShogiMove.fromUsi(usiStr).to.file to ShogiMove.fromUsi(usiStr).to.rank }
                            .getOrNull()
                    }
                }

                // 盤（盤エリア ≤ 50%。悪手カード領域 ≥ 40% を確保するため 45% に設定）
                // 駒のないマス寄りの左右半分タップ=1手戻る/進む（現在のタブの系列内・端では何もしない）
                // 駒のあるマスをタップしたら検討モード開始（本譜/最善の変化タブどちらでも可）。
                ReportBoardArea(
                    studyState = studyState,
                    currentSfen = currentSfen,
                    studyCurrentSfen = studyCurrentSfen,
                    flip = flip,
                    lastMoveDest = lastMoveDest,
                    studyLastMoveDest = studyLastMoveDest,
                    clampedPly = clampedPly,
                    maxPly = maxPly,
                    viewerMode = viewerMode,
                    selectedIdx = selectedIdx,
                    studyOriginAbsolutePly = navInfo.studyOriginAbsolutePly,
                    studyOrigin = navInfo.studyOrigin,
                    onStartStudy = onStartStudy,
                    onStudySquareTapped = onStudySquareTapped,
                    onStudyHandPieceTapped = onStudyHandPieceTapped,
                    onPlyIndexChange = { plyIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * 0.45f),
                )

                // 検討モードの成り選択ダイアログ（ドリルと同じUX）。
                StudyPromoteDialog(
                    show = studyState?.showPromoteDialog == true,
                    onDecision = onStudyPromoteDecision,
                )

                // 検討中の手番（手番ヒント文言の判定にのみ使う）。
                val studySenteToMove = remember(studyCurrentSfen, currentSfen) {
                    SfenPosition.parse(studyCurrentSfen ?: currentSfen).isBlackTurn
                }

                // ── ナビ行（常に1行。検討中/非検討で中身だけ入れ替える） ──────────────
                // 検討モードの出入りで罫線から下のY座標が動かないようにする
                // （DESIGN.md No-jitter原則）。常に1行(40dp)だけを描画してその中身を
                // 入れ替える構成にすることで、罫線位置が構造的に一致し、
                // 高さの帳尻合わせが不要になる。
                ReportNavRow(
                    studyState = studyState,
                    studySenteToMove = studySenteToMove,
                    onStudyStepBack = onStudyStepBack,
                    onStudyExit = exitStudy,
                    navLabelAnnotated = navInfo.navLabelAnnotated,
                    onLabelClick = { showMoveList = true },
                    canGoFirst = clampedPly > 0,
                    onFirst = { plyIndex = 0 },
                    canGoPrev = clampedPly > 0,
                    onPrev = { plyIndex = (clampedPly - 1).coerceAtLeast(0) },
                    canGoNext = clampedPly < maxPly || navInfo.canTriggerExtend,
                    onNext = {
                        if (clampedPly < maxPly) {
                            plyIndex = clampedPly + 1
                        } else if (navInfo.canTriggerExtend) {
                            selectedBlunder?.let { blunder ->
                                val sfenAtEnd = computeSfenAtStep(
                                    blunder.sfenBefore,
                                    movesInMode,
                                    movesInMode.size,
                                )
                                pendingExtendAdvance = true
                                onExtendBestPv(blunder.id, sfenAtEnd, blunder.bestPv)
                            }
                        }
                    },
                    showExtendIndicator = navInfo.showExtendIndicator,
                    canTriggerExtend = navInfo.canTriggerExtend,
                    canGoLast = clampedPly < maxPly,
                    onLast = { plyIndex = maxPly },
                )

                if (studyState == null) {
                    // ▶で延長トリガー後、延長成功（maxPly 増加）で自動的に1手進める。
                    LaunchedEffect(maxPly) {
                        if (pendingExtendAdvance) {
                            plyIndex = clampedPly + 1
                            pendingExtendAdvance = false
                        }
                    }
                    // 延長エラー時はフラグを下ろす（▶+での再試行を妨げないため）。
                    LaunchedEffect(selectedBlunder?.let { pvExtState[it.id] }) {
                        if (pendingExtendAdvance &&
                            selectedBlunder != null &&
                            pvExtState[selectedBlunder.id] is PvExtState.Error
                        ) {
                            pendingExtendAdvance = false
                        }
                    }
                }

                // testTag: 検討モード切替時のNo-jitter検証（罫線Y座標が動かないこと）に使う。
                HorizontalDivider(
                    color = MaterialTheme.shogiColors.line,
                    modifier = Modifier.testTag("report_divider"),
                )

                // 悪手ゼロ時のメッセージ: 勝敗・理由に応じて分岐（SUMMARY・LIST両方の
                // 空表示で使うため一度だけ計算する）。
                val noBlundersMessage = when {
                    game.userSide != null && game.gameWinner != null ->
                        if (game.gameWinner == game.userSide) {
                            AppStrings.NO_BLUNDERS_WIN
                        } else {
                            AppStrings.noBlundersLoss(game.endReason ?: "負け")
                        }
                    else -> AppStrings.NO_BLUNDERS_UNKNOWN
                }

                // 悪手を選んで LIST へ切り替える共通処理（グラフの朱マーカータップ・
                // 悪手カードタップの両方から呼ぶ）。検討中は選択・切替不可
                // （「終了」してから選び直す。既存のカードタップ制約と同じ）。
                val selectBlunderAndShowList: (Int) -> Unit = { idx ->
                    if (studyState == null) {
                        selectedIdx = idx
                        viewerMode = ViewerMode.MAINLINE
                        plyIndex = (reports[idx].ply - 1).toInt()
                        bodyMode = ReportBodyMode.LIST
                    }
                }

                if (studyState != null) {
                    // ── 検討中: グラフ＋サマリー領域を丸ごと検討パネルに入れ替える ──
                    // Modifier.fillMaxSize() が非検討時の SUMMARY/LIST と同じ「罫線から下の
                    // 残り領域」を占めるため、パネルの外形（≒罫線のY座標）は個別の高さ計算
                    // ではなく同じ排他スロットを共有することで構造的に一致する。
                    StudyPanel(
                        studyState = studyState,
                        onChipTapped = onStudyChipTapped,
                        onBranchChipTapped = onStudyBranchChipTapped,
                        onBranchPopupDismiss = onStudyBranchPopupDismiss,
                        onBranchOptionSelected = onStudyBranchOptionSelected,
                        onAnalyze = onStudyAnalyze,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else {
                    when (bodyMode) {
                        ReportBodyMode.SUMMARY -> {
                            // ── サマリー（既定表示）: グラフ＋悪手率・一致率・棋力 ────────
                            // 固定表示（スクロール対象外）。スクロールするのは LIST 側
                            // （悪手一覧）だけにする（実機確認: サマリーがスクロール領域に
                            // 入っていると一覧との境界が分かりにくいとの指摘）。
                            ReportSummaryBody(
                                evalGraphPoints = evalGraphPoints,
                                maxPly = game.movesUsi.size,
                                blunderPlies = blunderPlies,
                                // SUMMARY 表示中は viewerMode が常に MAINLINE
                                // （タブは LIST 側にしか無いため）。plyIndex がそのまま
                                // 対局全体のplyに対応する。
                                currentPly = clampedPly,
                                onPlyTapped = { ply ->
                                    // タップ位置の手へ現在手を移動する（盤・ナビ共通。
                                    // 悪手マーカータップでも同様——マーカー＝タップ位置の
                                    // plyなので、下の選択処理では plyIndex を上書きしない）。
                                    // この分岐（else側）は studyState == null が保証されている
                                    // （検討中は検討パネルが丸ごと入れ替わっているため）。
                                    viewerMode = ViewerMode.MAINLINE
                                    plyIndex = ply
                                    // タップ位置が悪手のplyと一致すれば、その悪手を
                                    // 選んで一覧へ（マーカーの選択導線）。
                                    val idx = reports.indexOfFirst { it.ply.toInt() == ply }
                                    if (idx >= 0) {
                                        selectedIdx = idx
                                        bodyMode = ReportBodyMode.LIST
                                    }
                                },
                                // ドラッグ（スクラバー操作）は現在手を連続的に動かすだけで、
                                // タップと違い一覧への切替は発火させない（実機指示: マーカー上を
                                // ドラッグで通過・終了しても一覧に切り替わってほしくない）。
                                onPlyDragged = { ply ->
                                    viewerMode = ViewerMode.MAINLINE
                                    plyIndex = ply
                                },
                                reports = reports,
                                noBlundersMessage = noBlundersMessage,
                                strengthDisplayText = strengthDisplayText,
                                matchRateDisplayText = matchRateDisplayText,
                                blunderRateDisplayText = blunderRateDisplayText,
                                onViewList = { bodyMode = ReportBodyMode.LIST },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        ReportBodyMode.LIST -> {
                            // ── 悪手一覧: サマリーへ戻る導線＋本譜/最善の変化タブ＋カード一覧 ──
                            ReportBlunderListBody(
                                onBackToSummary = {
                                    bodyMode = ReportBodyMode.SUMMARY
                                    // グラフの現在手ラインと矛盾しないよう MAINLINE に戻す
                                    // （LIST 側でBEST_PVを見ていた場合の後始末）。
                                    viewerMode = ViewerMode.MAINLINE
                                },
                                viewerMode = viewerMode,
                                hasBestPv = hasBestPv,
                                onSelectMainlineTab = {
                                    viewerMode = ViewerMode.MAINLINE
                                    plyIndex = clampedPly.coerceAtMost(game.movesUsi.size)
                                },
                                onSelectBestPvTab = {
                                    viewerMode = ViewerMode.BEST_PV
                                    plyIndex = 0
                                },
                                reports = reports,
                                noBlundersMessage = noBlundersMessage,
                                selectedIdx = selectedIdx,
                                evalDisplay = evalDisplay,
                                onSelectBlunder = selectBlunderAndShowList,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                } // else (studyState == null)
            } // Column
        } // Scaffold content lambda

        // 対局情報ダイアログ（ファイル名／取込元・日時・先手/後手名。閉じるボタンのみ）。
        GameInfoDialog(
            show = showGameInfoDialog,
            onDismiss = { showGameInfoDialog = false },
            game = game,
            playersLine = playersLine,
        )
    } // BoxWithConstraints
} // ReportScreen

/**
 * 指定ステップ数だけ進んだ局面の SFEN を返す。
 *
 * 同ファイル内だけでなく別コンポーネントからも呼ぶため private ではなく internal にする。
 */
internal fun computeSfenAtStep(startSfen: String?, moves: List<String>, steps: Int): String {
    val board = if (startSfen != null) {
        runCatching { ShogiBoard.fromSfen(startSfen) }.getOrElse { ShogiBoard() }
    } else {
        ShogiBoard()
    }
    val limit = steps.coerceAtMost(moves.size)
    for (i in 0 until limit) {
        runCatching { board.push(ShogiMove.fromUsi(moves[i])) }.onFailure { break }
    }
    return board.toSfen()
}
