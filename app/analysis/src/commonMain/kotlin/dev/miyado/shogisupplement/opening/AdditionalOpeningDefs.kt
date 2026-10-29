package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.PieceType.*

internal val ADDITIONAL_CASTLE_DEFS = listOf(
    PlacementDef(
        name = "金無双", slug = "kinmusou", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(3, 8)), PiecePlacement(GOLD, BfSquare(4, 8)), PiecePlacement(GOLD, BfSquare(5, 8))),
        plyCap = CASTLE_PLY_CAP,
        source = "https://www.shougi.jp/kakoi/kinmusou/（公開基本図の玉・金銀配置を照合）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("2h7h 4a4b 5i4h 4b4a 4h3h 4a4b 6i5h 4b4a 4i4h 4a4b"), true),
            OpeningSample("未完成の形", usi("2h7h 4a4b 5i4h 4b4a 4h3h 4a4b 6i5h 4b4a"), false),
        ),
    ),
    PlacementDef(
        name = "金無双", slug = "ibisha-kinmusou", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(7, 8)), PiecePlacement(GOLD, BfSquare(6, 8)), PiecePlacement(GOLD, BfSquare(5, 8))),
        plyCap = CASTLE_PLY_CAP,
        source = "https://store.shogi.or.jp/view/item/000000002624 （居飛車型は玉7八・金6八/5八）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("7g7f 4a4b 5i6h 4b4a 6h7h 4a4b 6i6h 4b4a 4i5h 4a4b"), true),
            OpeningSample("未完成の形", usi("7g7f 4a4b 5i6h 4b4a 6h7h 4a4b 6i6h 4b4a"), false),
        ),
    ),
    PlacementDef(
        name = "左美濃囲い", slug = "hidari-mino", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(8, 8)), PiecePlacement(SILVER, BfSquare(7, 8)), PiecePlacement(GOLD, BfSquare(6, 9)), PiecePlacement(GOLD, BfSquare(5, 8))),
        plyCap = CASTLE_PLY_CAP,
        source = "https://www.shougi.jp/kakoi/hidari_mino/（公開基本図の玉・金銀配置を照合）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("7g7f 4a4b 8h7g 4b4a 5i6h 4a4b 6h7h 4b4a 7h8h 4a4b 7i7h 4b4a 4i5h 4a4b"), true),
            OpeningSample("未完成の形", usi("7g7f 4a4b 8h7g 4b4a 5i6h 4a4b 6h7h 4b4a 7h8h 4a4b 7i7h 4b4a"), false),
        ),
    ),
    PlacementDef(
        name = "カニ囲い", slug = "kani", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(6, 9)), PiecePlacement(SILVER, BfSquare(6, 8)), PiecePlacement(GOLD, BfSquare(7, 8)), PiecePlacement(GOLD, BfSquare(5, 8))),
        plyCap = CASTLE_PLY_CAP,
        source = "https://www.shougi.jp/kakoi/kani_kakoi/（公開基本図の玉・金銀配置を照合）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("7i6h 4a4b 6i7h 4b4a 4i5h 4a4b 5i6i 4b4a"), true),
            OpeningSample("未完成の形", usi("7i6h 4a4b 6i7h 4b4a 4i5h 4a4b"), false),
        ),
    ),
    PlacementDef(
        name = "片矢倉", slug = "kata-yagura", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(7, 8)), PiecePlacement(SILVER, BfSquare(7, 7)), PiecePlacement(GOLD, BfSquare(6, 7)), PiecePlacement(GOLD, BfSquare(6, 8))),
        plyCap = CASTLE_PLY_CAP,
        developsFrom = "カニ囲い",
        source = "https://www.shougi.jp/kakoi/kata_yagura/（公開基本図の玉・金銀配置を照合）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("7g7f 4a4b 7i6h 4b4a 6h7g 4a4b 6g6f 4b4a 6i7h 4a4b 7h6h 4b4a 4i5h 4a4b 5h6g 4b4a 5i6i 4a4b 6i7h 4b4a"), true),
            OpeningSample("未完成の形", usi("7g7f 4a4b 7i6h 4b4a 6h7g 4a4b 6g6f 4b4a 6i7h 4a4b 7h6h 4b4a 4i5h 4a4b 5h6g 4b4a 5i6i 4a4b"), false),
        ),
    ),
    PlacementDef(
        name = "銀矢倉", slug = "gin-yagura", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(8, 8)), PiecePlacement(SILVER, BfSquare(7, 7)), PiecePlacement(SILVER, BfSquare(6, 7)), PiecePlacement(GOLD, BfSquare(7, 8))),
        plyCap = CASTLE_PLY_CAP,
        developsFrom = "カニ囲い",
        source = "https://www.shougi.jp/kakoi/gin_yagura/（公開基本図の玉・金銀配置を照合）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("7g7f 4a4b 8h5e 4b4a 7i6h 4a4b 6h7g 4b4a 5g5f 4a4b 6g6f 4b4a 3i4h 4a4b 4h5g 4b4a 5g6h 4a4b 6h6g 4b4a 6i7h 4a4b 5i6h 4b4a 6h7i 4a4b 7i8h 4b4a"), true),
            OpeningSample("未完成の形", usi("7g7f 4a4b 8h5e 4b4a 7i6h 4a4b 6h7g 4b4a 5g5f 4a4b 6g6f 4b4a 3i4h 4a4b 4h5g 4b4a 5g6h 4a4b 6h6g 4b4a 6i7h 4a4b 5i6h 4b4a 6h7i 4a4b"), false),
        ),
    ),
    PlacementDef(
        name = "ミレニアム", slug = "millennium", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(8, 9)), PiecePlacement(SILVER, BfSquare(8, 8)), PiecePlacement(SILVER, BfSquare(7, 9)), PiecePlacement(GOLD, BfSquare(7, 8)), PiecePlacement(GOLD, BfSquare(6, 7)), PiecePlacement(KNIGHT, BfSquare(7, 7))),
        plyCap = CASTLE_PLY_CAP,
        source = "https://www.shougi.jp/kakoi/millennium/（公開基本図の玉・金銀配置を照合）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("7g7f 4a4b 5g5f 4b4a 8h5e 4a4b 7i6h 4b4a 6h7g 4a4b 7g8h 4b4a 8i7g 4a4b 6i7h 4b4a 5i6h 4a4b 6h7i 4b4a 7i8i 4a4b 3i4h 4b4a 4h5g 4a4b 5g6h 4b4a 6h7i 4a4b 6g6f 4b4a 4i5h 4a4b 5h6g 4b4a"), true),
            OpeningSample("未完成の形", usi("7g7f 4a4b 5g5f 4b4a 8h5e 4a4b 7i6h 4b4a 6h7g 4a4b 7g8h 4b4a 8i7g 4a4b 6i7h 4b4a 5i6h 4a4b 6h7i 4b4a 7i8i 4a4b 3i4h 4b4a 4h5g 4a4b 5g6h 4b4a 6h7i 4a4b 6g6f 4b4a 4i5h 4a4b"), false),
        ),
    ),
    PlacementDef(
        name = "銀冠穴熊", slug = "ginana", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(1, 9)), PiecePlacement(SILVER, BfSquare(2, 7)), PiecePlacement(GOLD, BfSquare(3, 8)), PiecePlacement(GOLD, BfSquare(4, 8)), PiecePlacement(LANCE, BfSquare(1, 8))),
        plyCap = CASTLE_PLY_CAP,
        developsFrom = "銀冠",
        source = "https://www.shougi.jp/kakoi/ginana/（公開基本図の玉・金銀配置を照合）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("2h7h 4a4b 2g2f 4b4a 4g4f 4a4b 3i3h 4b4a 3h2g 4a4b 5i4h 4b4a 4h3h 4a4b 3h2h 4b4a 4i4h 4a4b 4h3h 4b4a 6i5h 4a4b 5h4h 4b4a 1i1h 4a4b 2h1i 4b4a"), true),
            OpeningSample("未完成の形", usi("2h7h 4a4b 2g2f 4b4a 4g4f 4a4b 3i3h 4b4a 3h2g 4a4b 5i4h 4b4a 4h3h 4a4b 3h2h 4b4a 4i4h 4a4b 4h3h 4b4a 6i5h 4a4b 5h4h 4b4a 1i1h 4a4b"), false),
        ),
    ),
    PlacementDef(
        name = "銀冠穴熊", slug = "ibisha-ginana", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(9, 9)), PiecePlacement(SILVER, BfSquare(8, 7)), PiecePlacement(GOLD, BfSquare(7, 8)), PiecePlacement(GOLD, BfSquare(6, 8)), PiecePlacement(LANCE, BfSquare(9, 8))),
        plyCap = CASTLE_PLY_CAP,
        developsFrom = "左美濃囲い",
        source = "https://www.shougi.jp/kakoi/ginana/ （公開基本図の筋対称形。居飛車型の用例はhttps://store.shogi.or.jp/view/item/000000002679 ）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("7g7f 4a4b 8h5e 4b4a 8g8f 4a4b 7i7h 4b4a 7h8g 4a4b 9i9h 4b4a 6i7h 4a4b 5i6h 4b4a 6h7i 4a4b 7i8h 4b4a 8h9i 4a4b 4i5h 4b4a 5h6h 4a4b"), true),
            OpeningSample("未完成の形", usi("7g7f 4a4b 8h5e 4b4a 8g8f 4a4b 7i7h 4b4a 7h8g 4a4b 9i9h 4b4a 6i7h 4a4b 5i6h 4b4a 6h7i 4a4b 7i8h 4b4a 8h9i 4a4b 4i5h 4b4a"), false),
        ),
    ),
    PlacementDef(
        name = "天守閣美濃", slug = "tenshukaku-mino", kind = OpeningKind.CASTLE,
        required = listOf(PiecePlacement(KING, BfSquare(8, 7)), PiecePlacement(SILVER, BfSquare(7, 8)), PiecePlacement(GOLD, BfSquare(6, 9)), PiecePlacement(GOLD, BfSquare(5, 8))),
        plyCap = CASTLE_PLY_CAP,
        developsFrom = "左美濃囲い",
        source = "https://www.shougi.jp/kakoi/tensyukaku_mino/（公開基本図の玉・金銀配置を照合）",
        samples = listOf(
            OpeningSample("基本形の成立", usi("7g7f 4a4b 8h7g 4b4a 5i6h 4a4b 6h7h 4b4a 7h8h 4a4b 7i7h 4b4a 4i5h 4a4b 8g8f 4b4a 8h8g 4a4b 7g8h 4b4a"), true),
            OpeningSample("未完成の形", usi("7g7f 4a4b 8h7g 4b4a 5i6h 4a4b 6h7h 4b4a 7h8h 4a4b 7i7h 4b4a 4i5h 4a4b"), false),
        ),
    ),
)

