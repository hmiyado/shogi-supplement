package dev.miyado.shogisupplement.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miyado.shogisupplement.blunder.DisplayWinProb
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.classify.BlunderCategoryLabels
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.notation.JapaneseNotation
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.common.NavIcons
import dev.miyado.shogisupplement.ui.common.PvExtState
import dev.miyado.shogisupplement.ui.common.ReportBackHandler
import dev.miyado.shogisupplement.ui.common.SfenPosition
import dev.miyado.shogisupplement.ui.common.ShogiBoardView
import dev.miyado.shogisupplement.ui.common.formatDateTime
import dev.miyado.shogisupplement.ui.common.formatFixed1
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import dev.miyado.shogisupplement.ui.theme.TextStyleData
import dev.miyado.shogisupplement.ui.theme.TextStyleDataMove
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlin.math.abs
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
    onBack: () -> Unit,
    /** 読み筋延長の状態 Map（blunderId → PvExtState）。 */
    pvExtState: Map<Long, PvExtState> = emptyMap(),
    /** 読み筋延長コールバック（blunderId, sfenAtLineEnd, currentPvStr）。 */
    onExtendBestPv: (blunderId: Long, sfenAtLineEnd: String, currentPvStr: String?) -> Unit = { _, _, _ -> },
    /** 検討モード状態（null = 検討していない）。VRTでは表示状態を直接注入できる。 */
    studyState: StudyState? = null,
    /** 検討モード開始（盤上の駒タップ時に呼ぶ）。タップしたマスも渡す（即選択用）。 */
    onStartStudy: (
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        tappedSquare: ShogiSquare,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
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
    /** 検討モード終了（呼び出し側でエンジンquit・状態破棄）。 */
    onStudyEnd: () -> Unit = {},
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
    var showMoveList by remember { mutableStateOf(false) }
    // ▶で読み筋延長をトリガーした後、延長成功で自動的に1手進めるためのフラグ。
    var pendingExtendAdvance by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val selectedBlunder = selectedIdx?.let { reports.getOrNull(it) }

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

    // 棋譜リストシートの最大高さ計算と盤の最大高さ計算は、画面全体を包む単一の
    // BoxWithConstraints から得る screenHeight を共有する（Android専用APIを使わない）。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight

        // 棋譜リスト ModalBottomSheet。
        // 最大高さを画面の約55%に制限する。シート内コンテンツを heightIn(max) で制限し、
        // ドラッグしてもコンテンツ高以上には展開されない（=全画面化を抑止）。
        // 56.dp はドラッグハンドル+上下余白の概算（シート全体で約55%に収めるための控除）。
        if (showMoveList) {
            val sheetMaxHeight = screenHeight * 0.55f - 56.dp
            ModalBottomSheet(onDismissRequest = { showMoveList = false }) {
                Box(Modifier.heightIn(max = sheetMaxHeight)) {
                    MoveListSheet(
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
                }
            }
        }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.BACK,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    // 棋戦（source_place）をタイトルに優先。無ければファイル名。
                    // 見出し専用書体（DESIGN.md Typography節）。
                    Text(
                        text = game.sourcePlace ?: game.fileName,
                        style = TextStyle(
                            fontFamily = ShipporiMinchoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp),
                    )
                    // 対局者名（playersLine）はここには表示しない（対局情報ダイアログに
                    // 同じ情報があるため）。空いた幅はタイトル（棋戦名/ファイル名）の
                    // weight(1f) に還元される。
                    // 対局情報ダイアログ（ファイル名・先手/後手名）。KIFコピーアイコンの左。
                    IconButton(onClick = { showGameInfoDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = AppStrings.GAME_INFO_ICON_DESC,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    val kifText = game.kifText
                    if (kifText != null) {
                        IconButton(
                            onClick = {
                                onCopyKif(kifText)
                                scope.launch {
                                    snackbarHostState.showSnackbar(AppStrings.KIF_COPIED_MESSAGE)
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = AppStrings.KIF_COPY_ICON_DESC,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // ── 固定エリア（盤 + 操作列） ──────────────────────────────────

                // 検討開始局面の絶対手数（MAINLINE=現ply、BEST_PV=blunder.ply+現ply-1）。
                // buildCurrentMoveLabel の gamePly と同じ式。
                val studyOriginAbsolutePly = when (viewerMode) {
                    ViewerMode.MAINLINE -> clampedPly
                    ViewerMode.BEST_PV -> (selectedBlunder?.ply?.toInt() ?: 0) + clampedPly - 1
                }
                val shogiColors = MaterialTheme.shogiColors

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
                ShogiBoardView(
                    sfen = studyCurrentSfen ?: currentSfen,
                    flip = flip,
                    lastMoveDest = if (studyState != null) studyLastMoveDest else lastMoveDest,
                    selectedFrom = studyState?.selectedFrom,
                    selectedDropType = studyState?.selectedDropType,
                    legalDestinations = studyState?.legalDestinations ?: emptySet(),
                    onSquareTapped = { sq ->
                        if (studyState != null) {
                            onStudySquareTapped(sq)
                        } else {
                            val piece = SfenPosition.parse(currentSfen).boardPieces[sq.file to sq.rank]
                            if (piece != null) {
                                // 開始タップのマスを渡し、手番側の駒なら開始と同時に選択する。
                                onStartStudy(
                                    currentSfen,
                                    flip,
                                    viewerMode == ViewerMode.BEST_PV,
                                    clampedPly,
                                    selectedIdx,
                                    studyOriginAbsolutePly,
                                    sq,
                                )
                            } else {
                                // 駒のないマス: 列位置（flip考慮）で左右半分を近似。
                                val files = if (flip) (1..9).toList() else (9 downTo 1).toList()
                                val visualColIndex = files.indexOf(sq.file)
                                if (visualColIndex <= 4) {
                                    if (clampedPly > 0) plyIndex = clampedPly - 1
                                } else {
                                    if (clampedPly < maxPly) plyIndex = clampedPly + 1
                                }
                            }
                        }
                    },
                    onHandPieceTapped = { pt -> if (studyState != null) onStudyHandPieceTapped(pt) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * 0.45f),
                )

                // 検討モードの成り選択ダイアログ（ドリルと同じUX）。
                if (studyState?.showPromoteDialog == true) {
                    AlertDialog(
                        onDismissRequest = { onStudyPromoteDecision(false) },
                        title = { Text(AppStrings.DRILL_PROMOTE_TITLE) },
                        confirmButton = {
                            TextButton(onClick = { onStudyPromoteDecision(true) }) { Text(AppStrings.DRILL_PROMOTE_YES) }
                        },
                        dismissButton = {
                            TextButton(onClick = { onStudyPromoteDecision(false) }) { Text(AppStrings.DRILL_PROMOTE_NO) }
                        },
                    )
                }

                if (studyState != null) {
                    // 現在の検討局面の手番（バナーの手番表示・手番ヒント文言に使う。着手ごとに更新）。
                    val studySenteToMove = remember(studyCurrentSfen) {
                        SfenPosition.parse(studyCurrentSfen ?: currentSfen).isBlackTurn
                    }

                    // ── 検討中バナー（タブ行の代わり。タブ切替は不可） ─────────
                    // タブ行（padding h8/v2 + 36dp ボタン = トータル40dp）と高さを完全一致させ、
                    // 検討モード切替時に罫線・カード一覧のY座標が動かないようにする。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .height(36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = AppStrings.studyBanner(studyState.originAbsolutePly, studySenteToMove),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        TextButton(
                            onClick = exitStudy,
                            modifier = Modifier.height(36.dp),
                        ) { Text(AppStrings.STUDY_END) }
                    }

                    // ── 検討ナビ行（|◀=検討開始局面へ／◀=1手戻し／▶・▶|=無効） ──
                    // 計器行（検討評価行・手番ヒント行）は持たず、ナビラベルに
                    // 形勢サフィックスとして統合する（No-jitter・DESIGN.md Layout節）。
                    // 「検討1手目 △４二金寄（−636）」— 解析中は「（…）」、完了で数値、
                    // エラーは「（—）」。手番ヒント（手番でない駒をタップ）はエラーではないので
                    // 数値と同じ枠に ink2 で「（▲番です）」のように割り込む。
                    val studyNavLabel = remember(studyState.baseSfen, studyState.moves) {
                        if (studyState.moves.isEmpty()) {
                            AppStrings.STUDY_START_POSITION
                        } else {
                            val n = studyState.moves.size
                            val prevBoard = runCatching { ShogiBoard.fromSfen(studyState.baseSfen) }.getOrNull()
                            if (prevBoard != null) {
                                for (i in 0 until n - 1) {
                                    runCatching { prevBoard.push(ShogiMove.fromUsi(studyState.moves[i])) }
                                }
                            }
                            val notation = if (prevBoard != null) {
                                runCatching { JapaneseNotation.format(studyState.moves.last(), prevBoard) }
                                    .getOrElse { studyState.moves.last() }
                            } else {
                                studyState.moves.last()
                            }
                            "${AppStrings.studyPlyLabel(n)} $notation"
                        }
                    }
                    val studySuffixText: String?
                    val studySuffixColor: Color
                    if (studyState.showTurnHint) {
                        studySuffixText = AppStrings.evalSuffix(AppStrings.studyTurnHint(studySenteToMove))
                        studySuffixColor = shogiColors.ink2
                    } else {
                        when (val es = studyState.evalState) {
                            StudyEvalState.Loading -> {
                                studySuffixText = AppStrings.evalSuffix(AppStrings.EVAL_LOADING)
                                studySuffixColor = shogiColors.ink2
                            }
                            StudyEvalState.Error -> {
                                studySuffixText = AppStrings.evalSuffix(AppStrings.EVAL_UNAVAILABLE)
                                studySuffixColor = shogiColors.ink2
                            }
                            is StudyEvalState.Value -> {
                                studySuffixText = AppStrings.evalSuffix(es.label.text)
                                studySuffixColor = when {
                                    es.label.sign > 0 -> MaterialTheme.colorScheme.primary
                                    es.label.sign < 0 -> shogiColors.loss
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            }
                            StudyEvalState.None -> {
                                studySuffixText = null
                                studySuffixColor = Color.Unspecified
                            }
                        }
                    }
                    val studyNavLabelAnnotated = buildAnnotatedString {
                        append(studyNavLabel)
                        if (studySuffixText != null) {
                            append(" ")
                            withStyle(SpanStyle(fontFamily = IbmPlexMonoFamily, color = studySuffixColor)) {
                                append(studySuffixText)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // ボタン実効幅を48dp→36dpに圧縮し、中央ラベルの幅を拡幅する
                        // （手数表示の見切れ対策）。
                        TextButton(
                            onClick = onStudyResetToStart,
                            enabled = studyState.moves.isNotEmpty(),
                            modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) { Icon(NavIcons.FirstPage, contentDescription = "検討開始局面へ") }
                        TextButton(
                            onClick = onStudyStepBack,
                            enabled = studyState.moves.isNotEmpty(),
                            modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "1手戻る") }
                        Text(
                            text = studyNavLabelAnnotated,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
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
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) { Icon(NavIcons.LastPage, contentDescription = "最後へ") }
                    }
                } else {

                // タブ：本譜｜最善の変化
                val hasBestPv = selectedBlunder?.bestPv != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ReportViewerTab(
                        label = AppStrings.TAB_MAINLINE,
                        isActive = viewerMode == ViewerMode.MAINLINE,
                        enabled = true,
                        modifier = Modifier.weight(1f).height(36.dp),
                    ) {
                        viewerMode = ViewerMode.MAINLINE
                        plyIndex = clampedPly.coerceAtMost(game.movesUsi.size)
                    }
                    ReportViewerTab(
                        label = AppStrings.TAB_BEST_PV,
                        isActive = viewerMode == ViewerMode.BEST_PV,
                        enabled = hasBestPv,
                        modifier = Modifier.weight(1f).height(36.dp),
                    ) {
                        viewerMode = ViewerMode.BEST_PV
                        plyIndex = 0
                    }
                }

                // ナビゲーション + 現在手表示（1行統合: |◀ ◀ 現在手（形勢） ▶/▶+ ▶|）
                // ラベルは「N手目 ▲同　銀成」のみ（最大12文字設計）。
                //          悪手の手は文字色を loss（朱）で示す。
                // 計器行（評価値行・「この変化の形勢」行・▶ヒント行・スピナー/エラー行）は
                // 持たず、形勢をナビラベルの末尾に「（+120）」のようなサフィックスとして統合する
                // （No-jitter・DESIGN.md Layout節）。罫線の上はこのナビ行が最後の行になる。
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
                // score_cp は先手視点保存なのでユーザーが後手なら符号反転（PositionEvalDisplay 内）。
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

                // 最善の変化タブの形勢サフィックス（分岐点の cp_before を全plyで固定表示）。
                // cp_before は「手番側（悪手を指した側）視点」（BlunderJudge.toCp / BlunderReport
                // 準拠。dev.miyado.shogisupplement.pipeline.BlunderReport:18-19、
                // dev.miyado.shogisupplement.pipeline.ReportPipeline:171）なので、
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
                        // 閾値 29_000 は BlunderCard の詰み判定と同じリテラル（詰み見逃し系の
                        // cp_before は BlunderJudge.toCp で ±(30000-|n|) にエンコードされるため）。
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

                // ▶延長の可視化: BEST_PV タブでライン末尾に到達しているとき、
                // ▶ボタンを「▶+」（primary色）にして延長トリガーであることを示す。
                // Loading中も「▶+」のまま無効化。エラー時はナビラベルに「（—）」を出し、
                // 「▶+」は有効なまま（再試行可）。
                val extState = selectedBlunder?.let { pvExtState[it.id] }
                val showExtendIndicator = viewerMode == ViewerMode.BEST_PV &&
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
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ボタン実効幅を48dp→36dpに圧縮し、中央ラベルの幅を拡幅する
                    // （手数表示の見切れ対策）。
                    TextButton(
                        onClick = { plyIndex = 0 },
                        enabled = clampedPly > 0,
                        modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) { Icon(NavIcons.FirstPage, contentDescription = "最初へ") }
                    TextButton(
                        onClick = { plyIndex = (clampedPly - 1).coerceAtLeast(0) },
                        enabled = clampedPly > 0,
                        modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "1手戻る") }
                    Text(
                        text = navLabelAnnotated,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = { showMoveList = true }),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                    TextButton(
                        onClick = {
                            if (clampedPly < maxPly) {
                                plyIndex = clampedPly + 1
                            } else if (canTriggerExtend) {
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
                        enabled = clampedPly < maxPly || canTriggerExtend,
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
                        onClick = { plyIndex = maxPly },
                        enabled = clampedPly < maxPly,
                        modifier = Modifier.height(36.dp).widthIn(min = 36.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) { Icon(NavIcons.LastPage, contentDescription = "最後へ") }
                }

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

                } // else (studyState == null)

                HorizontalDivider(color = MaterialTheme.shogiColors.line)

                // ── スクロールエリア（悪手カード一覧） ──────────────────────────

                // この一局の指し手の強さ（caption・Mono 数値、悪手カードリスト先頭）
                if (strengthDisplayText != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            AppStrings.GAME_STRENGTH_PREFIX,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.shogiColors.ink2,
                        )
                        Text(
                            strengthDisplayText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = IbmPlexMonoFamily,
                            ),
                            color = MaterialTheme.shogiColors.ink2,
                        )
                    }
                }

                if (reports.isEmpty()) {
                    // 悪手ゼロ時のメッセージ: 勝敗・理由に応じて分岐（item 10）
                    val noBlundersMessage = when {
                        game.userSide != null && game.gameWinner != null ->
                            if (game.gameWinner == game.userSide) {
                                AppStrings.NO_BLUNDERS_WIN
                            } else {
                                AppStrings.noBlundersLoss(game.endReason ?: "負け")
                            }
                        else -> AppStrings.NO_BLUNDERS_UNKNOWN
                    }
                    Text(
                        noBlundersMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        itemsIndexed(reports) { idx, report ->
                            BlunderCard(
                                report = report,
                                isSelected = selectedIdx == idx,
                                evalDisplay = evalDisplay,
                                onClick = {
                                    // 検討中はタブ/局面の切替不可（「終了」してから選び直す）。
                                    if (studyState == null) {
                                        selectedIdx = idx
                                        viewerMode = ViewerMode.MAINLINE
                                        // 悪手直前の局面へジャンプ
                                        plyIndex = (report.ply - 1).toInt()
                                    }
                                },
                            )
                        }
                    }
                }
            }  // Column
        }  // Scaffold content lambda

        // 対局情報ダイアログ（ファイル名／取込元・日時・先手/後手名。閉じるボタンのみ）。
        if (showGameInfoDialog) {
            AlertDialog(
                onDismissRequest = { showGameInfoDialog = false },
                title = { Text(AppStrings.GAME_INFO_DIALOG_TITLE) },
                text = {
                    Column {
                        // ファイル名（クリップボード取込は「クリップボード 2026-07-15 09:08」形式＝取込元を兼ねる）
                        Text(game.fileName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        // 解析日時（GameCard と同じ formatDateTime(analyzedAt)を使う）
                        Text(
                            formatDateTime(game.analyzedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.shogiColors.ink2,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(playersLine, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGameInfoDialog = false }) {
                        Text(AppStrings.GAME_INFO_CLOSE)
                    }
                },
            )
        }
    }  // BoxWithConstraints
}  // ReportScreen

/**
 * 非負の Double を小数点以下0桁で四捨五入して文字列化する（BlunderCard の勝率%表示用）。
 *
 * "%.0f".format(x)（java.lang.String.format）は Kotlin/Native commonMain では使えないため、
 * multiplatform-safe な実装にしている。呼び出し元（勝率パーセント）は常に非負のため
 * 符号は扱わない。
 */
private fun formatFixed0(value: Double): String = kotlin.math.round(value).toLong().toString()

/** 指定ステップ数だけ進んだ局面の SFEN を返す。 */
private fun computeSfenAtStep(startSfen: String?, moves: List<String>, steps: Int): String {
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

/**
 * 棋譜リストシート（ModalBottomSheet 内コンテンツ）。
 * 本譜の全指し手を和式表記で縦に並べ、現在手をハイライトする。
 * 行タップで局面へ遷移してシートを閉じる。
 *
 * 各行末尾に position_eval から評価値/勝率を表示する（PositionEvalDisplay 再利用）。
 */
@Composable
fun MoveListSheet(
    moves: List<String>,
    currentPly: Int,
    /** 全局面評価値（先手視点 cp・ply昇順）。空 = 評価値表示なし。 */
    positionEvals: List<PositionEvalRow> = emptyList(),
    /** 形勢の表示単位（"cp" or "wp"）。 */
    evalDisplay: String = "cp",
    /** ユーザーが後手なら true（PositionEvalDisplay の符号反転用）。 */
    userIsGote: Boolean = false,
    onSelectPly: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentPly) {
        if (currentPly > 0 && moves.isNotEmpty()) {
            listState.scrollToItem((currentPly - 1).coerceIn(0, moves.lastIndex))
        }
    }
    val shogiColors = MaterialTheme.shogiColors
    Column {
        Text(
            AppStrings.MOVE_LIST_TITLE,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            itemsIndexed(moves) { idx, usiStr ->
                val ply = idx + 1
                val isCurrentPly = ply == currentPly
                val prevSfen = computeSfenAtStep(null, moves, idx)
                val notation = runCatching {
                    JapaneseNotation.format(usiStr, ShogiBoard.fromSfen(prevSfen))
                }.getOrElse { usiStr }
                val bgColor = if (isCurrentPly) shogiColors.highlightSoft else Color.Transparent
                // 各手の評価値ラベル（その手を指した後の局面 = ply と同じ）
                val evalLabel = remember(ply, positionEvals, evalDisplay, userIsGote) {
                    positionEvals.firstOrNull { it.ply == ply }?.let { row ->
                        PositionEvalDisplay.format(
                            scoreCp = row.scoreCp,
                            mateIn = row.mateIn,
                            userIsGote = userIsGote,
                            evalDisplay = evalDisplay,
                            ply = ply,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .clickable { onSelectPly(ply) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${ply}手目",
                        style = MaterialTheme.typography.labelSmall,
                        color = shogiColors.ink3,
                        modifier = Modifier.width(48.dp),
                    )
                    Text(
                        notation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isCurrentPly) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (evalLabel != null) {
                        Text(
                            evalLabel.text,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = IbmPlexMonoFamily,
                            ),
                            color = when {
                                evalLabel.sign > 0 -> MaterialTheme.colorScheme.primary
                                evalLabel.sign < 0 -> shogiColors.loss
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                HorizontalDivider(color = shogiColors.line.copy(alpha = 0.5f))
            }
        }
    }
}

/** タブボタン（本譜 / 最善の変化）。 */
@Composable
private fun ReportViewerTab(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** 悪手カード（ミニ盤なし・テキスト情報のみ）。 */
@Composable
fun BlunderCard(
    report: BlunderRecord,
    isSelected: Boolean = false,
    /** 形勢の表示単位（"cp" or "wp"）。 */
    evalDisplay: String = "cp",
    onClick: () -> Unit = {},
) {
    val categoryLabel = BlunderCategoryLabels.of(report.category)
    val shogiColors = MaterialTheme.shogiColors

    // 指し手: 和式表記
    val moveDisplay = remember(report.sfenBefore, report.moveUsi) {
        runCatching {
            JapaneseNotation.format(report.moveUsi, ShogiBoard.fromSfen(report.sfenBefore))
        }.getOrElse { report.moveUsi }
    }
    // 最善手: 和式表記（getOrElse は明示的に usiStr を返すことで String? 型を確定）
    val bestDisplay: String? = remember(report.sfenBefore, report.bestUsi) {
        report.bestUsi?.let { usiStr ->
            runCatching {
                JapaneseNotation.format(usiStr, ShogiBoard.fromSfen(report.sfenBefore))
            }.getOrElse { usiStr }
        }
    }

    // 損失表示（"変化前 → 変化後（差分）"形式 or フォールバック）
    //
    // cp_before/cp_after の意味（cpBefore = 手番側視点、cpAfter = 次手番側視点）:
    //   変化前 (手番側 = 悪手を指した側) = cpBefore
    //   変化後 (手番側換算)              = -cpAfter  ← 符号反転で同一視点に揃える
    //   損失量                           = cpBefore + cpAfter（= 表示上の差分マイナス）
    //
    // 詰み判定: |cpBefore| >= 29_000 または |cpAfter| >= 29_000
    //   変化前 cpBefore >= 29_000 → "詰み"（手番側が詰ます側）
    //   変化後 cpAfter  >= 29_000 → "詰まされ"（手番側が詰まされる側）
    data class EvalDisplay(
        val beforeLabel: String?,  // null = フォールバック（差分のみ）
        val afterLabel: String?,
        val lossLabel: String,
    )
    val cpBefore = report.cpBefore?.toInt()
    val cpAfter = report.cpAfter?.toInt()
    val evalState = remember(evalDisplay, cpBefore, cpAfter, report.lossWp) {
        when {
            evalDisplay == "cp" && cpBefore != null && cpAfter != null -> {
                val isMate = abs(cpBefore) >= 29_000 || abs(cpAfter) >= 29_000
                val userAfterCp = -cpAfter
                EvalDisplay(
                    beforeLabel = when {
                        cpBefore >= 29_000 -> AppStrings.BLUNDER_LOSS_MATE
                        cpBefore <= -29_000 -> AppStrings.BLUNDER_AFTER_MATED
                        else -> AppStrings.cpSignedLabel(cpBefore)
                    },
                    afterLabel = when {
                        userAfterCp >= 29_000 -> AppStrings.BLUNDER_LOSS_MATE
                        userAfterCp <= -29_000 -> AppStrings.BLUNDER_AFTER_MATED
                        else -> AppStrings.cpSignedLabel(userAfterCp)
                    },
                    lossLabel = if (isMate) AppStrings.BLUNDER_LOSS_MATE
                                else AppStrings.blunderLossCp(cpBefore + cpAfter),
                )
            }
            evalDisplay == "wp" && cpBefore != null && cpAfter != null -> {
                val beforeWp = DisplayWinProb.winProb(cpBefore)
                val userAfterWp = DisplayWinProb.winProb(-cpAfter)
                val lossWp = DisplayWinProb.lossWp(cpBefore, cpAfter)
                EvalDisplay(
                    beforeLabel = "${formatFixed0(beforeWp * 100)}%",
                    afterLabel = "${formatFixed0(userAfterWp * 100)}%",
                    lossLabel = "−${formatFixed1(lossWp * 100)}%",
                )
            }
            else -> {
                // 旧レコード（cp未保存）: 保存済み loss_wp の差分のみ
                EvalDisplay(
                    beforeLabel = null,
                    afterLabel = null,
                    lossLabel = "−${kotlin.math.round(report.lossWp * 100).toInt()}%",
                )
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // n手目（Mincho）+ 判定チップ（primary-soft）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    AppStrings.blunderCardPly(report.ply),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 判定チップ: primary-soft
                Surface(
                    color = shogiColors.primarySoft,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = report.verdict,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // 形勢の変化表示（Mono・変化後と差分は朱色、変化前は中立）
                if (evalState.beforeLabel != null && evalState.afterLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = evalState.beforeLabel,
                            style = TextStyleData,
                            color = shogiColors.ink2,
                        )
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.bodySmall,
                            color = shogiColors.ink2,
                        )
                        Text(
                            text = "${evalState.afterLabel}（${evalState.lossLabel}）",
                            style = TextStyleData,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    // フォールバック: 差分のみ
                    Text(
                        text = evalState.lossLabel,
                        style = TextStyleData,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 実戦手（loss色Mono）/ 最善手（primary色Mono）
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    AppStrings.BLUNDER_CARD_ACTUAL,
                    style = MaterialTheme.typography.labelSmall,
                    color = shogiColors.ink3,
                )
                Text(
                    moveDisplay,
                    style = TextStyleDataMove,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (bestDisplay != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        AppStrings.BLUNDER_CARD_BEST,
                        style = MaterialTheme.typography.labelSmall,
                        color = shogiColors.ink3,
                    )
                    Text(
                        bestDisplay,
                        style = TextStyleDataMove,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 分類チップ（loss-soft）
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = shogiColors.lossSoft,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = categoryLabel.label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // note: line色の左罫線のみ（DESIGN.md。四辺枠にしない）
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(shogiColors.line),
                )
                Text(
                    report.note,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = shogiColors.ink2,
                )
            }
        }
    }
}
