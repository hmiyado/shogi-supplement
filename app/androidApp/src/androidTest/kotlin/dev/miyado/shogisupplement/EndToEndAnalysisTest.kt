package dev.miyado.shogisupplement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.miyado.shogisupplement.engine.createAndroidAnalysisRunner
import dev.miyado.shogisupplement.engine.EvalLoader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration

/**
 * 実機E2E: miyado_game1.kif 74手を全局面解析し所要時間を計測
 */
@RunWith(AndroidJUnit4::class)
class EndToEndAnalysisTest {

    companion object {
        private lateinit var evalDir: File

        // miyado_game1.kif の USI 手列（74手）
        private val GAME_MOVES = "2g2f 3c3d 2f2e 2b4d 3i4h 4a3b 4i5h 3d3e 4g4f 3b3c 4h4g 8b2b 5i6h 5a6b 6h7h 6b7b 9g9f 9c9d 7g7f 3a4b 4f4e 4d8h+ 7i8h 7a6b 8h7g 6a5a 5h6h 3c3d 7h8h 3d4e 2h2f 2a3c 1g1f 1c1d 6i7h 2c2d 2e2d P*2e 2d2c+ 2b2c B*3d 2c2d 3d4e 2e2f 4e5f 2f2g+ G*3d 2d2f 3d3e 2g1h 4g3h 2f2h+ P*2d P*2b 3e3d B*4e 5f4e 3c4e B*5f 1h2i 5f4e 2h3h 2d2c+ 2b2c N*3e R*4i 4e5f 3h3i P*4d 4i8i+ 8h9g 8i9i 9g8f B*9g".split(" ")

        @JvmStatic
        @BeforeClass
        fun setup() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            evalDir = EvalLoader.ensureReady(ctx)
        }
    }

    @Test
    fun endToEndGameAnalysis() = runTest(timeout = Duration.parse("180s")) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val runner = createAndroidAnalysisRunner(ctx.applicationInfo, evalDir, workers = 4)

        val startMs = System.currentTimeMillis()
        var lastProgress = ""

        val results = runner.analyzeGame(GAME_MOVES) { done, total ->
            lastProgress = "$done/$total"
            if (done % 10 == 0 || done == total) {
                val elapsed = (System.currentTimeMillis() - startMs) / 1000.0
                android.util.Log.i("E2ETest", "Progress: $done/$total (${String.format("%.1f", elapsed)}s)")
            }
        }

        val elapsedMs = System.currentTimeMillis() - startMs
        val elapsedSec = elapsedMs / 1000.0

        // 75局面（0〜74手）の結果があること
        assertEquals("全75局面の結果", GAME_MOVES.size + 1, results.size)
        results.forEachIndexed { i, pvList ->
            assertTrue("局面$i に結果がある", pvList.isNotEmpty())
        }

        android.util.Log.i("E2ETest", "=== E2E RESULT: ${GAME_MOVES.size}手 / ${results.size}局面 / ${String.format("%.1f", elapsedSec)}秒 ===")

        val bundle = android.os.Bundle()
        bundle.putString("e2e_elapsed_sec", String.format("%.1f", elapsedSec))
        bundle.putInt("e2e_positions", results.size)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)

        // 目標 60秒以内（実機スパイクの知見: 92局面≒35秒、75局面は余裕のはず）
        assertTrue("解析時間が90秒以内（目標60秒）", elapsedSec < 90.0)
        println("=== E2E完了: ${results.size}局面 / ${String.format("%.1f", elapsedSec)}秒 ===")
    }
}
