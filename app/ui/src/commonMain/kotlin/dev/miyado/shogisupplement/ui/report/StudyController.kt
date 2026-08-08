package dev.miyado.shogisupplement.ui.report

import dev.miyado.shogisupplement.blunder.BlunderJudge
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.board.Side
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.notation.JapaneseNotation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 検討モードのエンジン解析ノード数。仕様書の指定値。 */
private const val STUDY_ANALYSIS_NODES = 200_000

/**
 * ローカルエンジンが使える見込みが無い（[StudyEvalState.Preparing]）ときの再判定間隔。
 * 「使える見込みか」の実体判断はプラットフォーム側が注入する
 * [StudyController.localEngineLikelyAvailable] に委ねるため、ここでは秒単位の
 * 粗い間隔でポーリングするだけで足りる。
 */
private const val STUDY_LOCAL_ENGINE_POLL_INTERVAL_MS = 2_000L

/**
 * レポート画面の検討モードを担う状態・ロジック。
 *
 * DrillViewModel（judgeWithEngine 注入）・AccountViewModel と同じ「ホストがエンジン生成/
 * 評価値表示単位を注入する」パターンを使う:
 * - [engineFactory]: 検討評価が必要になったとき（着手・チップ移動での自動発火、または
 *   [StudyEvalState.Preparing]/[StudyEvalState.Error] 時の手動リトライ）呼ばれ、
 *   以後 [studyEngine] として生かしっぱなしにする。エンジンの起動/破棄ライフサイクルは
 *   呼び出し元＝ホストの責務。
 * - [evalDisplayProvider]: 形勢の表示単位（'cp'/'wp'）を都度取得する。
 * - [localEngineLikelyAvailable]: 自動発火してよいか（サーバーへ静かにフォールバックして
 *   クォータを消費しないか）の見込み判定。既定はネイティブエンジン常駐環境（Android・
 *   iOSエンジン入り版）向けの `{ true }`。iOS engineless 版はローカルWASM資産の準備状況を
 *   見る実判定をホストが注入する。
 *
 * 検討手順は分岐元（baseSfen）ごとに木構造で保持し、「検討終了（endStudy）」では
 * 破棄しない。レポート画面を開いている間は [treesByOrigin] に残り続け、同じ分岐元から
 * 検討を再開すると続きから辿れる（[dispose] で画面ごと破棄されるまで）。
 *
 * ReportViewModel が保持し、[dispose] を自身の onCleared 相当のタイミングで呼ぶ
 * （リーク厳禁: 検討エンジンが生きていれば quit する）。
 *
 * @param scope 状態更新の非同期処理に使うスコープ（呼び出し元の viewModelScope 相当を注入）
 * @param ioDispatcher DB/エンジン処理用ディスパッチャ（テスト時はUnconfinedを注入）
 */
