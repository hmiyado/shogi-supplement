package dev.miyado.shogisupplement.engine

import android.content.pm.ApplicationInfo
import android.util.Log
import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.PvInfo
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * Android用Engine実装。
 *
 * nativeLibraryDir の libyaneuraou_usi.so を ProcessBuilder で exec し、
 * USI プロトコルで通信する。
 *
 * 解析条件（不変条件）:
 * - Threads=1 / USI_Hash=128MB / MultiPV=2 / FV_SCALE=20
 * - go nodes 400000
 */
class UsiEngineProcess private constructor(
    private val process: Process,
    private val reader: BufferedReader,
    private val writer: PrintWriter,
) : Engine {

    companion object {
        private const val TAG = "UsiEngineProcess"

        /**
         * エンジンプロセスを起動し、USIハンドシェイクを完了させて返す。
         *
         * @param appInfo ApplicationInfo（nativeLibraryDir 取得用）
         * @param evalDir EvalDir（filesDir/eval）の絶対パス
         */
        fun create(appInfo: ApplicationInfo, evalDir: File): UsiEngineProcess {
            val soPath = File(appInfo.nativeLibraryDir, "libyaneuraou_usi.so")
            require(soPath.exists()) { "エンジンバイナリが見つかりません: ${soPath.absolutePath}" }

            Log.d(TAG, "Starting engine: ${soPath.absolutePath}")

            val process = ProcessBuilder(soPath.absolutePath)
                .redirectErrorStream(false)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val writer = PrintWriter(process.outputStream, /* autoFlush= */ true)

            val engine = UsiEngineProcess(process, reader, writer)

            // USIハンドシェイク
            engine.send("usi")
            engine.waitFor("usiok")

            // オプション設定（不変条件）
            engine.send("setoption name Threads value 1")
            engine.send("setoption name USI_Hash value 128")
            engine.send("setoption name MultiPV value 2")
            engine.send("setoption name USI_OwnBook value false")
            engine.send("setoption name NetworkDelay value 0")
            engine.send("setoption name NetworkDelay2 value 0")
            engine.send("setoption name FV_SCALE value 20")
            engine.send("setoption name EvalDir value ${evalDir.absolutePath}")

            engine.send("isready")
            engine.waitFor("readyok")
            engine.send("usinewgame")

            Log.d(TAG, "Engine ready")
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

    /**
     * "usinewgame" を送信する。
     * Android は毎局 [UsiEngineProcess.create] で新規プロセスを起動しており、
     * create() 内で既に usinewgame 送信済みのため通常は呼ばれない
     * （AnalysisOrchestrator は局ごとに新規プロセスを作るAndroid用エンジンファクトリでは
     * このメソッドを呼ばない）。念のため実装しておく。
     */
    override fun newGame() {
        send("usinewgame")
    }

    // ---- 内部ヘルパー ----

    /** 直前に送信した USI コマンド名（"go"/"position" など。内容は含まない）。 */
    private var lastCommandName: String = ""

    private fun send(cmd: String) {
        Log.v(TAG, ">> $cmd")
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
            Log.v(TAG, "<< $line")
            if (line.trim() == token) return
        }
    }

    /** エンジンプロセスの終了コードを取得する。プロセスがまだ動いていれば null を返す。 */
    private fun tryGetExitCode(): Int? = try {
        process.exitValue()
    } catch (_: IllegalThreadStateException) {
        null
    }

    /**
     * USI info 行をパース。
     * `info depth ... multipv N score cp/mate V pv ...` を PvInfo に変換する。
     *
     * multipv が無い行（lowerbound / upperbound 行など）は null を返す。
     */
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
