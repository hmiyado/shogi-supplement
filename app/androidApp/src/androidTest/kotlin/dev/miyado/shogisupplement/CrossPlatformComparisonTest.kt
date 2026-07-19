package dev.miyado.shogisupplement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.EvalLoader
import dev.miyado.shogisupplement.engine.PvInfo
import dev.miyado.shogisupplement.engine.UsiEngineProcess
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * クロスプラットフォーム一致検証テスト。
 * miyado_game1.kif の最初の10局面を実機で解析し、Logcatに出力する。
 * Mac側の結果と照合するために使用する。
 */
@RunWith(AndroidJUnit4::class)
class CrossPlatformComparisonTest {

    companion object {
        private lateinit var evalDir: File

        // miyado_game1.kif の USI 手列（74手）
        private val ALL_MOVES = "2g2f 3c3d 2f2e 2b4d 3i4h 4a3b 4i5h 3d3e 4g4f 3b3c 4h4g 8b2b 5i6h 5a6b 6h7h 6b7b 9g9f 9c9d 7g7f 3a4b 4f4e 4d8h+ 7i8h 7a6b 8h7g 6a5a 5h6h 3c3d 7h8h 3d4e 2h2f 2a3c 1g1f 1c1d 6i7h 2c2d 2e2d P*2e 2d2c+ 2b2c B*3d 2c2d 3d4e 2e2f 4e5f 2f2g+ G*3d 2d2f 3d3e 2g1h 4g3h 2f2h+ P*2d P*2b 3e3d B*4e 5f4e 3c4e B*5f 1h2i 5f4e 2h3h 2d2c+ 2b2c N*3e R*4i 4e5f 3h3i P*4d 4i8i+ 8h9g 8i9i 9g8f B*9g".split(" ")

        @JvmStatic
        @BeforeClass
        fun setup() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            evalDir = EvalLoader.ensureReady(ctx)
        }
    }

    @Test
    fun analyzeTenPositionsAndPrintResults() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = UsiEngineProcess.create(ctx.applicationInfo, evalDir)
        val TAG = "CrossPlatform"
        val sb = StringBuilder()
        sb.appendLine("=== Android engine results (first 10 positions) ===")

        try {
            for (i in 0 until 10) {
                val prefix = ALL_MOVES.take(i)
                val pvList = engine.analyze(prefix)
                val pv1 = pvList.firstOrNull()
                val pv2 = pvList.getOrNull(1)
                val bestmove = pv1?.pv?.firstOrNull() ?: "none"
                val scoreStr = pv1?.let { scoreJson(it) } ?: "null"
                val score2Str = pv2?.let { scoreJson(it) } ?: "null"
                val line = "局面$i: bestmove=$bestmove score=$scoreStr score2=$score2Str"
                sb.appendLine(line)
                android.util.Log.i(TAG, line)
            }
        } finally {
            engine.quit()
        }

        // テスト出力をLogcatに書く（instrumentation result で取得）
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bundle = android.os.Bundle()
        bundle.putString("android_engine_results", sb.toString())
        instrumentation.sendStatus(0, bundle)

        println(sb.toString())
    }

    private fun scoreJson(pv: PvInfo): String = when (val sc = pv.score) {
        is Score.Cp -> "{cp: ${sc.value}}"
        is Score.Mate -> "{mate: ${sc.plies}}"
    }
}