class StudyController(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val engineFactory: () -> Engine,
    private val evalDisplayProvider: () -> String,
    private val localEngineLikelyAvailable: () -> Boolean = { true },
) {

    /** レポート画面の検討モード状態（null = 検討していない）。 */
    private val _studyState = MutableStateFlow<StudyState?>(null)
    val studyState: StateFlow<StudyState?> = _studyState.asStateFlow()

    /** 検討モード中に生かしっぱなしにするエンジン（初回解析で遅延生成、終了/disposeでquit）。 */
    private var studyEngine: Engine? = null

    /** 検討モードの現局面（合法手計算用）。baseSfen + moves を都度適用して保持する。 */
    private var studyBoard: ShogiBoard? = null

    /**
     * 分岐元（baseSfen）ごとの検討木。レポート画面を開いている間（[dispose] まで）保持する。
     *
     * Why not blunderId等で分岐元を識別しないか: 「検討」はどの手からでも開始できる
     * （悪手の手・そうでない手のどちらのタップからも開始可能）ため、局面そのもの（SFEN）を
     * キーにするのが最も自然。同じ SFEN に複数の分岐元（別tab・別選択）から辿り着くことは
     * 実務上ほぼ無い（対局中の同一局面はplyで一意）ため、キー衝突は問題にならない。
     */
    private val treesByOrigin = mutableMapOf<String, StudyTree>()

    /** 検討木のノードID採番（controller生存中で単調増加。木を跨いだ一意性は不要）。 */
    private var nextNodeIdCounter = 1L
    private fun nextNodeId(): Long = nextNodeIdCounter++

    /** 解析の実行中フラグ（自動発火・手動リトライ共有。多重発火防止で1回分だけ実行する）。 */
    private var studyEvalRunning = false

    /**
     * [StudyEvalState.Preparing] 中の再判定ループ（[localEngineLikelyAvailable] が
     * true に変わるのを待つ）。局面が変わるたび [maybeAutoAnalyze] が先頭でキャンセルする
     * ため、常に「直前に評価対象だった局面」向けの1本だけが生きている。
     */
    private var pollJob: Job? = null

    /**
     * 検討モードを開始する（レポートビューアで盤上の駒または持ち駒をタップしたときに呼ぶ）。
     *
     * エンジンはここでは生成しない（起動コストを避けるため、実際に「解析」ボタンを押した
     * タイミングで遅延生成する）。
     * 開始タップのマス（tappedSquare）の駒が手番側なら、開始と同時に選択状態にする。
     * 持ち駒タップから開始する場合は tappedHandPieceType を渡す（打ちの選択状態で開始する。
     * tappedSquare とは排他）。
     *
     * 同じ baseSfen で以前に検討木があれば（同じ画面内で「終了」→再開した場合）再利用する。
     * moves は常に空（木のルート）から始める——「終了」直前にどこまで潜っていたかに関わらず、
     * 再開時はいつも分岐元の局面そのものから見せるのが、動作として一番わかりやすいという判断
     * （木は保持するが、表示位置は毎回リセットする。深い場所へは表示中のチップ列で辿り直せる）。
     */
    fun startStudy(
        baseSfen: String,
        flip: Boolean,
        originIsBestPv: Boolean,
        originPlyIndex: Int,
        originSelectedIdx: Int?,
        originAbsolutePly: Int,
        origin: StudyOrigin,
        tappedSquare: ShogiSquare? = null,
        tappedHandPieceType: PieceType? = null,
    ) {
        val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull() ?: return
        studyBoard = board
        treesByOrigin.getOrPut(baseSfen) { StudyTree() }
        _studyState.value = buildInitialStudyState(
            baseSfen = baseSfen,
            flip = flip,
            originIsBestPv = originIsBestPv,
            originPlyIndex = originPlyIndex,
            originSelectedIdx = originSelectedIdx,
            originAbsolutePly = originAbsolutePly,
            origin = origin,
            tappedSquare = tappedSquare,
            board = board,
            tappedHandPieceType = tappedHandPieceType,
        )
    }

    /** 検討モードの盤上マスタップ処理（DrillViewModel.onSquareTapped と同型のロジック）。 */
    fun onStudySquareTapped(sq: ShogiSquare) {
        val s = _studyState.value ?: return
        val board = studyBoard ?: return

        val selectedFrom = s.selectedFrom
        when {
            selectedFrom != null -> {
                val legalToHere = board.legalMovesFrom(selectedFrom).filter { it.to == sq }
                when {
                    legalToHere.isEmpty() -> {
                        val piece = board.pieceAt(sq)
                        if (piece != null && piece.side == board.turn) {
                            val dests = board.legalMovesFrom(sq).map { it.to }.toSet()
                            _studyState.value = s.copy(
                                selectedFrom = sq,
                                selectedDropType = null,
                                legalDestinations = dests,
                                showPromoteDialog = false,
                                pendingPromoteMove = null,
                                showTurnHint = false,
                            )
                        } else {
                            _studyState.value = s.copy(
                                selectedFrom = null,
                                selectedDropType = null,
                                legalDestinations = emptySet(),
                                // 手番でない側の駒をタップしたときは手番ヒントを表示する。
                                showTurnHint = piece != null && piece.side != board.turn,
                            )
                        }
                    }
                    legalToHere.size == 1 -> executeStudyMove(legalToHere.first())
                    else -> {
                        val promote = legalToHere.firstOrNull { it.promote } ?: legalToHere.first()
                        _studyState.value = s.copy(showPromoteDialog = true, pendingPromoteMove = promote)
                    }
                }
            }

            s.selectedDropType != null -> {
                val dropMoves = board.legalMoves().filter {
                    it.dropType == s.selectedDropType && it.to == sq
                }
                if (dropMoves.isNotEmpty()) {
                    executeStudyMove(dropMoves.first())
                } else {
                    val piece = board.pieceAt(sq)
                    _studyState.value = s.copy(
                        selectedDropType = null,
                        legalDestinations = emptySet(),
                        // 手番でない側の駒をタップしたときは手番ヒントを表示する。
                        showTurnHint = piece != null && piece.side != board.turn,
                    )
                }
            }

            else -> {
                val piece = board.pieceAt(sq)
                when {
                    piece != null && piece.side == board.turn -> {
                        val dests = board.legalMovesFrom(sq).map { it.to }.toSet()
                        _studyState.value = s.copy(
                            selectedFrom = sq,
                            selectedDropType = null,
                            legalDestinations = dests,
                            showTurnHint = false,
                        )
                    }
                    piece != null -> {
                        // 手番でない側の駒をタップ→ナビ行に「▲番です/△番です」を一時表示。
                        // 次の正常タップで消える（各正常経路が showTurnHint=false に戻す）。
                        _studyState.value = s.copy(showTurnHint = true)
                    }
                    else -> {
                        // 空マスタップ: 表示中の手番ヒントがあれば消すだけ。
                        if (s.showTurnHint) _studyState.value = s.copy(showTurnHint = false)
                    }
                }
            }
        }
    }

    /** 検討モードの持ち駒タップ処理。 */
    fun onStudyHandPieceTapped(pieceType: PieceType) {
        val s = _studyState.value ?: return
        val board = studyBoard ?: return

        if (s.selectedDropType == pieceType) {
            _studyState.value = s.copy(
                selectedDropType = null,
                selectedFrom = null,
                legalDestinations = emptySet(),
                showTurnHint = false,
            )
        } else {
            val dropSquares = board.legalDropSquares(pieceType).toSet()
            _studyState.value = s.copy(
                selectedDropType = pieceType,
                selectedFrom = null,
                legalDestinations = dropSquares,
                showTurnHint = false,
            )
        }
    }

    /** 検討モードの成り選択ダイアログで「成る/成らない」を決定した。 */
    fun onStudyPromoteDecision(promote: Boolean) {
        val s = _studyState.value ?: return
        val pending = s.pendingPromoteMove ?: return
        val board = studyBoard ?: return

        val actualMove = board.legalMovesFrom(pending.from!!)
            .filter { it.to == pending.to }
            .firstOrNull { it.promote == promote }
            ?: pending.copy(promote = promote)

        _studyState.value = s.copy(showPromoteDialog = false, pendingPromoteMove = null)
        executeStudyMove(actualMove)
    }

    fun studyStepBack() {
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        navigateToDepth(s.moves.size - 1)
    }

    fun studyResetToStart() {
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        navigateToDepth(0)
    }

    /**
     * 検討パネルの手順チップタップ: displayLine 上の depth 手目の局面へ移動する
     * （depth = 0 は検討開始局面。studyStepBack/studyResetToStart はこの特殊形）。
     * moves より先（まだ進んでいない側）のチップも displayLine には残っているため、
     * このタップだけで前後どちらへも移動できる（実機確認: 「戻ると先が消える」対応）。
     * displayLine の範囲外は無視する。
     */
    fun onChipTapped(depth: Int) {
        val s = _studyState.value ?: return
        if (depth !in 0..s.displayLine.size) return
        navigateToDepth(depth)
    }

    /**
     * 分岐（下向きチェブロン付き）チップタップ: そのチップの depth に兄弟変化があれば、
     * 兄弟変化ポップの中身を用意して開く。displayLine 上のどのチップ（現在より
     * 先のチップも含む）からでも開ける。
     */
    fun onBranchChipTapped(depth: Int) {
        val s = _studyState.value ?: return
        if (depth !in s.displayLine.indices) return
        if (s.branchFlags.getOrNull(depth) != true) return
        val tree = treesByOrigin[s.baseSfen] ?: return
        val siblings = tree.siblingsAtDepth(s.displayLine, depth)
        val options = siblings.map { node ->
            StudyBranchOption(
                moveUsi = node.moveUsi,
                evalState = node.evalState,
                isCurrent = node.moveUsi == s.displayLine[depth],
            )
        }
        _studyState.value = s.copy(openBranchPopupDepth = depth, branchPopupOptions = options)
    }

    /** 兄弟変化ポップを閉じる（選ばずに閉じる場合）。 */
    fun onBranchPopupDismiss() {
        _studyState.update { it?.copy(openBranchPopupDepth = null, branchPopupOptions = emptyList()) }
    }

    /**
     * 兄弟変化ポップで別ラインを選ぶ。選んだ兄弟のノードへ切り替えるだけで、
     * その先（選んだ兄弟からさらに指し進めた手）へは自動で潜らない
     * （ポップに出ている評価はその1手だけのものであり、切替直後の表示局面と
     * ポップの情報を一致させるための単純化。深い変化を見たい場合は改めて
     * チップ列から辿る）。
     * 別ラインへの切替は displayLine を置き換える（＝ここから先は新しいラインの表示になる。
     * 旧ラインは木構造側に残ったまま）。
     */
    fun onBranchOptionSelected(depth: Int, moveUsi: String) {
        val s = _studyState.value ?: return
        if (depth !in s.displayLine.indices) return
        applyMoves(s, s.displayLine.take(depth) + moveUsi)
    }

    /** 検討モードを終了する（エンジンをquitし、盤面状態を破棄する）。検討木は破棄しない。 */
    fun endStudy() {
        pollJob?.cancel()
        pollJob = null
        studyEngine?.quit()
        studyEngine = null
        studyBoard = null
        studyEvalRunning = false
        _studyState.value = null
    }

    /**
     * リーク厳禁: 呼び出し元の onCleared 相当のタイミング、または
     * レポート画面を離れるタイミングで呼ぶこと。検討木もここで初めて破棄する
     * （画面を開いている間は保持するため。endStudy では破棄しない）。
     */
    fun dispose() {
        pollJob?.cancel()
        pollJob = null
        studyEngine?.quit()
        studyEngine = null
        treesByOrigin.clear()
    }

    /**
     * 現在局面の解析を明示的にリトライする（[StudyEvalState.Preparing]／[StudyEvalState.Error]
     * 時にのみ表示される「解析」ボタン）。moves が空（分岐元そのもの）のときは何もしない
     * ——分岐元の形勢はパネル冒頭に既に表示されているため、同じ局面を改めて解析する意味がない。
     *
     * [localEngineLikelyAvailable] の判定を経ずに常に [startAnalysis] を呼ぶ
     * （明示タップは見込み判定を待たせない。ローカル不可なら engineFactory 側の
     * フォールバック合成——iOS engineless の FailoverEngine——がサーバーへ切り替える）。
     * Preparing 中の再判定ループは不要になるため止める。
     */
    fun analyzeCurrentPosition() {
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        pollJob?.cancel()
        pollJob = null
        startAnalysis(s.baseSfen, s.moves, s.flip)
    }

    /**
     * 着手・チップ移動後に呼ぶ自動発火の入り口。moves が空、または既に評価済み
     * （None 以外。Error も含む——失敗は手動リトライ待ちの終端状態として扱う）なら何もしない。
     *
     * [localEngineLikelyAvailable] が true ならその場で解析を開始する。false なら
     * [StudyEvalState.Preparing] にして、見込みが変わるまで [pollJob] で再判定を続ける
     * （資産ダウンロード完了後にユーザー操作なしで評価が出るようにするため）。
     */
    private fun maybeAutoAnalyze() {
        pollJob?.cancel()
        pollJob = null
        val s = _studyState.value ?: return
        if (s.moves.isEmpty()) return
        if (s.evalState != StudyEvalState.None) return
        if (!localEngineLikelyAvailable()) {
            _studyState.update { it?.copy(evalState = StudyEvalState.Preparing) }
            val baseSfen = s.baseSfen
            val moves = s.moves
            val flip = s.flip
            pollJob = scope.launch {
                while (true) {
                    delay(STUDY_LOCAL_ENGINE_POLL_INTERVAL_MS)
                    if (!localEngineLikelyAvailable()) continue
                    val cur = _studyState.value
                    // 局面が変わっていた、または手動リトライ等で既にPreparingを抜けていたら
                    // このループの役目は終わっている。
                    if (cur == null || cur.baseSfen != baseSfen || cur.moves != moves) return@launch
                    if (cur.evalState != StudyEvalState.Preparing) return@launch
                    startAnalysis(baseSfen, moves, flip)
                    return@launch
                }
            }
            return
        }
        startAnalysis(s.baseSfen, s.moves, s.flip)
    }

    /**
     * [baseSfen]+[moves] の局面をエンジンで解析し、完了したら結果を検討木へキャッシュする。
     * [studyEvalRunning] による単一実行ガードのため、既に他局面の解析が進行中のときは
     * 何もしない——[maybeAutoAnalyze] が呼び出し元（着手・チップ移動）と解析完了の両方から
     * 呼ばれるため、進行中の解析が完了した時点で「今の局面がまだ未評価なら」拾い直される
     * （速い連続着手でも最終的に今の局面が解析される。途中で通り過ぎた局面は解析されない）。
     *
     * 解析完了時、結果は常に [baseSfen]+[moves] の木へキャッシュする（呼び出し時点の局面と
     * 一致するかに関わらず）。表示中の evalState を上書きするのは局面が一致する場合のみで、
     * 一致しない（解析中に局面が進んでいた）場合も chipEvalStates は更新する
     * （通り過ぎた手のチップに評価値が後から併記される。ShogiHome同様の見え方）。
     */
    private fun startAnalysis(baseSfen: String, moves: List<String>, flip: Boolean) {
        if (studyEvalRunning) return
        studyEvalRunning = true
        _studyState.update { it?.copy(evalState = StudyEvalState.Loading) }

        scope.launch {
            val evalResult = withContext(ioDispatcher) {
                runCatching {
                    val engine = studyEngine ?: engineFactory().also { studyEngine = it }
                    val pv1 = engine.analyzeSfen(baseSfen, moves, nodes = STUDY_ANALYSIS_NODES)
                        .firstOrNull() ?: error("PV empty")
                    studyEvalLabel(baseSfen, moves, pv1, flip)
                }.getOrElse { StudyEvalState.Error }
            }
            studyEvalRunning = false

            val tree = (treesByOrigin[baseSfen] ?: StudyTree()).withEvalState(moves, evalResult)
            treesByOrigin[baseSfen] = tree

            val latest = _studyState.value
            when {
                latest == null -> Unit
                latest.baseSfen == baseSfen && latest.moves == moves -> {
                    _studyState.update {
                        it?.copy(evalState = evalResult, chipEvalStates = tree.evalStatesAlong(latest.displayLine))
                    }
                }
                latest.baseSfen == baseSfen -> {
                    _studyState.update { it?.copy(chipEvalStates = tree.evalStatesAlong(latest.displayLine)) }
                }
            }
            // 解析中に局面が進んでいた場合、今の局面がまだ未評価なら拾い直す
            // （studyEvalRunning が false に戻った直後なので、ここで即座に再発火できる）。
            maybeAutoAnalyze()
        }
    }

    private fun executeStudyMove(move: ShogiMove) {
        val s = _studyState.value ?: return
        applyMoves(s, s.moves + move.toUsiString())
    }

    /**
     * moves（検討開始局面からの実際の現在局面までの手列）を適用する共通処理。
     * 木への反映（新規なら分岐として追加・既存なら再利用。moves が s.moves の1手延長で
     * ないとき＝チップタップ等でのシークのときは木を触らない）→ 盤面再構築 →
     * displayLine の更新 → branchFlags/evalState/chipEvalStates を木から再取得、の順で行う
     * （chip移動・分岐切替・新手着手の3経路すべてがこの手順を共有する）。
     *
     * displayLine の更新規則: newMoves が現在の displayLine の prefix（＝表示中のライン内の
     * シーク、前後どちらへの移動でも該当）なら displayLine は変えない。それ以外
     * （displayLine の先端を超えて進んだ・displayLine と異なる手へ分岐した）は
     * displayLine を newMoves に置き換える（実機確認: 「戻ると先が消える」対応。
     * 別の手を指したときだけ表示中のラインが新しいものに切り替わる）。
     */
    private fun applyMoves(s: StudyState, newMoves: List<String>) {
        val addedMove = if (newMoves.size == s.moves.size + 1 && newMoves.dropLast(1) == s.moves) {
            newMoves.last()
        } else {
            null
        }
        var tree = treesByOrigin[s.baseSfen] ?: StudyTree()
        if (addedMove != null) {
            tree = tree.withMovePlayed(s.moves, addedMove, newId = nextNodeId())
            treesByOrigin[s.baseSfen] = tree
        }
        studyBoard = runCatching { ShogiBoard.fromSfen(s.baseSfen) }.getOrNull()?.also { b ->
            newMoves.forEach { m -> runCatching { b.push(ShogiMove.fromUsi(m)) } }
        }
        val newDisplayLine = if (
            newMoves.size <= s.displayLine.size && newMoves == s.displayLine.take(newMoves.size)
        ) {
            s.displayLine
        } else {
            newMoves
        }
        _studyState.value = s.copy(
            moves = newMoves,
            displayLine = newDisplayLine,
            chipEvalStates = tree.evalStatesAlong(newDisplayLine),
            branchFlags = tree.branchFlags(newDisplayLine),
            openBranchPopupDepth = null,
            branchPopupOptions = emptyList(),
            selectedFrom = null,
            selectedDropType = null,
            legalDestinations = emptySet(),
            showPromoteDialog = false,
            pendingPromoteMove = null,
            evalState = tree.evalStateAt(newMoves),
            showTurnHint = false,
        )
        // 新しい局面（未評価なら）の自動発火を判定する。チップタップ等で既に評価済みの
        // 局面へ戻ったときは maybeAutoAnalyze 内の evalState != None ガードで何もしない。
        maybeAutoAnalyze()
    }

    /** displayLine 上の depth 手目まで（0 = 検討開始局面）を現在局面として適用する。 */
    private fun navigateToDepth(depth: Int) {
        val s = _studyState.value ?: return
        applyMoves(s, s.displayLine.take(depth))
    }

    /**
     * エンジンPVのスコア（手番側視点）を先手視点に正規化して表示ラベル + 自分視点cpに変換する。
     * あわせて PV 先頭手（最善手）を現局面基準の棋譜表記へ変換する。
     *
     * PvInfo.score のドキュメント（dev.miyado.shogisupplement.engine.Engine.kt）:
     * 「手番側視点のスコア」。position_eval・BlunderJudge と同じ規約
     * （AnalysisService の positionEvalRows 生成: 手番がgoteなら反転）に合わせ、
     * 検討中の現局面（baseSfen + moves 適用後）の手番が先手なら符号そのまま、
     * 後手なら反転して先手視点にする。
     *
     * userCp は共通のcpエンコード規約（常にcp軸で保持する）に合わせるため、詰みも含めて
     * 同じ方式でエンコードしたうえでユーザー視点に正規化する。
     */
    private fun studyEvalLabel(baseSfen: String, moves: List<String>, pv: PvInfo, userIsGote: Boolean): StudyEvalState {
        val board = runCatching { ShogiBoard.fromSfen(baseSfen) }.getOrNull()
            ?: return StudyEvalState.Error
        moves.forEach { m -> runCatching { board.push(ShogiMove.fromUsi(m)) } }
        val moverIsSente = board.turn == Side.BLACK
        // mate_in=0 の勝敗判定用（parity のみ使うダミーply）。
        val syntheticPly = if (moverIsSente) 0 else 1

        val score = pv.score
        val moverCp = BlunderJudge.toCp(score)
        val senteCp = if (moverIsSente) moverCp else -moverCp
        val userCp = if (userIsGote) -senteCp else senteCp

        val label = when (score) {
            is Score.Cp -> {
                PositionEvalDisplay.format(
                    scoreCp = senteCp,
                    mateIn = null,
                    userIsGote = userIsGote,
                    evalDisplay = evalDisplayProvider(),
                    ply = syntheticPly,
                )
            }
            is Score.Mate -> {
                val senteMate = if (moverIsSente) score.plies else -score.plies
                PositionEvalDisplay.format(
                    scoreCp = null,
                    mateIn = senteMate,
                    userIsGote = userIsGote,
                    evalDisplay = evalDisplayProvider(),
                    ply = syntheticPly,
                )
            }
        }
        // board はこの時点で現局面（baseSfen+moves適用後）のまま——PV先頭手はここからの
        // 指し手なので、チップと同じ JapaneseNotation.format(usi, board) で整形できる。
        val bestMoveText = pv.pv.firstOrNull()?.let { usi ->
            runCatching { JapaneseNotation.format(usi, board) }.getOrNull()
        }
        return label?.let { StudyEvalState.Value(it, userCp = userCp, bestMoveText = bestMoveText) } ?: StudyEvalState.None
    }
}
