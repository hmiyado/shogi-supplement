package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.Side

/** 条件を評価するときに見えるもの。既に決まったタグも見る（前提と排他のため）。 */
data class OpeningContext(
    val events: OpeningEvents,
    val styles: Map<Side, String>,
    val tags: Map<Side, Set<String>>,
    /** 駒の配置で決まる形を見るときの、その形が成立した手数。 */
    val achievementPly: Int? = null,
)

/**
 * 戦型の成立条件。判定（[holds]）と資料の記述（[describe]）が同じ宣言から出るので、
 * 条件を変えれば資料も変わる。文章と実装がずれない。
 */
sealed interface OpeningCondition {
    fun describe(): String
    fun holds(context: OpeningContext, side: Side): Boolean
}

/** 双方が飛車先の歩を5段目まで伸ばし合う。 */
data class RookPawnsPushed(val plyCap: Int, val gapMax: Int) : OpeningCondition {
    override fun describe(): String =
        "双方が${plyCap}手以内に飛車先の歩を5段目（先手2五・後手8五）へ伸ばし、" +
            "その手数差が${gapMax}手以内"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val pushed = context.events.rookPawnsPushedPly ?: return false
        val gap = context.events.rookPawnPushGap ?: return false
        return pushed <= plyCap && gap <= gapMax
    }
}

/** 飛車先を伸ばし切った時点で双方の飛車がまだ初期の筋にいる。 */
data object RooksHomeAtRookPawnPush : OpeningCondition {
    override fun describe(): String = "伸ばし切った時点で双方の飛車がまだ初期の筋にいる"

    override fun holds(context: OpeningContext, side: Side): Boolean =
        context.events.bothRooksHomeAt(context.events.rookPawnsPushedPly)
}

/** 飛車先の歩交換がある。 */
data class RookPawnTraded(val plyCap: Int) : OpeningCondition {
    override fun describe(): String =
        "${plyCap}手以内に飛車先の歩交換がある（先手2四・後手8六で飛車が歩を取る）"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val ply = context.events.pawnTradePly ?: return false
        return ply <= plyCap
    }
}

/** 序盤に角交換が成立していない。 */
data class NoBishopExchange(val plyCap: Int) : OpeningCondition {
    override fun describe(): String = "${plyCap}手以内に角交換が成立していない"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val ply = context.events.bishopExchangePly ?: return true
        return ply > plyCap
    }
}

/** 序盤に角交換が成立し、その時点で双方の飛車が初期の筋にいる。 */
data class BishopExchanged(val plyCap: Int) : OpeningCondition {
    override fun describe(): String =
        "${plyCap}手以内に角交換が成立し、その時点で双方の飛車がまだ初期の筋にいる"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val ply = context.events.bishopExchangePly ?: return false
        return ply <= plyCap && context.events.bothRooksHomeAt(ply)
    }
}

/** 成立の直後に飛車を振っていない。 */
data class NoSwingAfter(val basis: Basis, val plies: Int) : OpeningCondition {
    enum class Basis(val label: String) {
        ROOK_PAWN_PUSH("飛車先を伸ばし切った時点"),
        BISHOP_EXCHANGE("角交換が成立した時点"),
        ACHIEVEMENT("その形が成立した時点"),
    }

    override fun describe(): String = "${basis.label}から${plies}手以内にどちらも飛車を振らない"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val base = when (basis) {
            Basis.ROOK_PAWN_PUSH -> context.events.rookPawnsPushedPly
            Basis.BISHOP_EXCHANGE -> context.events.bishopExchangePly
            Basis.ACHIEVEMENT -> context.achievementPly
        } ?: return false
        return !context.events.swungWithin(base, plies)
    }
}

/** 角交換の直後に角を打っていない（筋違い角を除く）。 */
data class NoBishopDropAfterExchange(val plies: Int) : OpeningCondition {
    override fun describe(): String = "角交換から${plies}手以内に角を打たない（筋違い角を除く）"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val exchange = context.events.bishopExchangePly ?: return false
        val drop = context.events.bishopDropPly ?: return true
        return drop - exchange > plies
    }
}

/** 別のタグが既に成立している。 */
data class RequiresTag(val tag: String) : OpeningCondition {
    override fun describe(): String = "${tag}が成立している"

    override fun holds(context: OpeningContext, side: Side): Boolean =
        context.tags.getValue(side).contains(tag)
}

/** 別のタグと同時には成立しない。 */
data class ExcludesTag(val tag: String) : OpeningCondition {
    override fun describe(): String = "同じ対局で${tag}が成立していない"

    override fun holds(context: OpeningContext, side: Side): Boolean =
        context.tags.values.none { tag in it }
}

/** 飛車が横歩を取る。 */
data class RookTookYokofu(val plyCap: Int) : OpeningCondition {
    override fun describe(): String =
        "${plyCap}手以内に飛車が横歩（先手3四・後手7六）を取る"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val ply = context.events.yokofuPly ?: return false
        return ply <= plyCap
    }
}

