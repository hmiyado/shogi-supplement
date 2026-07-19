@file:OptIn(ExperimentalForeignApi::class)

package dev.miyado.shogisupplement.engine

import dev.miyado.shogisupplement.blunder.Score
import dev.miyado.shogisupplement.engine.wrapper.shogi_engine_read_line
import dev.miyado.shogisupplement.engine.wrapper.shogi_engine_send
import dev.miyado.shogisupplement.engine.wrapper.shogi_engine_start
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString

/** shogi_engine_read_line 1回あたりのポーリング上限（ms）。タイムアウトしたら内部で読み続ける。 */
private const val READ_POLL_TIMEOUT_MS = 1000

/** shogi_engine_read_line の出力バッファ容量（バイト）。infoのpvが長くても十分な余裕を持たせる。 */
private const val READ_BUF_CAPACITY = 8192

/**
 * iOS用Engine実装。
 *
 * YaneuraOu本体をcinterop経由（app/iosApp/engine/wrapper/engine_wrapper.h、実体は
 * libshogiengine.a = wrapper.cpp + libyaneuraou.a をbuild_ios.shがマージしたもの）で
 * プロセス内スレッドとして起動する。Android版 UsiEngineProcess（別プロセスexec）と異なり、
 * エンジンはアプリと同一プロセスのスレッドとして動作し続ける点に注意。
 *
 * 制約（重要）:
 * - `shogi_engine_start()` はプロセス内で一度しか呼べない（C側にも二重起動ガードがあるが、
 *   Kotlin側の [Companion.create] でも2回目の呼び出しを弾く）。
 * - [quit] は "quit" コマンドを送信するのみ。C wrapper側の設計として、エンジンスレッドは
 *   `std::thread(...).detach()` されており、Kotlin/Swift側からそのスレッドを終了させる
 *   手段が無い。そのため [quit] 後に [Companion.create] を再度呼んでもエンジンは
 *   再起動できない（アプリプロセスの再起動が必要）。ラップ側で再起動に対応する余地は
 *   将来的にあるが、現状はこの制約を前提にホルダー側（[IosEngineHost]）で
 *   単一インスタンスを使い回す設計にしている。
 * - engine_wrapper.h の制約により、`shogi_engine_start()` 呼び出し後はプロセスのfd0/fd1が
 *   エンジン用パイプに専有される。アプリ側の標準出力/標準入力はそれ以降使用禁止
 *   （このファイル・呼び出し側のログはprintln等ではなくOSLog/NSLog等を使うこと）。
 * - [analyze]/[analyzeSfen]/[quit] の呼び出しは呼び出し側で直列に行うこと
 *   （engine_wrapper.h側のsend/read_lineは複数箇所からの同時呼び出しを想定していない）。
 *
 * 解析条件の不変条件はAndroid版 UsiEngineProcess.create と同一
 * （Threads=1 / USI_Hash=128 / MultiPV=2 / FV_SCALE=20）。
 */
class UsiEngineInProcess private constructor() : Engine {

    companion object {
        private var started = false

        /**
         * エンジンスレッドを起動し、USIハンドシェイクを完了させて返す。
         * プロセス内で一度しか呼べない（2回目はIllegalStateExceptionを投げる。[quit] を
         * 呼んだ後でも再度このメソッドで再起動することはできない。クラスKDoc参照）。
         *
         * @param evalDir EvalDirへ渡す絶対パス（バンドル同梱eval配下のディレクトリ）
         */
        fun create(evalDir: String): UsiEngineInProcess {
            check(!started) {
                "UsiEngineInProcess.create() はプロセス内で一度しか呼べません" +
                    "（quit後の再startも不可。クラスKDoc参照）"
            }
            started = true

            shogi_engine_start()
            val engine = UsiEngineInProcess()

            engine.send("usi")
            engine.waitFor("usiok")

            // オプション設定（不変条件。Android版 UsiEngineProcess.create と同一）
            engine.send("setoption name Threads value 1")
            engine.send("setoption name USI_Hash value 128")
            engine.send("setoption name MultiPV value 2")
            engine.send("setoption name USI_OwnBook value false")
            engine.send("setoption name NetworkDelay value 0")
            engine.send("setoption name NetworkDelay2 value 0")
            engine.send("setoption name FV_SCALE value 20")
            engine.send("setoption name EvalDir value $evalDir")

            engine.send("isready")
            engine.waitFor("readyok")
            engine.send("usinewgame")

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

    /**
     * "quit" コマンドを送信するのみ。エンジンスレッドをdestroyする手段は無い
     * （プロセス内スレッドのため。クラスKDocの制約参照）。呼び出し後、このインスタンスの
     * 再利用・再startは不可。
     */
    override fun quit() {
        send("quit")
    }

    /**
     * "usinewgame" を送信する。iOS はプロセス内で一度しか起動できず
     * 局ごとに [quit] できないため、局の区切りはこのメソッドで表現する
     * （[dev.miyado.shogisupplement.engine.IosEngineHost] の局ごとのエンジンファクトリから呼ばれる）。
     */
    override fun newGame() {
        send("usinewgame")
    }

    // ---- 内部ヘルパー（parseInfoLine/collectPvResultはAndroid版 UsiEngineProcess と同一ロジック） ----

    /** go nodes の結果を bestmove まで収集して返す（analyze/analyzeSfen 共通）。 */
    private fun collectPvResult(): List<PvInfo> {
        val pvMap = mutableMapOf<Int, PvInfo>()
        while (true) {
            val line = readLineBlocking()
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

    private fun send(cmd: String) {
        lastCommandName = cmd.substringBefore(' ')
        shogi_engine_send(cmd)
    }

    private fun waitFor(token: String) {
        while (true) {
            val line = readLineBlocking()
            if (line.trim() == token) return
        }
    }

    /**
     * 1行読めるまで [READ_POLL_TIMEOUT_MS] 刻みでポーリングを繰り返す。
     * shogi_engine_read_line がEOF（パイプクローズ）を返したら例外を投げる。
     */
    private fun readLineBlocking(): String {
        while (true) {
            val line = memScoped {
                val buf = allocArray<ByteVar>(READ_BUF_CAPACITY)
                val n = shogi_engine_read_line(buf, READ_BUF_CAPACITY, READ_POLL_TIMEOUT_MS)
                when {
                    n >= 0 -> buf.toKString()
                    n == -2 -> throw IllegalStateException(
                        "Engine pipe closed unexpectedly (EOF); lastCommandName=$lastCommandName",
                    )
                    else -> null // -1: タイムアウト。呼び出し元のwhileループでリトライする
                }
            }
            if (line != null) return line
        }
    }

    /** 直前に送信した USI コマンド名（"go"/"position" など。内容は含まない）。デバッグ用。 */
    private var lastCommandName: String = ""

    /**
     * USI info 行をパース。
     * `info depth ... multipv N score cp/mate V pv ...` を PvInfo に変換する。
     *
     * multipv が無い行（lowerbound / upperbound 行など）は null を返す。
     * Android版 UsiEngineProcess.parseInfoLine と同一ロジック。
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
