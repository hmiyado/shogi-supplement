package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.UsiEngineSubprocess
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsiEmptyPvTest {

    private val fakeEngineScript = """
        #!/bin/sh
        go_count=0
        while IFS= read -r line; do
          case "${'$'}line" in
            usi) printf 'usiok\n' ;;
            isready) printf 'readyok\n' ;;
            go*)
              go_count=${'$'}((go_count + 1))
              if [ "${'$'}go_count" -eq 1 ]; then
                printf 'info depth 0 multipv 1 score mate 0 mateverified nodes 0 pv \n'
              else
                printf 'info depth 5 multipv 1 score cp 12 nodes 100 pv 7g7f   2g2f  8c8d\n'
              fi
              printf 'bestmove 7g7f\n'
              ;;
          esac
        done
    """.trimIndent()

    @Test
    fun analyzeKeepsEmptyPvEmptyAndPreservesVerifiedMate0() {
        val script = File.createTempFile("usi-empty-pv", ".sh")
        try {
            script.writeText(fakeEngineScript)
            assertTrue(script.setExecutable(true), "fake USI responder must be executable")
            val engine = UsiEngineSubprocess.create(script.absolutePath, evalDir = "/eval")
            try {
                val emptyResult = engine.analyze(emptyList(), nodes = 1)
                assertEquals(1, emptyResult.size)
                assertEquals(1, emptyResult[0].multipv)
                assertEquals(Score.Mate(0, verified = true), emptyResult[0].score)
                assertEquals(0L, emptyResult[0].nodes)
                assertTrue(emptyResult[0].pv.isEmpty(), "empty PV must stay empty, was ${emptyResult[0].pv}")

                val normalResult = engine.analyze(listOf("7g7f"), nodes = 1)
                assertEquals(1, normalResult.size)
                assertEquals(Score.Cp(12), normalResult[0].score)
                assertEquals(listOf("7g7f", "2g2f", "8c8d"), normalResult[0].pv)
            } finally {
                engine.quit()
            }
        } finally {
            script.delete()
        }
    }
}
