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
import dev.miyado.shogisupplement.db.GameRecord
import dev.miyado.shogisupplement.ui.home.HomeScreen
import dev.miyado.shogisupplement.ui.home.StrengthCardData
import dev.miyado.shogisupplement.ui.home.TodaysDrillHint
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ホーム画面の VRT（スクリーンショットテスト）。
 * レイアウト検証:
 *   ①推定棋力カード ②今日の1問カード ③過去の解析リスト（上から順）
 *   画面最下部の固定「棋譜を追加する」ボタン・⚙設定アイコン
 *   タイトル左のアプリアイコン（全golden共通）。
 *   「直近の解析」見出し右の「すべて見る」は維持・リスト末尾の「すべて見る」は削除
 *   （home_manyGames で4局投入し確認）。
 *
 * HomeScreen は :ui commonMain（dev.miyado.shogisupplement.ui.home.HomeScreen）にある。
 * タイトルアイコンは Android専用リソースのため titleIcon スロットへホイストしているので、
 * golden 画像を不変に保つため本番（MainActivity.kt）と同じ
 * Image(painterResource(R.drawable.ic_app_title_icon)) を各テストから明示的に渡す。
 */
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

    /**
     * HomeScreen の titleIcon スロットに渡す本番同等の実装
     * （MainActivity.kt の HomeScreen 呼び出しと同一のImage/painterResource/Modifier）。
     * golden 画像を不変に保つため、全テストからこの実装を渡す。
     */
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

    /** ログイン中: アップロードバッジがゲームカードに表示される。 */
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

    /** 強さ指標カード表示（user_side入りの解析済み局がある場合）。推定棋力カードが最上部に表示される。 */
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

    /** 未ログイン: アップロードバッジが表示されない。 */
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

    /**
     * 4局以上のとき「直近の解析」見出し右の「すべて見る」は表示されるが、
     * リスト末尾の「すべて見る」ボタンは表示されない（削除確認）。
     */
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
}
