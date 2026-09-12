package dev.miyado.shogisupplement.engine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/** 解析中の画面が1手進む間隔。 */
internal const val POSITION_REVEAL_INTERVAL_MS = 500L

/** 全局面が揃ったあと、表示が残りに追いつくまでにかける時間。 */
internal const val POSITION_CATCH_UP_BUDGET_MS = 2_000L

/** 追いつきの間隔の上限。解析中より遅くしない。 */
internal const val POSITION_CATCH_UP_MAX_INTERVAL_MS = 100L

/** 追いつきの間隔の下限。60Hzで2.4フレームぶんあり、1手ずつ動いて見える限界。 */
internal const val POSITION_CATCH_UP_MIN_INTERVAL_MS = 40L

/**
 * 局面ごとの解析結果を、ply順に1件ずつ [intervalMs] 間隔で [reveal] へ渡す。
 *
 * [submit] は解析ワーカーのどのスレッドから呼んでもよい。
 * [pace] はキャンセルされるまで戻らず、[revealRemaining] は残りを
 * [catchUpBudgetMs] を目安に出し切る。
 */
internal class PositionRevealPacer(
    private val reveal: (ply: Int, pvs: List<PvInfo>) -> Unit,
    private val intervalMs: Long = POSITION_REVEAL_INTERVAL_MS,
    private val catchUpBudgetMs: Long = POSITION_CATCH_UP_BUDGET_MS,
) {
    // Why not Mutexで守ったMap: submitは解析ワーカーのスレッドから来るため、
    // suspendせずその場で預けられる受け口でなければ解析の足を引っ張る。
    private val arrivals = Channel<Arrival>(Channel.UNLIMITED)
    private val waiting = mutableMapOf<Int, List<PvInfo>>()
    private var nextPly = 0

    private class Arrival(val ply: Int, val pvs: List<PvInfo>)

    fun submit(ply: Int, pvs: List<PvInfo>) {
        arrivals.trySend(Arrival(ply, pvs))
    }

    suspend fun pace(): Nothing {
        while (true) {
            collectArrivals()
            revealNextInOrder()
            delay(intervalMs)
        }
    }

    /**
     * 未通知のものをply順に出し切る。これ以降は[pace]を再開しない。
     * Why not まとめて渡す: 溜まっていた分が盤に1手も映らないまま最終局面へ飛ぶ。
     * Why not 解析中と同じ間隔: 進みを示さず、レポートの表示をただ待たせる。
     */
    suspend fun revealRemaining() {
        collectArrivals()
        // 連続しない残り（欠けた局面があった場合）も落とさない。
        val remaining = waiting.entries.sortedBy { it.key }.map { it.key to it.value }
        waiting.clear()
        val interval = catchUpIntervalFor(remaining.size)
        remaining.forEachIndexed { index, (ply, pvs) ->
            if (index > 0) delay(interval)
            reveal(ply, pvs)
        }
    }

    // Why not 1手あたりの間隔を決め打つ: 残り手数にそのまま比例してレポートの表示が遅れ、
    // 長い棋譜ほど待たされる。かける時間のほうを決め、間隔は見える範囲に収める。
    private fun catchUpIntervalFor(remaining: Int): Long {
        if (remaining <= 1) return 0
        return (catchUpBudgetMs / remaining)
            .coerceIn(POSITION_CATCH_UP_MIN_INTERVAL_MS, POSITION_CATCH_UP_MAX_INTERVAL_MS)
    }

    private fun collectArrivals() {
        while (true) {
            val arrival = arrivals.tryReceive().getOrNull() ?: return
            // Why not 届いたものを無条件に預かる: サーバー解析は切断からの再送で同じ局面を
            // もう一度渡してくるため、出し終えた手を預かると最後の吐き出しで二度出る。
            if (arrival.ply >= nextPly) waiting[arrival.ply] = arrival.pvs
        }
    }

    private fun revealNextInOrder() {
        val pvs = waiting.remove(nextPly) ?: return
        reveal(nextPly, pvs)
        nextPly += 1
    }
}
