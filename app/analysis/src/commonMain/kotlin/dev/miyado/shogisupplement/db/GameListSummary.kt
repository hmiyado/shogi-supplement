package dev.miyado.shogisupplement.db

/**
 * 絞り込んだ棋譜の集合の成績。
 *
 * 割合は標本が小さいと1局の増減で大きく振れる。分母を割合と必ず並べて出し、分母が
 * [MIN_DECIDED_GAMES] / [MIN_USER_MOVES] に満たないときは割合そのものを出さない。
 */
data class GameListSummary(
    val games: Int,
    /** 勝敗が判る局数。引き分け・不明はここに入らない。 */
    val decidedGames: Int,
    val wins: Int,
    val blunders: Int,
    /** 悪手率の分母。自分の手数の合計。 */
    val userMoves: Int,
    /** 推定棋力の材料になった局のレート。空なら推定を出さない。 */
    val ratings: List<Int>,
) {
    val winRatePct: Int?
        get() = if (decidedGames >= MIN_DECIDED_GAMES) wins * 100 / decidedGames else null

    val blunderRatePct: Int?
        get() = if (userMoves >= MIN_USER_MOVES) blunders * 100 / userMoves else null

    companion object {
        /** 4局以下だと1局の勝敗で25ポイント以上動くため、割合として読ませない。 */
        const val MIN_DECIDED_GAMES = 5

        /** 1局ぶん（[dev.miyado.shogisupplement.judge.Judge]のfallbackが55手）に満たない分母では出さない。 */
        const val MIN_USER_MOVES = 50
    }
}

/** 総手数から自分の手数を出す。先手は初手を持つぶん1手多い。 */
internal fun userMoveCount(totalMoves: Long, userSide: String): Int {
    val t = totalMoves.toInt()
    return if (userSide == "sente") (t + 1) / 2 else t / 2
}

/**
 * @param blunderCounts 棋譜IDごとの悪手件数。渡された分だけを分子に数える。
 */
fun List<GameRecord>.summarize(blunderCounts: Map<Long, Int>): GameListSummary {
    var decided = 0
    var wins = 0
    var blunders = 0
    var userMoves = 0
    val ratings = mutableListOf<Int>()
    for (game in this) {
        val side = game.userSide ?: continue
        // 未解析の棋譜は悪手も手数も確定していない。混ぜると分母だけが増えて率が下がる。
        if (game.analysisStatus != GameAnalysisStatus.COMPLETED) continue
        userMoves += userMoveCount(game.moveCount, side)
        blunders += blunderCounts[game.id] ?: 0
        ratings += game.rating.toInt()
        val winner = game.gameWinner ?: continue
        decided++
        if (winner == side) wins++
    }
    return GameListSummary(
        games = size,
        decidedGames = decided,
        wins = wins,
        blunders = blunders,
        userMoves = userMoves,
        ratings = ratings,
    )
}
