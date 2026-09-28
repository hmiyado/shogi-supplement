package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.board.ShogiBoard
import dev.miyado.shogisupplement.board.ShogiMove
import dev.miyado.shogisupplement.db.BlunderRecord
import dev.miyado.shogisupplement.db.EngineMatchRate
import dev.miyado.shogisupplement.text.AppStrings
import kotlin.math.roundToInt
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
import dev.miyado.shogisupplement.ui.theme.LightBg
import dev.miyado.shogisupplement.ui.theme.LightInk
import dev.miyado.shogisupplement.ui.theme.LightPrimarySoft
import dev.miyado.shogisupplement.ui.theme.LightSurface
import dev.miyado.shogisupplement.ui.theme.ShipporiMinchoFamily
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Why not ステータスバーとホームインジケータも描く: OSのUIを描き足すと実機を騙ることになる。
/** ストア掲載画像（見出し＋端末の枠）を書き出す。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    // 台紙は端末（[PhoneWidth]）より大きく、縦横比は [OutputWidthPx]:[OutputHeightPx] に合わせる。
    qualifiers = "w500dp-h1084dp-xxhdpi",
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

    private fun capture(fileName: String, caption: String, screen: @Composable () -> Unit) {
        val path = "../iosApp/fastlane/screenshots/ja/$fileName.png"
        captureRoboImage(filePath = path, roborazziOptions = storeRoborazziOptions) {
            StoreCard(caption = caption, screen = screen)
        }
        val source = ImageIO.read(File(path))
        finalizeImage(source, File(path), OutputWidthPx, OutputHeightPx)
        finalizeImage(source, File("../iosApp/fastlane/screenshots/ja/${fileName}_6.5.png"), 1284, 2778)
    }

    /**
     * 提出枠へ縮小し、アルファチャンネルを落とす。
     * Why not 提出枠のまま描く: 端末の中身を実寸（[PhoneWidth]）で組み立てたいので、
     * 台紙は端末より大きい。App Store Connect は透過を含む画像も受け付けない。
     */
    private fun finalizeImage(source: BufferedImage, file: File, width: Int, height: Int) {
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        output.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            drawImage(source, 0, 0, width, height, null)
            dispose()
        }
        ImageIO.write(output, "png", file)
    }


    @Test
    fun `01 home`() {
        capture(
            "01_home",
            "悪手を復習して\n棋力向上",
            {
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
    @Config(qualifiers = "+night")
    fun `02 home dark`() {
        capture(
            "02_home_dark",
            "ダークモードも対応",
            {
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
    fun `03 report graph`() {
        capture("03_report_graph", "形勢をグラフで確認") { StoreReport() }
    }

    @Test
    fun `04 blunder list`() {
        capture("04_blunder_list", "ポイントとなる\n悪手をピックアップ") { StoreReport(showBlunderList = true) }
    }

    @Test
    fun `05 drill question`() {
        capture(
            "05_drill_question",
            "自分の棋譜から\n問題を出題",
            {
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
            "正解は AI で判定",
            {
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

    @Composable
    private fun StoreReport(
        showBlunderList: Boolean = false,
    ) {
        val matchRate = checkNotNull(EngineMatchRate.compute(storeMoves, storePositionEvals, "sente"))
        ReportScreen(
            game = storeGames.first(),
            reports = listOf(storeBlunder),
            flip = false,
            positionEvals = storePositionEvals,
            matchRateDisplayText = AppStrings.matchRateValue(
                (matchRate.rate * 100).roundToInt(), matchRate.matched, matchRate.sampleMoves,
            ),
            blunderRateDisplayText = AppStrings.blunderRateValue(
                (100.0 / matchRate.sampleMoves).roundToInt(), 1, matchRate.sampleMoves,
            ),
            initialPlyIndex = 41,
            initialBodyModeList = showBlunderList,
            onBack = {},
        )
    }
}

/** ビルド側（androidApp/build.gradle.kts）がテストJVMへ渡す。 */
private const val STORE_IMAGE_PROPERTY = "shogi.storeImages"

/** App Store Connect の6.9インチ枠。 */
private const val OutputWidthPx = 1290
private const val OutputHeightPx = 2796

/** 端末の中身は6.9インチの実寸で組み立てる。縮小は画像にしてから行う。 */
private val PhoneWidth = 430.dp
private val PhoneHeight = 932.dp

// 6枚を並べたときにキャプションの行数で端末の位置がずれないよう、見出し帯の高さは固定する。
private val CaptionTop = 56.dp
private val CaptionBandHeight = 136.dp
private val DeviceTop = CaptionTop + CaptionBandHeight + 20.dp

private val BezelWidth = 8.dp

/**
 * 見出し＋端末の枠の2段。端末は台紙の下端で切れる長さにして、画面の続きがあることを示す。
 * 背景・文字色をテーマから取らない理由: ダークモードの端末を明るい台紙に載せる枚があるため。
 */
@Composable
private fun StoreCard(caption: String, screen: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(LightBg, LightPrimarySoft),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = CaptionTop, start = 32.dp, end = 32.dp)
                .requiredHeight(CaptionBandHeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = caption,
                style = TextStyle(
                    fontFamily = ShipporiMinchoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 42.sp,
                    lineHeight = 62.sp,
                    textAlign = TextAlign.Center,
                    color = LightInk,
                ),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = DeviceTop)
                .requiredWidth(PhoneWidth + BezelWidth * 2)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(topStart = 56.dp, topEnd = 56.dp),
                    clip = false,
                )
                .clip(RoundedCornerShape(topStart = 56.dp, topEnd = 56.dp))
                .background(LightSurface)
                .padding(top = BezelWidth, start = BezelWidth, end = BezelWidth),
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(PhoneWidth, PhoneHeight)
                    .clip(RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)),
            ) {
                ShogiTheme { Surface { screen() } }
            }
        }
    }
}

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
    KifParser().parse(File("../data/kifu_samples/miyado_game1.kif").readText()).moves

private const val STORE_BLUNDER_PLY = 41

private const val STORE_BEST_USI = "2f6f"

private val storeGames = listOf(
    GameRecord(
        id = 1L,
        fileName = "lishogi_20260910.kif",
        contentHash = "store1",
        moveCount = storeMoves.size.toLong(),
        senteName = "あなた",
        goteName = "相手",
        analyzedAt = 1_789_000_000L,
        rating = 1750L,
        coefVersion = "hao_v1",
        movesUsi = storeMoves,
        // 41手目の悪手（storeBlunder）を指した側を自分にする。ここが噛み合わないと
        // 「自分の悪手の直後に自分の形勢が良くなる」グラフになる。
        userSide = "sente",
        uploadedAt = 1_789_000_100L,
        sourcePlace = "lishogi",
        gameWinner = "gote",
        endReason = "投了",
    ),
    GameRecord(
        id = 2L,
        fileName = "wars_20260909.kif",
        contentHash = "store2",
        moveCount = 94L,
        senteName = "あなた",
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
        senteName = "あなた",
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

private val storeEvalRecords =
    File("../analysis/src/jvmTest/resources/evals_game1.ndjson").readLines()
        .filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

// USIのスコアは手番視点だが、保存済み評価値は先手視点で扱う。
private val storePositionEvals: List<PositionEvalRow> =
    storeEvalRecords.mapIndexed { index, record ->
            val ply = record.getValue("ply").jsonPrimitive.int
            check(record.getValue("file").jsonPrimitive.content == "miyado_game1.kif")
            check(ply == index)
            val score = record.getValue("score").jsonObject
            val sign = if (ply % 2 == 0) 1 else -1
            PositionEvalRow(
                ply = ply,
                scoreCp = score["cp"]?.jsonPrimitive?.int?.times(sign),
                mateIn = score["mate"]?.jsonPrimitive?.int?.times(sign),
                bestUsi = record.getValue("pv").jsonArray.firstOrNull()?.jsonPrimitive?.content,
            )
        }.also {
            check(it.size == storeMoves.size + 1)
            check(it[STORE_BLUNDER_PLY - 1].bestUsi == STORE_BEST_USI)
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
    bestPv = STORE_BEST_USI,
    punishPv = storeEvalRecords[STORE_BLUNDER_PLY].getValue("pv").jsonArray
        .joinToString(" ") { it.jsonPrimitive.content },
    cpBefore = storePositionEvals[STORE_BLUNDER_PLY - 1].scoreCp!!.toLong(),
)