internal val ADDITIONAL_STRATEGY_DEFS = listOf(
    PlacementDef(
        name = "ゴキゲン中飛車", slug = "gokigen-nakabisha", kind = OpeningKind.STRATEGY,
        required = listOf(PiecePlacement(ROOK, BfSquare(5, 8)), PiecePlacement(PAWN, BfSquare(5, 5)),
            PiecePlacement(PAWN, BfSquare(7, 6)), PiecePlacement(BISHOP, BfSquare(8, 8))),
        empty = listOf(BfSquare(6, 6)), plyCap = 24,
        conditions = listOf(NoBishopExchangeBeforeAchievement, BishopPathOpenBeforeAchievement),
        source = "https://store.shogi.or.jp/view/item/000000002584 （角道を止めない中飛車。5五歩型の基本形を対象とし、角交換前に成立したものを記録する）",
        samples = listOf(
            OpeningSample("角道を止めない5五歩型", usi("7g7f 4a4b 5g5f 4b4a 2h5h 4a4b 5f5e 4b4a"), true),
            OpeningSample("5筋の歩を伸ばす前", usi("7g7f 4a4b 5g5f 4b4a 2h5h 4a4b"), false),
        ),
    ),
    PlacementDef(
        name = "嬉野流", slug = "ureshino", kind = OpeningKind.STRATEGY,
        required = listOf(PiecePlacement(SILVER, BfSquare(5, 7)), PiecePlacement(PAWN, BfSquare(5, 6)),
            PiecePlacement(PAWN, BfSquare(7, 7))),
        plyCap = 24, conditions = listOf(UreshinoFirstMove, NoBishopExchangeBeforeAchievement),
        source = "https://store.shogi.or.jp/view/item/000000002588 （自分の初手6八銀、角道を開けず5六歩・5七銀。7九角を保留する新嬉野流も含む）",
        samples = listOf(
            OpeningSample("ureshinoの成立", usi("7i6h 4a4b 5g5f 4b4a 6h5g 4a4b 8h7i 4b4a"), true),
            OpeningSample("ureshino-newの成立", usi("7i6h 4a4b 5g5f 4b4a 6h5g 4a4b"), true),
            OpeningSample("銀を上げただけ", usi("7i6h 4a4b"), false),
        ),
    ),
)
