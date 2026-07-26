package dev.miyado.shogisupplement.server.worker.engine

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.EngineAbnormalExitException
import dev.miyado.shogisupplement.engine.PvInfo
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * Cloud Run（Linux/JVM）用のEngine実装。ProcessBuilderでUSIエンジンバイナリを1本起動し、
 * USIプロトコルで通信する。
 *
 * Why not androidApp の `UsiEngineProcess` を再利用: android.content.pm.ApplicationInfo に
 * 依存しておりAndroid Gradle Pluginを引き込むため、このモジュールは複製して持つ。
 *
 * 解析条件（不変条件）は [EngineInvariants] を単一の真実源とする。
 */
class WorkerUsiEngineProcess private constructor(
    private val process: Process,
    private val reader: BufferedReader,
    private val writer: PrintWriter,
) : Engine {

    companion object {
        private val log = LoggerFactory.getLogger(WorkerUsiEngineProcess::class.java)

        /**
         * エンジンプロセスを起動し、USIハンドシェイクと不変条件のsetoptionを完了させて返す。
         *
         * @param enginePath USIエンジンバイナリの絶対パス
         * @param evalDir EvalDirの絶対パス（eval_hao）
         */
        fun create(enginePath: String, evalDir: String): WorkerUsiEngineProcess {
            log.info("Starting engine: {}", enginePath)

            val process = ProcessBuilder(enginePath)
                .redirectErrorStream(false)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val writer = PrintWriter(process.outputStream, /* autoFlush= */ true)

            val engine = WorkerUsiEngineProcess(process, reader, writer)

            engine.send("usi")
            engine.waitFor("usiok")

            engine.send("setoption name Threads value ${EngineInvariants.THREADS}")
            engine.send("setoption name USI_Hash value ${EngineInvariants.USI_HASH_MB}")
            engine.send("setoption name MultiPV value ${EngineInvariants.MULTI_PV}")
            engine.send("setoption name USI_OwnBook value false")
            engine.send("setoption name NetworkDelay value 0")
            engine.send("setoption name NetworkDelay2 value 0")
            engine.send("setoption name FV_SCALE value ${EngineInvariants.FV_SCALE}")
            engine.send("setoption name EvalDir value $evalDir")

            engine.send("isready")
            engine.waitFor("readyok")
            engine.send("usinewgame")

            log.info("Engine ready")
            return engine
        }
    }

    override fun analyze(moves: List<String>, nodes: Int): List<PvInfo> {
        val posCmd = if (moves.isEmpty()) {
            "position startpos"
        } else {
            "position startpos moves ${moves.joinToString(" ")}"
        }
        send(posCmd)
        send("go nodes $nodes")
        return collectPvResult()
    }

    override fun analyzeSfen(sfen: String, additionalMoves: List<String>, nodes: Int): List<PvInfo> {
        val posCmd = if (additionalMoves.isEmpty()) {
            "position sfen $sfen"
        } else {
            "position sfen $sfen moves ${additionalMoves.joinToString(" ")}"
        }
        send(posCmd)
        send("go nodes $nodes")
        return collectPvResult()
    }

    private fun collectPvResult(): List<PvInfo> {
        val pvMap = mutableMapOf<Int, PvInfo>()
        while (true) {
            val line = reader.readLine()
                ?: throw EngineAbnormalExitException(
                    message = "Engine stdout closed unexpectedly",
                    exitCode = tryGetExitCode(),
                    lastCommandName = lastCommandName,
                )
            when {
                line.startsWith("bestmove") -> break
                line.startsWith("info ") -> {
                    val pv = parseInfoLine(line) ?: continue
                    pvMap[pv.multipv] = pv
                }
            }
        }
        return pvMap.entries.sortedBy { it.key }.map { it.value }
    }

    override fun quit() {
        try {
            send("quit")
        } catch (_: Exception) {
        } finally {
            process.destroy()
        }
    }

    override fun newGame() {
        send("usinewgame")
    }

    private var lastCommandName: String = ""

    private fun send(cmd: String) {
        log.debug(">> {}", cmd)
        lastCommandName = cmd.substringBefore(' ')
        writer.println(cmd)
    }

    private fun waitFor(token: String) {
        while (true) {
            val line = reader.readLine()
                ?: throw EngineAbnormalExitException(
                    message = "Engine closed before receiving '$token'",
                    exitCode = tryGetExitCode(),
                    lastCommandName = lastCommandName,
                )
            log.debug("<< {}", line)
            if (line.trim() == token) return
        }
    }

    private fun tryGetExitCode(): Int? = try {
        process.exitValue()
    } catch (_: IllegalThreadStateException) {
        null
    }

    /** `info depth ... multipv N score cp/mate V pv ...` を [PvInfo] に変換する。 */
    private fun parseInfoLine(line: String): PvInfo? {
        val toks = line.split(" ")
        var i = 1 // "info" をスキップ
        var multipv: Int? = null
        var score: Score? = null
        var pvList: List<String> = emptyList()
        var nodes: Long = 0L

        while (i < toks.size) {
            when (toks[i]) {
                "multipv" -> {
                    multipv = toks.getOrNull(i + 1)?.toIntOrNull()
                    i += 2
                }
                "score" -> {
                    val kind = toks.getOrNull(i + 1)
                    val value = toks.getOrNull(i + 2)?.toIntOrNull()
                    score = when {
                        kind == "cp" && value != null -> Score.Cp(value)
                        kind == "mate" && value != null -> Score.Mate(value)
                        else -> null
                    }
                    i += 3
                }
                "nodes" -> {
                    nodes = toks.getOrNull(i + 1)?.toLongOrNull() ?: 0L
                    i += 2
                }
                "pv" -> {
                    pvList = toks.drop(i + 1)
                    break
                }
                else -> i++
            }
        }

        val sc = score ?: return null
        val mp = multipv ?: if (pvList.isNotEmpty()) 1 else return null
        return PvInfo(multipv = mp, score = sc, pv = pvList, nodes = nodes)
    }
}
