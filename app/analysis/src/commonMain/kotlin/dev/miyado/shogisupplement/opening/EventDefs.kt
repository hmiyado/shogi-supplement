package dev.miyado.shogisupplement.opening

/** 出来事で判定する戦型の閾値。条件の記述もこの値から組み立てる。 */
object OpeningEventRules {
    /** 双方が飛車先の歩を伸ばし合う序盤の上限。 */
    const val AIGAKARI_PLY_CAP = 16

    /** 双方の伸ばし切りがこれ以上離れたら「伸ばし合った」とは見ない。 */
    const val AIGAKARI_GAP_MAX = 8

    /** 飛車先の歩交換が「序盤の交換」として成立する上限。 */
    const val PAWN_TRADE_PLY_CAP = 24

    /** 飛車で横歩を取る手が「戦型としての横歩取り」である上限。 */
    const val YOKOFUDORI_PLY_CAP = 30

    /** 角交換が成立する上限（角換わりが成立する側の窓）。 */
    const val KAKUGAWARI_PLY_CAP = 20

    /** 取り返しがこの手数までなら序盤の角交換とみなす（相掛かりを除外する側の窓）。 */
    const val EXCHANGE_FINISH_CAP = 24

    /** 成立からこの手数以内に振り飛車へ定着したら取り消す。 */
    const val FURIBISHA_CANCEL_PLIES = 20

    /** 角交換からこの手数以内の角打ちは筋違い角として除く。 */
    const val BISHOP_DROP_CANCEL_PLIES = 10
}

private fun sample(label: String, moves: String, matches: Boolean) =
    OpeningSample(label, usi(moves), matches)

/**
 * 出来事で判定する戦型。条件は [OpeningEventTracker] の実装と対応し、
 * 手数はすべて [OpeningEventRules] の値を指す。
 */
val EVENT_DEFS: List<EventDef> = listOf(
    EventDef(
        name = "相掛かり",
        slug = "aigakari",
        conditions = listOf(
            "双方が${OpeningEventRules.AIGAKARI_PLY_CAP}手以内に飛車先の歩を5段目（先手2五・後手8五）へ伸ばす",
            "双方の伸ばし切りの手数差が${OpeningEventRules.AIGAKARI_GAP_MAX}手以内",
            "遅い方が伸ばし切った時点で、双方の飛車がまだ初期の筋にいる",
            "${OpeningEventRules.PAWN_TRADE_PLY_CAP}手以内に飛車先の歩交換がある（先手2四・後手8六で飛車が歩を取る）",
            "${OpeningEventRules.EXCHANGE_FINISH_CAP}手以内に角交換が成立していない",
            "成立から${OpeningEventRules.FURIBISHA_CANCEL_PLIES}手以内にどちらも振り飛車へ定着しない",
        ),
        source = "ja.wikipedia.org/wiki/相掛かり（「飛車先の歩を伸ばし合い」「角交換は行わない」" +
            "「飛車先の歩を交換してから飛車を引く」）",
        samples = listOf(
            sample("成立する手順", "2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d", matches = true),
            sample(
                "飛車先の歩を交換していないので成立しない",
                "2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 3i3h 3c3d 3h2g 2b3c",
                matches = false,
            ),
        ),
    ),
    EventDef(
        name = "角換わり",
        slug = "kakugawari",
        conditions = listOf(
            "${OpeningEventRules.KAKUGAWARI_PLY_CAP}手以内に角交換が成立する（双方の角が取られる）",
            "その時点で双方の飛車がまだ初期の筋にいる",
            "成立から${OpeningEventRules.FURIBISHA_CANCEL_PLIES}手以内にどちらも振り飛車へ定着しない",
            "角交換から${OpeningEventRules.BISHOP_DROP_CANCEL_PLIES}手以内に角を打たない（筋違い角を除く）",
            "同じ対局で角交換振り飛車が成立していない",
        ),
        source = "ja.wikipedia.org/wiki/角換わり（「序盤で角を交換した後に駒組みを進める相居飛車の戦法」）",
        samples = listOf(
            sample(
                "成立する手順",
                "7g7f 8c8d 2g2f 4a3b 2f2e 8d8e 8h7g 3c3d 7i6h 2b7g+ 6h7g 3a2b",
                matches = true,
            ),
            sample("角交換が無いので成立しない", "2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d", matches = false),
        ),
    ),
    EventDef(
        name = "一手損角換わり",
        slug = "itteson-kakugawari",
        conditions = listOf(
            "角換わりが成立している",
            "最初の角の捕獲を後手が、相手の角の初期位置（8八）で行った",
            "その時点で後手がまだ飛車先の歩を5段目（8五）へ伸ばしていない",
            "成立するのは後手だけ（手損した側の指し方のため）",
        ),
        source = "ja.wikipedia.org/wiki/一手損角換わり（「後手が△8五歩を省略するために早期に角交換する」）",
        samples = listOf(
            sample("成立する手順（後手に付く）", "7g7f 3c3d 2g2f 2b8h+ 7i8h 3a2b", matches = true),
            sample(
                "飛車先を伸ばしてから交換したので成立しない",
                "2g2f 8c8d 2f2e 8d8e 7g7f 3c3d 6i7h 2b8h+ 7i8h 3a2b",
                matches = false,
            ),
        ),
    ),
    EventDef(
        name = "横歩取り",
        slug = "yokofudori",
        conditions = listOf(
            "相掛かりが成立している",
            "${OpeningEventRules.YOKOFUDORI_PLY_CAP}手以内に飛車が横歩（先手3四・後手7六）を取る",
        ),
        source = "ja.wikipedia.org/wiki/横歩取り（「15手目に△3四歩を飛車で取ってからの一連の変化」）",
        samples = listOf(
            sample(
                "成立する手順",
                "7g7f 3c3d 2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d 8e8f 8g8f 8b8f 2d3d",
                matches = true,
            ),
            sample(
                "横歩を取っていないので成立しない",
                "2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d",
                matches = false,
            ),
        ),
    ),
)
