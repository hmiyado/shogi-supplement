package dev.miyado.shogisupplement.opening

private fun sample(label: String, moves: String, matches: Boolean) =
    OpeningSample(label, usi(moves), matches)

/**
 * 出来事で判定する戦型。並び順は評価順で、前提にするタグ（横歩取り←相掛かり）や
 * 排他にするタグ（角換わり×角交換振り飛車）が先に決まっている必要がある。
 */
val EVENT_STRATEGY_DEFS: List<EventStrategyDef> = listOf(
    EventStrategyDef(
        name = "相振り飛車",
        slug = "ai-furibisha",
        scope = TagScope.BOTH_SIDES,
        conditions = listOf(BothFuribisha),
        source = "双方が振り飛車に振った対局を指す一般的な呼び方",
    ),
    EventStrategyDef(
        name = "角交換振り飛車",
        slug = "kakukoukan-furibisha",
        scope = TagScope.MATCHING_SIDE,
        conditions = listOf(
            BishopExchangedAnywhere(plyCap = 20),
            SelfFuribisha,
        ),
        source = "ja.wikipedia.org/wiki/角交換振り飛車（序盤に角を交換してから振る指し方）",
        samples = listOf(
            sample(
                "成立する手順（先手に付く）",
                "7g7f 3c3d 8h2b+ 3a2b 2h6h 1c1d 1g1f 9c9d 9g9f 4c4d 3g3f 7c7d 4g4f 6c6d " +
                    "5g5f 2b3c 6g6f 8c8d 3i3h 8d8e 5i6i 5c5d",
                matches = true,
            ),
        ),
    ),
    EventStrategyDef(
        name = "相掛かり",
        slug = "aigakari",
        scope = TagScope.BOTH_SIDES,
        conditions = listOf(
            RookPawnsPushed(plyCap = 16, gapMax = 8),
            RooksHomeAtRookPawnPush,
            RookPawnTraded(plyCap = 24),
            NoBishopExchange(plyCap = 24),
            NoSwingAfter(NoSwingAfter.Basis.ROOK_PAWN_PUSH, plies = 20),
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
    EventStrategyDef(
        name = "角換わり",
        slug = "kakugawari",
        scope = TagScope.BOTH_SIDES,
        conditions = listOf(
            BishopExchanged(plyCap = 20),
            NoSwingAfter(NoSwingAfter.Basis.BISHOP_EXCHANGE, plies = 20),
            NoBishopDropAfterExchange(plies = 10),
            ExcludesTag("角交換振り飛車"),
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
    EventStrategyDef(
        name = "一手損角換わり",
        slug = "itteson-kakugawari",
        scope = TagScope.MATCHING_SIDE,
        conditions = listOf(
            RequiresTag("角換わり"),
            OnlyGote,
            CapturedBishopOnOpponentHome,
            RookPawnNotPushedBeforeExchange,
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
    EventStrategyDef(
        name = "横歩取り",
        slug = "yokofudori",
        scope = TagScope.BOTH_SIDES,
        conditions = listOf(
            RequiresTag("相掛かり"),
            RookTookYokofu(plyCap = 30),
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
