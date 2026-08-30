package dev.miyado.shogisupplement.db

import dev.miyado.shogisupplement.kifu.KifuSource
import dev.miyado.shogisupplement.kifu.isKnownTimeControlRule
import dev.miyado.shogisupplement.kifu.timeControlDisplayText
import dev.miyado.shogisupplement.opening.OpeningClassifier

/** 判定表に無い持ち時間をまとめる絞り込み値。表示ラベルはAppStringsが持つ。 */
const val TIME_CONTROL_OTHER = "other"

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
    val openingStyle: String? = null,
    /** 持ち時間の表示文字列（[timeControlDisplayText]の値）。[TIME_CONTROL_OTHER]なら判定表に無いものすべて。 */
    val timeControl: String? = null,
) {
    /** 指定された絞り込み軸の数を返す。 */
    val activeCount: Int
        get() = listOfNotNull(source, userSide, openingStyle, timeControl, result, dateFrom).size

    val isActive: Boolean
        get() = activeCount > 0
}

/** filterの条件をAND結合でゲームレコードへ適用する。勝敗情報が欠けたレコードは勝敗条件から除外する。 */
fun List<GameRecord>.filterGames(filter: GameListFilter): List<GameRecord> {
    if (!filter.isActive) return this
    return filter { game ->
        matchesSource(game, filter.source) &&
            matchesUserSide(game, filter.userSide) &&
            matchesOpeningStyle(game, filter.openingStyle) &&
            matchesTimeControl(game, filter.timeControl) &&
            matchesResult(game, filter.result) &&
            matchesDateFrom(game, filter.dateFrom)
    }
}

private fun matchesSource(game: GameRecord, source: String?): Boolean =
    source == null || game.sourcePlace == source

private fun matchesUserSide(game: GameRecord, userSide: String?): Boolean =
    userSide == null || game.userSide == userSide

private fun matchesOpeningStyle(game: GameRecord, openingStyle: String?): Boolean =
    openingStyle == null || openingStyle in game.openingTagList()

private fun matchesTimeControl(game: GameRecord, timeControl: String?): Boolean =
    timeControl == null || game.timeControlFilterValue() == timeControl

private fun GameRecord.timeControlFilterValue(): String? {
    val display = timeControlDisplayText(sourcePlace, timeControlRaw, timeControlByoyomiRaw) ?: return null
    val known = isKnownTimeControlRule(sourcePlace, timeControlRaw, timeControlByoyomiRaw)
    return if (known) display else TIME_CONTROL_OTHER
}

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

/** 保存済みの棋譜に現れる戦型。代表として出す順（[OpeningClassifier.PRIMARY_STYLE_PRIORITY]）に並べる。 */
fun List<GameRecord>.distinctOpeningStyles(): List<String> {
    val found = flatMap { it.openingTagList() }.distinct()
    val ordered = OpeningClassifier.PRIMARY_STYLE_PRIORITY.filter { it in found }
    return ordered + (found - ordered.toSet()).sorted()
}

/**
 * 保存済みの棋譜に現れる持ち時間。ヘッダを持たない棋譜は含まず、判定表に無いものは
 * [TIME_CONTROL_OTHER] 1件へまとめて末尾に置く。ルールの並びは表示文字列の昇順。
 */
fun List<GameRecord>.distinctTimeControls(): List<String> {
    val values = mapNotNull { it.timeControlFilterValue() }.distinct()
    val rules = values.filterNot { it == TIME_CONTROL_OTHER }.sorted()
    return if (TIME_CONTROL_OTHER in values) rules + TIME_CONTROL_OTHER else rules
}

/** [source]の棋譜に現れる持ち時間。[source]がnullなら全棋譜から返す。 */
fun List<GameRecord>.availableTimeControls(source: String?): List<String> =
    filter { matchesSource(it, source) }.distinctTimeControls()

/** 出典に実在しない持ち時間が指定されていれば外した条件を返す。 */
fun GameListFilter.clearUnavailableTimeControl(games: List<GameRecord>): GameListFilter =
    if (timeControl != null && timeControl !in games.availableTimeControls(source)) {
        copy(timeControl = null)
    } else {
        this
    }