/** 最初の角の捕獲を、その側が相手の角の初期位置で行った。 */
data object CapturedBishopOnOpponentHome : OpeningCondition {
    override fun describe(): String = "最初の角の捕獲を、相手の角の初期位置（8八）で自分から行った"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val capture = context.events.firstBishopCapture ?: return false
        return capture.by == side && capture.onOpponentHome
    }
}

/** 角交換より前に自分の飛車先の歩を伸ばしていない（手を省いている）。 */
data object RookPawnNotPushedBeforeExchange : OpeningCondition {
    override fun describe(): String = "角交換の時点で、自分はまだ飛車先の歩を5段目へ伸ばしていない"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val exchange = context.events.bishopExchangePly ?: return false
        val push = context.events.rookPawnPushPly[side] ?: return true
        return push > exchange
    }
}

/** その形が成立するまでに角交換が起きていない。 */
data object NoBishopExchangeBeforeAchievement : OpeningCondition {
    override fun describe(): String = "その形が成立するまでに角交換が起きていない"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val achievement = context.achievementPly ?: return false
        val capture = context.events.anyBishopCapturePly ?: return true
        return capture >= achievement
    }
}

/** 双方が振り飛車。 */
data object BothFuribisha : OpeningCondition {
    override fun describe(): String = "双方が振り飛車に振っている"

    override fun holds(context: OpeningContext, side: Side): Boolean =
        context.styles.values.all { it in FURIBISHA_STYLE_LABELS }
}

/** 自分が振り飛車。 */
data object SelfFuribisha : OpeningCondition {
    override fun describe(): String = "自分が振り飛車に振っている"

    override fun holds(context: OpeningContext, side: Side): Boolean =
        context.styles.getValue(side) in FURIBISHA_STYLE_LABELS
}

/** 序盤に角交換が成立している（飛車の位置は問わない）。 */
data class BishopExchangedAnywhere(val plyCap: Int) : OpeningCondition {
    override fun describe(): String = "${plyCap}手以内に角交換が成立している"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val ply = context.events.bishopExchangePly ?: return false
        return ply <= plyCap
    }
}

/** 振り飛車として扱う戦型名（袖飛車は振り飛車ではないため含めない）。 */
internal val FURIBISHA_STYLE_LABELS = setOf("中飛車", "四間飛車", "三間飛車", "向かい飛車")

/** 後手の指し方を指す戦型（手損のように、先後で意味が変わるもの）。 */
data object OnlyGote : OpeningCondition {
    override fun describe(): String = "成立するのは後手だけ"

    override fun holds(context: OpeningContext, side: Side): Boolean = side == Side.WHITE
}

/** 角交換の直後に、自分の角を筋違いのマスへ打つ。 */
data class SujichigaiDrop(val withinPlies: Int) : OpeningCondition {
    override fun describe(): String =
        "角交換から${withinPlies}手以内に、自分の角を筋違いのマス（先手4五・後手6五）へ打つ"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val drop = context.events.sujichigaiDropPly[side] ?: return false
        val exchange = context.events.bishopExchangePly ?: return false
        return drop - exchange <= withinPlies
    }
}

/** 角道を開けたまま7五の歩を突き、飛車を7筋へ振る。 */
data class HayaishidaSetup(val plyCap: Int) : OpeningCondition {
    override fun describe(): String =
        "${plyCap}手以内に7五（後手3五）の歩を突き、飛車を7筋（後手3筋）へ振る。" +
            "角道を止める歩（先手6六・後手4四）は突いていない"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val pawn = context.events.sideRookPawnPly[side] ?: return false
        val rook = context.events.sangenbishaPly[side] ?: return false
        // 角道を止めた将棋はただの三間飛車。角道を開けたまま突くのが早石田。
        if (context.events.bishopPathClosedPly[side] != null) return false
        return maxOf(pawn, rook) <= plyCap
    }
}

/** 飛車先の歩を交換したあと、その飛車を左翼へ寄せる。 */
data class HineriTurn(val plyCap: Int) : OpeningCondition {
    override fun describe(): String =
        "飛車先の歩を交換したあと、${plyCap}手以内にその飛車を3六（後手7四）へ寄せる"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val ply = context.events.hineriPly[side] ?: return false
        return ply <= plyCap
    }
}

/** 自陣の最下段へ引いた飛車を、その段のまま端筋へ通す。 */
data class ChikatetsuTunnel(val plyCap: Int) : OpeningCondition {
    override fun describe(): String =
        "自陣の最下段へ引いた飛車を、その段のまま${plyCap}手以内に端筋（先手9筋・後手1筋）へ回す"

    override fun holds(context: OpeningContext, side: Side): Boolean {
        val ply = context.events.chikatetsuPly[side] ?: return false
        return ply <= plyCap
    }
}
