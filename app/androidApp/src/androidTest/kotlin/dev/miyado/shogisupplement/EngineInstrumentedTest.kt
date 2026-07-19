package dev.miyado.shogisupplement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.createAndroidAnalysisRunner
import dev.miyado.shogisupplement.engine.EvalLoader
import dev.miyado.shogisupplement.engine.UsiEngineProcess
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EngineInstrumentedTest {

    companion object {
        private lateinit var evalDir: File

        @JvmStatic
        @BeforeClass
        fun setup() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            evalDir = EvalLoader.ensureReady(ctx)
        }
    }

    /** 初期局面の解析でスコアが返ること */
    @Test
    fun testAnalyzeInitialPosition() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = UsiEngineProcess.create(ctx.applicationInfo, evalDir)
        try {
            val result = engine.analyze(emptyList())
            assertTrue("MultiPVが少なくとも1つ返る", result.isNotEmpty())
            val pv1 = result.first()
            assertNotNull("scoreが非null", pv1.score)
            assertFalse("pvが空でない", pv1.pv.isEmpty())
            println("初期局面スコア: ${pv1.score} pv=${pv1.pv.take(3)}")
        } finally {
            engine.quit()
        }
    }

    /** 数局面を連続解析してスコアが返ること */
    @Test
    fun testAnalyzeMultiplePositions() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = UsiEngineProcess.create(ctx.applicationInfo, evalDir)
        // 実際の棋譜の冒頭3手
        val moves = listOf("2g2f", "3c3d", "2f2e")
        try {
            // 0〜3手目の4局面を解析
            for (i in 0..moves.size) {
                val prefix = moves.take(i)
                val result = engine.analyze(prefix)
                assertTrue("局面$i: 結果が返る", result.isNotEmpty())
                val score = result.first().score
                assertNotNull("局面$i: scoreが非null", score)
                println("局面$i score=$score")
            }
        } finally {
            engine.quit()
        }
    }

    /** AnalysisRunner で並列解析（4局面）の動作確認 */
    @Test
    fun testAnalysisRunnerParallel() = runTest(timeout = kotlin.time.Duration.parse("120s")) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val runner = createAndroidAnalysisRunner(ctx.applicationInfo, evalDir, workers = 4)
        val moves = listOf("2g2f", "3c3d", "2f2e")
        val results = runner.analyzeGame(moves) { done, total ->
            println("Progress: $done/$total")
        }
        assertTrue("全局面分の結果が返る", results.size == moves.size + 1)
        results.forEachIndexed { i, pvList ->
            assertTrue("局面$i: MultiPV結果あり", pvList.isNotEmpty())
            val score = pvList.first().score
            assertNotNull("局面$i: scoreあり", score)
            println("局面$i: score=$score pv=${pvList.first().pv.take(3)}")
        }
    }
}
