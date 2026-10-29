package dev.miyado.shogisupplement.ui

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.miyado.shogisupplement.board.*
import dev.miyado.shogisupplement.engine.StudyEngine
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.repertoire.*
import dev.miyado.shogisupplement.ui.repertoire.InitialPositionEditorScreen
import dev.miyado.shogisupplement.ui.repertoire.RepertoireScreen
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xxhdpi", application = android.app.Application::class)
class Release115PromoScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private fun position(): ShogiBoard = ShogiBoard().also { board ->
        "7g7f 3c3d 2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d 8e8f 8g8f 8b8f 2d3d 2b3c 5i5h 5a5b 3g3f"
            .split(" ").forEach { board.push(ShogiMove.fromUsi(it)) }
    }
    private fun repository(sfen: String) = Repository().also { repo ->
        repo.addPositionLabel("owner", sfen, PositionLabelScope.ALL, "横歩取り")
        repo.addPositionLabel("owner", sfen, PositionLabelScope.BLACK, "青野流")
    }
    @Test fun repertoire() {
        val sfen = position().toSfen()
        val repo = repository(sfen)
        repo.save("owner", "aono", "line", RepertoireCodec.encode(RepertoireDocument("横歩取り・青野流", sfen,
            listOf(RepertoireLine("8f7f", listOf(RepertoireLine("2i3g")))))))
        rule.setContent { ShogiTheme { Surface { RepertoireScreen(repo, "owner", null, { Engine() }, {}) } } }
        rule.onNodeWithText("横歩取り・青野流").performClick()
        rule.onRoot().captureRoboImage(filePath = "../../docs/promo/release-1.15-repertoire-full.png", roborazziOptions = screenshotRoborazziOptions)
    }
    @Test fun labels() {
        val sfen = position().toSfen()
        val repo = repository(sfen)
        val doc = RepertoireDocument("横歩取り・青野流", sfen)
        rule.setContent { ShogiTheme { Surface(Modifier.fillMaxSize()) {
            dev.miyado.shogisupplement.ui.repertoire.PositionLabelEditor(repo, "owner", sfen, {}, document = doc)
        } } }
        rule.onAllNodesWithText("先手：青野流").onFirst().assertIsDisplayed()
        rule.onRoot().captureRoboImage(filePath = "../../docs/promo/release-1.15-labels-full.png", roborazziOptions = screenshotRoborazziOptions)
    }
    private class Engine : StudyEngine {
        override suspend fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int, multiPv: Int): List<PvInfo> = emptyList()
        override fun quit() {}
    }
    private class Repository : RepertoireRepository {
        private val values = mutableMapOf<String, RepertoireEntry>()
        override fun entries(owner: String) = values.values.toList()
        override fun save(owner: String, id: String, kind: String, payload: String) { values[id] = RepertoireEntry(id, kind, payload, 1, null, true) }
        override fun acknowledge(owner: String, id: String, revision: Long, remoteVersion: String) {}
        override fun acceptRemote(owner: String, id: String, kind: String, payload: String, remoteVersion: String) {}
    }
}
