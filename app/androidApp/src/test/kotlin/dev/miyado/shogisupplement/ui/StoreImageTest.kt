package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.blunder.PositionEvalDisplay
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.db.PositionEvalRow
import dev.miyado.shogisupplement.drill.DrillJudge
import dev.miyado.shogisupplement.kifu.KifParser
import dev.miyado.shogisupplement.ui.drill.DrillQuestionContent
import dev.miyado.shogisupplement.ui.drill.DrillResultContent
import dev.miyado.shogisupplement.ui.drill.DrillUiState
import dev.miyado.shogisupplement.ui.home.DrillRecordCardData
import dev.miyado.shogisupplement.ui.home.HomeScreen
import dev.miyado.shogisupplement.ui.home.StrengthCardData
import dev.miyado.shogisupplement.ui.home.TodaysDrillHint
import dev.miyado.shogisupplement.ui.report.ReportScreen
import dev.miyado.shogisupplement.ui.report.StudyCandidate
import dev.miyado.shogisupplement.ui.report.StudyEvalState
import dev.miyado.shogisupplement.ui.report.StudyOrigin
import dev.miyado.shogisupplement.ui.report.StudyState
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 430x932dp・xxhdpi = 1290x2796px は App Store Connect の6.9インチ枠。
// Why not ステータスバーとホームインジケータも描く: OSのUIを描き足すと実機を騙ることになる。
/** ストア掲載画像を書き出す。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w430dp-h932dp-xxhdpi",
    application = android.app.Application::class,
)
class StoreImageTest {

    /**
     * VRTのゴールデンと違い差分が出ることが目的なので、verifyRoborazziDebug の巻き添えで
     * 落ちないよう明示指示のときだけ走らせる。
     */
    @Before
    fun requireExplicitRequest() {
        assumeTrue(System.getProperty(STORE_IMAGE_PROPERTY) == "true")
    }

    private fun capture(fileName: String, content: @Composable () -> Unit) {
        val path = "../iosApp/fastlane/screenshots/ja/$fileName.png"
        captureRoboImage(filePath = path, roborazziOptions = storeRoborazziOptions, content = content)
        dropAlphaChannel(File(path))
    }

    /** App Store Connect は透過を含むスクリーンショットを受け付けない。書き出しはRGBAなので落とす。 */
    private fun dropAlphaChannel(file: File) {
        val source = ImageIO.read(file)
        val opaque = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        opaque.createGraphics().apply {
            drawImage(source, 0, 0, null)
            dispose()
        }
        ImageIO.write(opaque, "png", file)
    }

    private fun themed(content: @Composable () -> Unit): @Composable () -> Unit = {
        ShogiTheme { Surface { content() } }
    }

    @Test
    fun `01 home`() {
        capture(
            "01_home",
            themed {
                HomeScreen(
                    pastGames = storeGames,
                    isLoggedIn = true,
                    strengthCard = StrengthCardData(
                        displayText = "58 ±9",
                        detailText = "直近20局から算出",
                        declaredRankLine = "申告: 将棋ウォーズ 初段（10秒将棋）",
                    ),
                    todaysDrillHint = TodaysDrillHint(ply = 41L),
                    drillRecordCard = DrillRecordCardData(
                        activeDaysInWindow = 22,
                        windowDays = 30,
                        totalAttempts = 214,
                        weekStreakCount = 3,
                    ),
                    onOpenKif = {},
                    onGameClick = {},
                    onStartDrill = {},
                    onViewAllGames = {},
                )
            },
        )
    }

    @Test
    fun `02 report graph`() {
        capture("02_report_graph", themed { StoreReport() })
    }

    @Test
    fun `03 blunder list`() {
        capture("03_blunder_list", themed { StoreReport(showBlunderList = true) })
    }

    @Test
    fun `04 study`() {
        capture("04_study", themed { StoreReport(studyState = storeStudyState) })
    }

    @Test
    fun `05 drill question`() {
        capture(
            "05_drill_question",
            themed {
                DrillQuestionContent(
                    state = DrillUiState.Question(
                        blunder = storeBlunder,
                        sfenCurrent = storeBlunder.sfenBefore,
                        attemptCount = 1,
                        totalCandidates = 5,
                    ),
                    onSquareTapped = {},
                    onHandPieceTapped = {},
                    onPromoteDecision = {},
                    onSurrender = {},
                    onUndoMove = {},
                    onResetMoves = {},
                    onSubmitAnswer = {},
                )
            },
        )
    }

    @Test
    fun `06 drill result`() {
        capture(
            "06_drill_result",
            themed {
                DrillResultContent(
                    result = DrillJudge.DrillResult(
                        isCorrect = true,
                        lossWp = 0.0,
                        userMoveUsi = STORE_BEST_USI,
                        bestMoveUsi = STORE_BEST_USI,
                        reason = DrillJudge.Reason.MATCH_BEST,
                    ),
                    blunder = storeBlunder,
                    onNext = {},
                    onBack = {},
                )
            },
        )
    }

    @Test
    @Config(qualifiers = "+night")
    fun `07 report dark`() {
        capture("07_report_dark", themed { StoreReport() })
    }

    @Composable
    private fun StoreReport(
        showBlunderList: Boolean = false,
        studyState: StudyState? = null,
    ) {
        ReportScreen(
            game = storeGames.first(),
            reports = listOf(storeBlunder),
            flip = false,
            strengthDisplayText = "58 ±9",
            matchRateDisplayText = "64%(32/50)",
            blunderRateDisplayText = "9%(3/33)",
            positionEvals = storePositionEvals,
            initialPlyIndex = 41,
            initialBodyModeList = showBlunderList,
            studyState = studyState,
            onBack = {},
        )
    }
}

