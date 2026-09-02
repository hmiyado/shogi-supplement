package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.miyado.shogisupplement.classify.ClassificationResult
import dev.miyado.shogisupplement.db.DrillRepository
import dev.miyado.shogisupplement.db.GameRepository
import dev.miyado.shogisupplement.db.ShogiSupplementDatabase
import dev.miyado.shogisupplement.db.SqlDelightDrillRepository
import dev.miyado.shogisupplement.db.SqlDelightGameRepository
import dev.miyado.shogisupplement.judge.Judgement
import dev.miyado.shogisupplement.judge.VerdictKind
import dev.miyado.shogisupplement.pipeline.BlunderReport
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.drillrecord.DrillRecordDetailScreen
import dev.miyado.shogisupplement.ui.drillrecord.DrillRecordDetailViewModel
import dev.miyado.shogisupplement.ui.home.DrillRecordCard
import dev.miyado.shogisupplement.ui.home.DrillRecordCardData
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * 学習の記録カードから詳細画面へ至る導線のテスト。
 * VRTは画面をフィクスチャで直接描くため、カードのタップと、実データを読んで画面へ出す
 * ところが通らない。ここで両端を繋ぐ。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi")
class DrillRecordDetailFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun newDatabase(): ShogiSupplementDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShogiSupplementDatabase.Schema.create(driver)
        return ShogiSupplementDatabase(driver)
    }

    private fun epochSecondsAt(tokyoDateTime: String): Long =
        ZonedDateTime.parse("$tokyoDateTime+09:00[Asia/Tokyo]").toEpochSecond()

    /** ドリルの出題元となる悪手を1件仕込み、そのIDを返す。 */
    private fun seedBlunder(gameRepository: GameRepository): Long {
        val report = BlunderReport(
            ply = 41,
            side = "sente",
            moveUsi = "B*3d",
            bestUsi = "2f6f",
            lossWp = 0.225,
            classification = ClassificationResult(
                category = "駒損（タクティクス）",
                diffMaterial = -11,
                punishChecks = 0,
                tookMovedPiece = false,
                missedMateIn = null,
            ),
            judgement = Judgement(
                kind = VerdictKind.TARGET,
                verdict = "○ 出題対象",
                note = "自帯6.3件/1000手 (上帯5.2件)。帯として典型的なミス",
                problem = "手筋 (両取り・素抜き) の問題",
                priority = 3.0,
            ),
        )
        val gameId = gameRepository.saveAnalysis(
            fileName = "g.kif",
            contentHash = "h",
            moves = listOf("7g7f", "3c3d", "2g2f"),
            headers = emptyMap(),
            reports = listOf(report),
            rating = 1750,
            coefVersion = "hao_v1",
        )
        return gameRepository.getReports(gameId).first().id
    }

    @Test
    fun `カードのタップで詳細画面を開く導線が繋がっている`() {
        var opened = false
        composeRule.setContent {
            ShogiTheme {
                Surface {
                    DrillRecordCard(
                        drillRecordCard = DrillRecordCardData(
                            activeDaysInWindow = 3,
                            windowDays = 30,
                            totalAttempts = 5,
                            weekStreakCount = 0,
                        ),
                        shogiColors = androidx.compose.material3.MaterialTheme.shogiColors,
                        onCardClick = { opened = true },
                    )
                }
            }
        }

        composeRule.onNodeWithText(AppStrings.DRILL_RECORD_CARD_TITLE).performClick()

        assertEquals(true, opened)
    }

    @Test
    fun `実データを読み込んで詳細画面へ出す`() = runTest {
        val database = newDatabase()
        val gameRepository: GameRepository = SqlDelightGameRepository(database)
        val drillRepository: DrillRepository = SqlDelightDrillRepository(database)
        val blunderId = seedBlunder(gameRepository)

        // 6/5に2問（1問正解）、6/7に1問正解。
        drillRepository.saveDrillAttempt(blunderId, "7g7f", true, 0.0, epochSecondsAt("2025-06-05T10:00:00"))
        drillRepository.saveDrillAttempt(blunderId, "2g2f", false, 0.3, epochSecondsAt("2025-06-05T11:00:00"))
        drillRepository.saveDrillAttempt(blunderId, "7g7f", true, 0.0, epochSecondsAt("2025-06-07T10:00:00"))

        val data = checkNotNull(
            DrillRecordDetailViewModel(drillRepository, ioDispatcher = Dispatchers.Unconfined)
                .loadDrillRecordDetail(),
        )

        assertEquals(3, data.totalAttempts)
        assertEquals(2, data.correctAttempts)
        assertEquals(30, data.dailyCounts.size)

        composeRule.setContent {
            ShogiTheme {
                Surface { DrillRecordDetailScreen(data = data, onBack = {}) }
            }
        }

        composeRule.onNodeWithText(AppStrings.DRILL_RECORD_DETAIL_TITLE).assertIsDisplayed()
        // 概要は「累計 3問 ・ 正答率 67%（2/3）」。カードと同じ集計から作る。
        composeRule.onNodeWithText("67%（2/3）", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(AppStrings.DRILL_RECORD_DETAIL_STREAK_EMPTY).assertIsDisplayed()
    }

    @Test
    fun `1問も解いていなければ詳細画面のデータを作らない`() = runTest {
        val database = newDatabase()
        val drillRepository: DrillRepository = SqlDelightDrillRepository(database)

        val data = DrillRecordDetailViewModel(drillRepository, ioDispatcher = Dispatchers.Unconfined)
            .loadDrillRecordDetail()

        assertNull(data)
    }
}
