package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.PieceType
import dev.miyado.shogisupplement.board.ShogiSquare
import dev.miyado.shogisupplement.board.Side

/**
 * 自分視点に正規化したマス。先手の2八と後手の8二が同じ値になり、
 * 先手・後手で同じ定義を使える。
 */
data class BfSquare(val file: Int, val rank: Int) {

    fun toSquare(side: Side): ShogiSquare =
        if (side == Side.BLACK) ShogiSquare(file, rank) else ShogiSquare(10 - file, 10 - rank)
}

data class PiecePlacement(val type: PieceType, val square: BfSquare)

enum class OpeningKind { CASTLE, STRATEGY }

/** 手順の例。テストの検証と資料の掲載に同じものを使う。 */
data class OpeningSample(val label: String, val usiMoves: List<String>, val matches: Boolean)

/**
 * 駒の配置で判定する形。一度成立したら、その後崩れてもその対局の記録として残す。
 *
 * @param plyCap これ以降は判定しない手数。終盤の玉の逃避行で偶然一致する形を拾わないため。
 * @param developsFrom より未発達の形。判定には使わず（それぞれ独立に成立を見る）、
 *   両方成立したときにどちらを表示するかだけを決める。
 * @param conditions 駒の配置に加えて満たす条件。判定にも資料にも同じ宣言を使う。
 */
data class PlacementDef(
    val name: String,
    val slug: String,
    val kind: OpeningKind,
    val required: List<PiecePlacement>,
    val empty: List<BfSquare> = emptyList(),
    val forbidden: List<PiecePlacement> = emptyList(),
    val plyCap: Int,
    val developsFrom: String? = null,
    val conditions: List<OpeningCondition> = emptyList(),
    val source: String,
    val samples: List<OpeningSample> = emptyList(),
)

/** タグを誰に付けるか。 */
enum class TagScope {
    /** 対局単位で決まる戦型。条件を満たしたら両者に付ける。 */
    BOTH_SIDES,

    /** その側の指し方を指す戦型。条件を満たした側にだけ付ける。 */
    MATCHING_SIDE,
}

/**
 * 序盤の出来事で判定する戦型。駒の配置では表せない（角が盤上から消えたか、
 * 飛車で歩を取ったか、が定義の核）ため、条件の並びとして宣言する。
 */
data class EventStrategyDef(
    val name: String,
    val slug: String,
    val scope: TagScope,
    val conditions: List<OpeningCondition>,
    val source: String,
    val samples: List<OpeningSample> = emptyList(),
)

internal fun usi(moves: String): List<String> = moves.split(" ").filter { it.isNotBlank() }