/** ビルド側（androidApp/build.gradle.kts）がテストJVMへ渡す。 */
private const val STORE_IMAGE_PROPERTY = "shogi.storeImages"

/** ストア画像は縮小せずそのまま書き出す（VRTのゴールデンは0.5倍で記録している）。 */
@OptIn(ExperimentalRoborazziApi::class)
private val storeRoborazziOptions = RoborazziOptions(
    recordOptions = RoborazziOptions.RecordOptions(resizeScale = 1.0),
)

/**
 * 盤も評価値グラフも実際の対局のものを出す。手を自作すると非合法手でパースが止まり、
 * 盤が初期局面のまま写る。
 */
private val storeMoves: List<String> =
    KifParser().parse(File("../data/kifu_samples/wars_game1.kif").readText()).moves

private const val STORE_BLUNDER_PLY = 41

/** ▲２四歩から△２四歩。どちらもこの局面で合法な手にする。 */
private const val STORE_BEST_USI = "2e2d"
private const val STORE_BEST_REPLY_USI = "2c2d"

private val storeGames = listOf(
    GameRecord(
        id = 1L,
        fileName = "wars_20260910.kif",
        contentHash = "store1",
        moveCount = storeMoves.size.toLong(),
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_789_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = storeMoves,
        // 41手目の悪手（storeBlunder）を指した側を自分にする。ここが噛み合わないと
        // 「自分の悪手の直後に自分の形勢が良くなる」グラフになる。
        userSide = "sente",
        uploadedAt = 1_789_000_100L,
        sourcePlace = "wars",
        gameWinner = "gote",
        endReason = "投了",
    ),
    GameRecord(
        id = 2L,
        fileName = "wars_20260909.kif",
        contentHash = "store2",
        moveCount = 94L,
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_788_900_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        uploadedAt = 1_788_900_100L,
        sourcePlace = "wars",
        gameWinner = "gote",
        endReason = "投了",
    ),
    GameRecord(
        id = 3L,
        fileName = "lishogi_20260908.kif",
        contentHash = "store3",
        moveCount = 71L,
        senteName = "miyado",
        goteName = "相手",
        analyzedAt = 1_788_800_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        uploadedAt = 1_788_800_100L,
        sourcePlace = "lishogi",
        gameWinner = "sente",
        endReason = "投了",
    ),
)

/** [STORE_BLUNDER_PLY] 手目の直前の局面。実戦手・最善手はここで合法でなければ盤が描けない。 */
private val storeSfenBeforeBlunder: String = ShogiBoard().run {
    storeMoves.take(STORE_BLUNDER_PLY - 1).forEach { push(ShogiMove.fromUsi(it)) }
    toSfen()
}

private val storeBlunder = BlunderRecord(
    id = 1L,
    gameId = 1L,
    ply = STORE_BLUNDER_PLY.toLong(),
    side = "sente",
    moveUsi = storeMoves[STORE_BLUNDER_PLY - 1],
    bestUsi = STORE_BEST_USI,
    lossWp = 0.225,
    sfenBefore = storeSfenBeforeBlunder,
    category = "駒損（タクティクス）",
    diffMaterial = -11L,
    punishChecks = 0L,
    tookMovedPiece = false,
    missedMateIn = null,
    verdict = "○ 出題対象",
    note = "あなたの棋力帯(偏差値47-59): 約3局に1回",
    problemType = "手筋 (両取り・素抜き) の問題",
    priority = 2.9978349024480666,
    bestPv = "$STORE_BEST_USI $STORE_BEST_REPLY_USI",
    punishPv = "${storeMoves[STORE_BLUNDER_PLY]} ${storeMoves[STORE_BLUNDER_PLY + 1]}",
    cpBefore = 240L,
)

/** [STORE_BLUNDER_PLY] 手目で一気に傾き、そこから戻り切らない曲線。 */
private val storePositionEvals: List<PositionEvalRow> = storeMoves.indices.map { ply ->
    val cp = if (ply < STORE_BLUNDER_PLY) 40 + ply * 5 else -210 - (ply - STORE_BLUNDER_PLY) * 5
    PositionEvalRow(ply = ply, scoreCp = cp, mateIn = null, bestUsi = storeMoves[ply])
}

// 最善手を指した先を検討している状態。候補手は手番（後手）の応手で、
// 評価は自分（先手）視点のまま出る。
private val storeStudyState = StudyState(
    baseSfen = storeSfenBeforeBlunder,
    moves = listOf(STORE_BEST_USI),
    displayLine = listOf(STORE_BEST_USI, STORE_BEST_REPLY_USI),
    chipEvalStates = listOf(
        StudyEvalState.Value(PositionEvalDisplay.EvalLabel(text = "+240", sign = 1), userCp = 240),
    ),
    origin = StudyOrigin(label = "41手目 ▲５八金（−210）", userCp = -210),
    originIsBestPv = false,
    originPlyIndex = STORE_BLUNDER_PLY,
    originSelectedIdx = null,
    originAbsolutePly = STORE_BLUNDER_PLY,
    flip = false,
    branchFlags = listOf(false, false),
    evalState = StudyEvalState.Value(
        PositionEvalDisplay.EvalLabel(text = "+240", sign = 1),
        userCp = 240,
        candidates = listOf(
            StudyCandidate("2c2d", "△２四歩", PositionEvalDisplay.EvalLabel(text = "+240", sign = 1)),
            StudyCandidate("3c2d", "△２四銀", PositionEvalDisplay.EvalLabel(text = "+310", sign = 1)),
            StudyCandidate("8e8f", "△８六歩", PositionEvalDisplay.EvalLabel(text = "+520", sign = 1)),
        ),
    ),
)
