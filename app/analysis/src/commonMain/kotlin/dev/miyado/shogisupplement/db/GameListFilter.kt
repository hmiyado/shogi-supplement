package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.kifu.KifuSource

enum class GameResultFilter {
    WIN,
    LOSS,
}

/**
 * 棋譜一覧の絞り込み条件。
 * 各軸はnull（=不問）ならその条件を適用しない。指定されている軸同士はAND結合する。
 */
data class GameListFilter(
    val source: String? = null,
    /** ユーザーの先後（"sente"/"gote"）。 */
    val userSide: String? = null,
    val result: GameResultFilter? = null,
    /** 解析日時の下限（epoch秒・含む）。 */
    val dateFrom: Long? = null,
) {
    /** 指定された絞り込み軸の数を返す。 */
    val activeCount: Int
        get() = listOfNotNull(source, userSide, result, dateFrom).size

    val isActive: Boolean
        get() = activeCount > 0
}

/** filterの条件をAND結合でゲームレコードへ適用する。勝敗情報が欠けたレコードは勝敗条件から除外する。 */
fun List<GameRecord>.filterGames(filter: GameListFilter): List<GameRecord> {
    if (!filter.isActive) return this
    return filter { game ->
        matchesSource(game, filter.source) &&
            matchesUserSide(game, filter.userSide) &&
            matchesResult(game, filter.result) &&
            matchesDateFrom(game, filter.dateFrom)
    }
}

private fun matchesSource(game: GameRecord, source: String?): Boolean =
    source == null || game.sourcePlace == source

private fun matchesUserSide(game: GameRecord, userSide: String?): Boolean =
    userSide == null || game.userSide == userSide

private fun matchesResult(game: GameRecord, result: GameResultFilter?): Boolean {
    if (result == null) return true
    val userSide = game.userSide ?: return false
    val winner = game.gameWinner ?: return false
    return when (result) {
        GameResultFilter.WIN -> winner == userSide
        GameResultFilter.LOSS -> winner != userSide
    }
}

private fun matchesDateFrom(game: GameRecord, dateFrom: Long?): Boolean =
    dateFrom == null || game.analyzedAt >= dateFrom

/**
 * 一覧に実在するsource_place（正規化値）を順序を保って重複なく返す。
 * フィルタチップは実データに現れる値のみ表示する（存在しない軸を作らないため）。
 */
fun List<GameRecord>.distinctSources(): List<String> {
    val present = mapNotNull { it.sourcePlace }.toSet()
    return KifuSource.entries.map { it.wireValue }.filter { it in present }
}

fun List<GameRecord>.hasUserSideData(): Boolean = any { it.userSide != null }

fun List<GameRecord>.hasResultData(): Boolean = any { it.userSide != null && it.gameWinner != null }
