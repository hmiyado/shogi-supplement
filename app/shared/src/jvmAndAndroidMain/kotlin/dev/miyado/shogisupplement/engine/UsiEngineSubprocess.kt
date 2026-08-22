package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * USIエンジンをサブプロセスで実行するEngine実装。
 * Androidとserverで実装を共有する。解析条件はEngineInvariantsを単一の定義とする。
 * Why not 実装を複製しない: 起動、ハンドシェイク、info行パースの乖離を防ぐため。
 */
class UsiEngineSubprocess private constructor(
    private val process: Process,
    private val reader: BufferedReader,
    private val writer: PrintWriter,
    private val logIo: (String) -> Unit,
) : Engine {

    companion object {
        /** エンジンを起動し、不変条件を設定する。 @param enginePath エンジンの絶対パス。 @param evalDir EvalDirの絶対パス。 @param logLifecycle ライフサイクルログ。 @param logIo USI行ログ。 */
        fun create(
            enginePath: String,
            evalDir: String,
            logLifecycle: (String) -> Unit = {},
            logIo: (String) -> Unit = {},
        ): UsiEngineSubprocess {
            logLifecycle("Starting engine: $enginePath")

            val process = ProcessBuilder(enginePath)
                .redirectErrorStream(false)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val writer = PrintWriter(process.outputStream, /* autoFlush= */ true)

            val engine = UsiEngineSubprocess(process, reader, writer, logIo)

            // USIハンドシェイク
            engine.send("usi")
            engine.waitFor("usiok")

            // オプション設定（不変条件）
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

            logLifecycle("Engine ready")
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

    /** go nodes の結果を bestmove まで収集して返す（analyze/analyzeSfen 共通）。 */
    private fun collectPvResult(): List<PvInfo> {
        // info行を収集し、bestmove が来たら終了
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
        // usinewgameだけでは置換表・履歴が残り、直前に解析した局面が次の探索に効いてしまう
        // （固定ノード数では「どこまで読めたか」が変わるので結果が変わる）。
        // やねうら王が探索状態を実際にクリアするのはisreadyなので、その順で送る。
        send("isready")
        waitFor("readyok")
        send("usinewgame")
    }

    // ---- 内部ヘルパー ----

    /** 直前に送信した USI コマンド名（"go"/"position" など。内容は含まない）。 */
    private var lastCommandName: String = ""

    private fun send(cmd: String) {
        logIo(">> $cmd")
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
            logIo("<< $line")
            if (line.trim() == token) return
        }
    }

    /** エンジンプロセスの終了コードを取得する。プロセスがまだ動いていれば null を返す。 */
    private fun tryGetExitCode(): Int? = try {
        process.exitValue()
    } catch (_: IllegalThreadStateException) {
        null
    }

    /** USI info行をPvInfoへ変換する。multipvがない行はnullを返す。 */
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
        // multipv が省略される場合（早期詰み確定等）は 1 にフォールバック
        val mp = multipv ?: if (pvList.isNotEmpty()) 1 else return null
        return PvInfo(multipv = mp, score = sc, pv = pvList, nodes = nodes)
    }
}
