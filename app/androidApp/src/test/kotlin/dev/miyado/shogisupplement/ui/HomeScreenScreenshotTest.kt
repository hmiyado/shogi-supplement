package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.R
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.pipeline.InProgressAnalysis
import dev.miyado.shogisupplement.pipeline.ProgressiveReportState
import dev.miyado.shogisupplement.ui.home.DrillRecordCardData
import dev.miyado.shogisupplement.ui.home.HomeScreen
import dev.miyado.shogisupplement.ui.home.StrengthCardData
import dev.miyado.shogisupplement.ui.home.TodaysDrillHint
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h800dp-xxhdpi",
    application = android.app.Application::class,
)
class HomeScreenScreenshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    private val roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
    )

    @Composable
    private fun testTitleIcon() {
        Image(
            painter = painterResource(id = R.drawable.ic_app_title_icon),
            contentDescription = null,
            modifier = Modifier
                .height(30.dp)
                .width(24.dp),
        )
    }

    private fun sampleGames() = listOf(
        GameRecord(
            id = 1L,
            fileName = "miyado_game1.kif",
            contentHash = "hash1",
            moveCount = 74L,
            senteName = "miyado",
            goteName = "相手",
            analyzedAt = 1_780_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            uploadedAt = 1_780_000_100L,  // アップロード済み
        ),
        GameRecord(
            id = 2L,
            fileName = "miyado_game2.kif",
            contentHash = "hash2",
            moveCount = 50L,
            senteName = null,
            goteName = null,
            analyzedAt = 1_779_000_000L,
            rating = 1750L,
            coefVersion = "hao_v1",
            uploadedAt = null,  // 未アップロード
        ),
    )

    private val analyzingMoves = listOf(
        "7g7f", "3c3d", "2g2f", "8c8d", "2f2e", "8d8e", "2e2d", "2c2d", "2h2d", "4a3b",
    )

    private fun pv(cp: Int): List<PvInfo> =
        listOf(PvInfo(multipv = 1, score = Score.Cp(cp), pv = emptyList(), nodes = 0L))

    private fun sampleAnalyzingSession(confirmedThrough: Int): InProgressAnalysis {
        var progressive = ProgressiveReportState.initial(analyzingMoves)
        for (ply in 0 until confirmedThrough) {
            progressive = progressive.withPosition(ply, pv(40))
        }
        return InProgressAnalysis(
            id = "analyzing-hash",
            fileName = "miyado_game3.kif",
            userSide = "sente",
            progressive = progressive,
        )
    }

    @Test
    fun home_loggedIn_withUploadStatus() {
        captureRoboImage(
            filePath = "src/test/snapshots/home_logged_in_with_upload.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    HomeScreen(
                        pastGames = sampleGames(),
                        isLoggedIn = true,
                        todaysDrillHint = TodaysDrillHint(
                            ply = 41L,
                        ),
                        onOpenKif = {},
                        onGameClick = {},
                        onStartDrill = {},
                        titleIcon = { testTitleIcon() },
                    )
                }
            }
        }
    }

    @Test
    fun home_withStrengthCard() {
        captureRoboImage(
            filePath = "src/test/snapshots/home_with_strength_card.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    HomeScreen(
                        pastGames = sampleGames(),
                        isLoggedIn = false,
                        strengthCard = StrengthCardData(
                            displayText = "51 ±25",
                            detailText = "直近5局から算出",
                        ),
                        todaysDrillHint = TodaysDrillHint(
                            ply = 27L,
                        ),
                        onOpenKif = {},
                        onGameClick = {},
                        onStartDrill = {},
                        titleIcon = { testTitleIcon() },
                    )
                }
            }
        }
    }

    @Test
    fun home_withDrillRecordCard() {
        captureRoboImage(
            filePath = "src/test/snapshots/home_with_drill_record_card.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    HomeScreen(
                        pastGames = sampleGames(),
                        isLoggedIn = false,
                        strengthCard = StrengthCardData(
                            displayText = "58",
                            detailText = "直近8局から算出",
                        ),
                        drillRecordCard = DrillRecordCardData(
                            activeDaysInWindow = 18,
                            windowDays = 30,
                            totalAttempts = 142,
                        ),
                        todaysDrillHint = TodaysDrillHint(
                            ply = 32L,
                        ),
                        onOpenKif = {},
                        onGameClick = {},
                        onStartDrill = {},
                        titleIcon = { testTitleIcon() },
                    )
                }
            }
        }
    }

    @Test
    fun home_notLoggedIn() {
        captureRoboImage(
            filePath = "src/test/snapshots/home_not_logged_in.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    HomeScreen(
                        pastGames = sampleGames(),
                        isLoggedIn = false,
                        onOpenKif = {},
                        onGameClick = {},
                        onStartDrill = {},
                        titleIcon = { testTitleIcon() },
                    )
                }
            }
        }
    }

    @Test
    fun home_manyGames() {
        val manyGames = sampleGames() + listOf(
            GameRecord(
                id = 3L,
                fileName = "miyado_game3.kif",
                contentHash = "hash3",
                moveCount = 88L,
                senteName = "miyado",
                goteName = "相手2",
                analyzedAt = 1_778_000_000L,
                rating = 1750L,
                coefVersion = "hao_v1",
                uploadedAt = null,
            ),
            GameRecord(
                id = 4L,
                fileName = "miyado_game4.kif",
                contentHash = "hash4",
                moveCount = 62L,
                senteName = "相手3",
                goteName = "miyado",
                analyzedAt = 1_777_000_000L,
                rating = 1750L,
                coefVersion = "hao_v1",
                uploadedAt = null,
            ),
        )
        captureRoboImage(
            filePath = "src/test/snapshots/home_many_games.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    HomeScreen(
                        pastGames = manyGames,
                        isLoggedIn = false,
                        onOpenKif = {},
                        onGameClick = {},
                        onStartDrill = {},
                        onViewAllGames = {},
                        titleIcon = { testTitleIcon() },
                    )
                }
            }
        }
    }

    @Test
    fun home_withAnalyzingCard() {
        captureRoboImage(
            filePath = "src/test/snapshots/home_with_analyzing_card.png",
            roborazziOptions = roborazziOptions,
        ) {
            ShogiTheme {
                Surface {
                    HomeScreen(
                        pastGames = sampleGames(),
                        isLoggedIn = true,
                        analyzingSessions = listOf(sampleAnalyzingSession(confirmedThrough = 4)),
                        onOpenKif = {},
                        onGameClick = {},
                        onAnalyzingClick = {},
                        onStartDrill = {},
                        titleIcon = { testTitleIcon() },
                    )
                }
            }
        }
    }
}
