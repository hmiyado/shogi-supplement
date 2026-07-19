package dev.miyado.shogisupplement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.miyado.shogisupplement.engine.EvalLoader
import dev.miyado.shogisupplement.engine.UsiEngineProcess
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter

@RunWith(AndroidJUnit4::class)
class DiagnosticTest {

    companion object {
        private lateinit var evalDir: File

        private val ALL_MOVES = "2g2f 3c3d 2f2e 2b4d 3i4h 4a3b 4i5h 3d3e 4g4f 3b3c 4h4g 8b2b 5i6h 5a6b 6h7h 6b7b 9g9f 9c9d 7g7f 3a4b 4f4e 4d8h+ 7i8h 7a6b 8h7g 6a5a 5h6h 3c3d 7h8h 3d4e 2h2f 2a3c 1g1f 1c1d 6i7h 2c2d 2e2d P*2e 2d2c+ 2b2c B*3d 2c2d 3d4e 2e2f 4e5f 2f2g+ G*3d 2d2f 3d3e 2g1h 4g3h 2f2h+ P*2d P*2b 3e3d B*4e 5f4e 3c4e B*5f 1h2i 5f4e 2h3h 2d2c+ 2b2c N*3e R*4i 4e5f 3h3i P*4d 4i8i+ 8h9g 8i9i 9g8f B*9g".split(" ")

        @JvmStatic
        @BeforeClass
        fun setup() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            evalDir = EvalLoader.ensureReady(ctx)
        }
    }

    /** 局面70を単独で解析してエンジンの生の出力を確認 */
    @Test
    fun diagnosePosition70() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val appInfo = ctx.applicationInfo
        val soPath = java.io.File(appInfo.nativeLibraryDir, "libyaneuraou_usi.so")

        val process = ProcessBuilder(soPath.absolutePath)
            .redirectErrorStream(false)
            .start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val writer = PrintWriter(process.outputStream, true)

        fun send(s: String) { writer.println(s) }
        fun waitFor(token: String) {
            while (true) {
                val l = reader.readLine() ?: error("engine closed")
                if (l.trim() == token) return
            }
        }

        send("usi"); waitFor("usiok")
        send("setoption name Threads value 1")
        send("setoption name USI_Hash value 128")
        send("setoption name MultiPV value 2")
        send("setoption name USI_OwnBook value false")
        send("setoption name NetworkDelay value 0")
        send("setoption name NetworkDelay2 value 0")
        send("setoption name FV_SCALE value 20")
        send("setoption name EvalDir value ${evalDir.absolutePath}")
        send("isready"); waitFor("readyok")
        send("usinewgame")

        val prefix70 = ALL_MOVES.take(70)
        send("position startpos moves ${prefix70.joinToString(" ")}")
        send("go nodes 400000")

        val lines = mutableListOf<String>()
        while (true) {
            val l = reader.readLine() ?: break
            lines.add(l)
            android.util.Log.i("Diagnostic", "RAW: $l")
            if (l.startsWith("bestmove")) break
        }

        send("quit"); process.destroy()

        println("=== Position 70 raw output ===")
        lines.forEach { println(it) }
        println("Total lines: ${lines.size}")
    }
}
