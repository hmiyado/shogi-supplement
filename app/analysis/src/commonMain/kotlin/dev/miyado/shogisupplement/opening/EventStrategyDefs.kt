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
        samples = listOf(
            sample(
                "成立する手順（双方が振る）",
                "2h6h 8b4b 1g1f 1c1d 3g3f 3c3d 4g4f 4c4d 7g7f 7c7d 8g8f 8c8d 9g9f 9c9d 1f1e 1d1e 3f3e 3d3e 4f4e 4d4e 7f7e 7d7e 8f8e 8d8e 9f9e 9d9e",
                matches = true,
            ),
            sample(
                "先手だけが振ったので成立しない",
                "2h6h 6c6d 1g1f 7c7d 2g2f 8c8d 3g3f 9c9d 4g4f 6d6e 1f1e 7d7e 2f2e 8d8e 3f3e 9d9e 4f4e 6e6f 1e1d 7e7f 2e2d 8e8f 3e3d 9e9f 4e4d 6f6g",
                matches = false,
            ),
        ),
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
            sample(
                "角交換していない振り飛車なので成立しない",
                "2h6h 6c6d 1g1f 7c7d 2g2f 8c8d 3g3f 9c9d 4g4f 6d6e 1f1e 7d7e 2f2e 8d8e 3f3e 9d9e 4f4e 6e6f 1e1d 7e7f 2e2d 8e8f 3e3d 9e9f 4e4d 6f6g",
                matches = false,
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
    EventStrategyDef(
        name = "筋違い角",
        slug = "sujichigai-kaku",
        scope = TagScope.MATCHING_SIDE,
        conditions = listOf(
            SujichigaiDrop(withinPlies = 10),
        ),
        source = "shougi.jp/senpou/sujichigaikaku/（「▲7六歩△3四歩▲2二角成△同銀▲4五角」）",
        samples = listOf(
            sample("成立する手順", "7g7f 3c3d 8h2b+ 3a2b B*4e", matches = true),
            sample(
                "角を交換しただけで打っていないので成立しない",
                "7g7f 3c3d 8h2b+ 3a2b 3i4h 2b3c",
                matches = false,
            ),
        ),
    ),
    EventStrategyDef(
        name = "早石田",
        slug = "hayaishida",
        scope = TagScope.MATCHING_SIDE,
        conditions = listOf(
            HayaishidaSetup(plyCap = 20),
        ),
        source = "shougi.jp/senpou/hayaishida/（「▲7八飛、▲7五歩」「▲7六歩△3四歩のあとに" +
            "角道を止めず▲7五歩を突く」）",
        samples = listOf(
            sample("成立する手順", "7g7f 3c3d 7f7e 8c8d 2h7h", matches = true),
            sample(
                "角道を止めた三間飛車は成立しない",
                "7g7f 3c3d 6g6f 8c8d 2h7h 8d8e 7f7e 4a3b",
                matches = false,
            ),
        ),
    ),
    EventStrategyDef(
        name = "ひねり飛車",
        slug = "hineribisha",
        scope = TagScope.MATCHING_SIDE,
        conditions = listOf(
            RequiresTag("相掛かり"),
            HineriTurn(plyCap = 40),
        ),
        source = "shougi.jp/senpou/hineribisya/（「相掛かり戦法の出だしから飛車を左翼に転換して" +
            "戦う戦法」「▲３六飛まで」）",
        samples = listOf(
            sample(
                "成立する手順",
                "2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d 8e8f 8g8f 8b8f " +
                    "2d2f 8f8b 2f3f 6a5b",
                matches = true,
            ),
            sample(
                "飛車を左翼へ寄せていない相掛かりでは成立しない",
                "2g2f 8c8d 2f2e 8d8e 6i7h 4a3b 2e2d 2c2d 2h2d 8e8f 8g8f 8b8f 2d2h 8f8b",
                matches = false,
            ),
        ),
    ),
    EventStrategyDef(
        name = "地下鉄飛車",
        slug = "chikatetsu-bisha",
        scope = TagScope.MATCHING_SIDE,
        conditions = listOf(
            ChikatetsuTunnel(plyCap = 60),
        ),
        source = "ja.wikipedia.org/wiki/地下鉄飛車（「飛車を1段目に引いた後、飛車を転換して指す」" +
            "「9筋に飛車を転換すると地下鉄飛車となる」）",
        samples = listOf(
            sample(
                "成立する手順",
                "3g3f 3c3d 7g7f 4c4d 2i3g 5c5d 3i3h 6c6d 4i4h 7c7d 5i5h 8c8d " +
                    "6i6h 1c1d 7i7h 9c9d 8i7g 2c2d 9i9h 4a3b 2h2i 5a4b 2i9i 6a5b",
                matches = true,
            ),
            sample(
                "最下段へ引いただけで端筋へ回していないので成立しない",
                "3g3f 3c3d 7g7f 4c4d 2i3g 5c5d 3i3h 6c6d 2h2i 4a3b",
                matches = false,
            ),
        ),
    ),
)
