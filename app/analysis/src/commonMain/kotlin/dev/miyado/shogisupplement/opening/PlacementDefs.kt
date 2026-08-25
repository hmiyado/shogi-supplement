package dev.miyado.shogisupplement.opening

import dev.miyado.shogisupplement.board.PieceType.BISHOP
import dev.miyado.shogisupplement.board.PieceType.GOLD
import dev.miyado.shogisupplement.board.PieceType.KING
import dev.miyado.shogisupplement.board.PieceType.KNIGHT
import dev.miyado.shogisupplement.board.PieceType.LANCE
import dev.miyado.shogisupplement.board.PieceType.PAWN
import dev.miyado.shogisupplement.board.PieceType.ROOK
import dev.miyado.shogisupplement.board.PieceType.SILVER
import dev.miyado.shogisupplement.board.PieceType

// 座標はすべて自分視点（先手の8八＝後手の2二）。
// 出典は公開情報（Wikipedia・日本将棋連盟の戦法/囲い一覧）から独自に書き起こしたもの。

/** 囲いを判定する上限手数。これ以降は終盤の玉の逃避行による偶然の一致を拾わない。 */
const val CASTLE_PLY_CAP: Int = 160

private fun p(type: PieceType, file: Int, rank: Int) = PiecePlacement(type, BfSquare(file, rank))

private fun bf(file: Int, rank: Int) = BfSquare(file, rank)

private fun sample(label: String, moves: String, matches: Boolean) =
    OpeningSample(label, usi(moves), matches)

/** 囲い。完成形だけを厳密に照合する（曖昧な閾値は置かない）。 */
val CASTLE_DEFS: List<PlacementDef> = listOf(
    PlacementDef(
        name = "矢倉",
        slug = "yagura",
        kind = OpeningKind.CASTLE,
        required = listOf(p(KING, 8, 8), p(SILVER, 7, 7), p(GOLD, 7, 8), p(GOLD, 6, 7)),
        plyCap = CASTLE_PLY_CAP,
        source = "ja.wikipedia.org/wiki/矢倉囲い（基本形=玉8八・左金7八・右金6七・銀7七）",
        samples = listOf(
            sample("成立する手順", "7g7f 1c1d 8h5e 9c9d 5i6h 2c2d 6h7g 8c8d 7g8h 3c3d 7i7h 7c7d 7h7g 1d1e 6g6f 9d9e 6i7h 2d2e 4i5h 8d8e 5h6g 3d3e", matches = true),
            sample("成立しない近い手順", "7g7f 1c1d 8h5e 9c9d 5i6h 2c2d 6h7g 8c8d 7g8h 3c3d 7i7h 7c7d 7h7g 1d1e 6g6f 9d9e 6i7h 2d2e 4i5h 8d8e", matches = false),
        ),
    ),
    PlacementDef(
        name = "本美濃囲い",
        slug = "hon-mino",
        kind = OpeningKind.CASTLE,
        required = listOf(p(KING, 2, 8), p(SILVER, 3, 8), p(GOLD, 5, 8), p(GOLD, 4, 9)),
        plyCap = CASTLE_PLY_CAP,
        developsFrom = "片美濃囲い",
        source = "ja.wikipedia.org/wiki/美濃囲い（玉2八・銀3八・左金5八・右金は初期位置4九のまま）",
        samples = listOf(
            sample("成立する手順", "2h7h 1c1d 5i4h 9c9d 4h3h 2c2d 3h2h 8c8d 3i3h 3c3d 6i5h 7c7d", matches = true),
            sample("成立しない近い手順", "2h7h 1c1d 5i4h 9c9d 4h3h 2c2d 3h2h 8c8d 3i3h 3c3d", matches = false),
        ),
    ),
    PlacementDef(
        name = "高美濃囲い",
        slug = "taka-mino",
        kind = OpeningKind.CASTLE,
        required = listOf(p(KING, 2, 8), p(SILVER, 3, 8), p(GOLD, 4, 7), p(GOLD, 4, 9)),
        plyCap = CASTLE_PLY_CAP,
        developsFrom = "本美濃囲い",
        source = "ja.wikipedia.org/wiki/美濃囲い（本美濃から左金が5八→4七へ進んだ発展形）",
        samples = listOf(
            sample("成立する手順", "2h7h 1c1d 5i4h 9c9d 4h3h 2c2d 3h2h 8c8d 3i3h 3c3d 4g4f 7c7d 6i5h 1d1e 5h4g 9d9e", matches = true),
            sample("成立しない近い手順", "2h7h 1c1d 5i4h 9c9d 4h3h 2c2d 3h2h 8c8d 3i3h 3c3d", matches = false),
        ),
    ),
    PlacementDef(
        name = "片美濃囲い",
        slug = "kata-mino",
        kind = OpeningKind.CASTLE,
        required = listOf(p(KING, 2, 8), p(SILVER, 3, 8), p(GOLD, 4, 9)),
        plyCap = CASTLE_PLY_CAP,
        source = "shogi.jp/kakoi/katamino/（「美濃囲いから5八の金をとった形」。中飛車で美濃を組むと必然的にこの形になる）",
        samples = listOf(
            sample("成立する手順", "2h7h 1c1d 5i4h 9c9d 4h3h 2c2d 3h2h 8c8d 3i3h 3c3d", matches = true),
            sample("成立しない近い手順", "2h7h 1c1d 5i4h 9c9d 4h3h 2c2d 3h2h 8c8d", matches = false),
        ),
    ),
    PlacementDef(
        name = "銀冠",
        slug = "gin-kanmuri",
        kind = OpeningKind.CASTLE,
        required = listOf(p(KING, 2, 8), p(SILVER, 2, 7), p(GOLD, 4, 7), p(GOLD, 3, 8)),
        plyCap = CASTLE_PLY_CAP,
        developsFrom = "高美濃囲い",
        source = "shogi.jp/kakoi/ginkanmuri/（「高美濃囲いから、銀が玉の上にきた形」＝銀が3八→2七、金が4九→3八へ進んだ発展形。金4七は高美濃から変化なし）",
        samples = listOf(
            sample("成立する手順", "2h7h 1c1d 2g2f 9c9d 4g4f 2c2d 3i3h 8c8d 3h2g 3c3d 5i4h 7c7d 4h3h 6c6d 3h2h 4c4d 4i4h 1d1e 4h4g 9d9e 6i5h 2d2e 5h4h 8d8e 4h3h 3d3e", matches = true),
            sample("成立しない近い手順", "2h7h 1c1d 2g2f 9c9d 4g4f 2c2d 3i3h 8c8d 3h2g 3c3d 5i4h 7c7d 4h3h 6c6d 3h2h 4c4d 4i4h 1d1e 4h4g 9d9e 6i5h 2d2e 5h4h 8d8e", matches = false),
        ),
    ),
    PlacementDef(
        name = "居飛車穴熊",
        slug = "ibisha-anaguma",
        kind = OpeningKind.CASTLE,
        required = listOf(p(KING, 9, 9), p(SILVER, 8, 8), p(GOLD, 7, 9)),
        plyCap = CASTLE_PLY_CAP,
        source = "ja.wikipedia.org/wiki/穴熊囲い、将棋連盟コラム「居飛車穴熊の特徴と組み方」（銀が8八で蓋をし金が7九＝3枚穴熊の完成形）",
        samples = listOf(
            sample("成立する手順", "7g7f 1c1d 8h5e 9c9d 9i9h 2c2d 5i6h 8c8d 6h7g 3c3d 7g8h 7c7d 8h9i 1d1e 7i8h 9d9e 6i7i 2d2e", matches = true),
            sample("成立しない近い手順", "7g7f 1c1d 8h5e 9c9d 9i9h 2c2d 5i6h 8c8d 6h7g 3c3d 7g8h 7c7d 8h9i 1d1e 7i8h 9d9e", matches = false),
        ),
    ),
    PlacementDef(
        name = "振り飛車穴熊",
        slug = "furibisha-anaguma",
        kind = OpeningKind.CASTLE,
        required = listOf(p(KING, 1, 9), p(SILVER, 2, 8), p(GOLD, 3, 9)),
        plyCap = CASTLE_PLY_CAP,
        source = "ja.wikipedia.org/wiki/穴熊囲い（居飛車穴熊と筋対称の振り飛車側の形）",
        samples = listOf(
            sample("成立する手順", "2h7h 1c1d 1i1h 9c9d 5i4h 2c2d 4h3h 8c8d 3h2h 3c3d 2h1i 7c7d 3i2h 1d1e 4i3i 9d9e", matches = true),
            sample("成立しない近い手順", "2h7h 1c1d 1i1h 9c9d 5i4h 2c2d 4h3h 8c8d 3h2h 3c3d 2h1i 7c7d 3i2h 1d1e", matches = false),
        ),
    ),
    PlacementDef(
        name = "エルモ囲い",
        slug = "elmo",
        kind = OpeningKind.CASTLE,
        required = listOf(p(KING, 7, 8), p(SILVER, 6, 8), p(GOLD, 7, 9)),
        plyCap = CASTLE_PLY_CAP,
        source = "ja.wikipedia.org/wiki/エルモ囲い（7八玉・6八銀・7九金型）",
        samples = listOf(
            sample("成立する手順", "5i6h 1c1d 6h7h 9c9d 7i6h 2c2d 6i7i 8c8d", matches = true),
            sample("成立しない近い手順", "5i6h 1c1d 6h7h 9c9d 7i6h 2c2d", matches = false),
        ),
    ),
)

/** 駒の配置で判定する戦法。 */
val PLACEMENT_STRATEGY_DEFS: List<PlacementDef> = listOf(
    PlacementDef(
        name = "棒銀",
        slug = "bougin",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(SILVER, 2, 6)),
        plyCap = 40,
        source = "shougi.jp/senpou/bougin/（「原始棒銀」の完成図キャプション「▲2六銀まで」。本文『銀将を棒のようにまっすぐ進めて攻めることからこの名が付いた』。対振り飛車棒銀は▲２七銀・端棒銀は▲８四銀等ページ内に複数の型があるが、最も異論のない基本形（原始棒銀の銀2六）のみを厳密照合の対象とした）",
        samples = listOf(
            sample("成立する手順", "2g2f 1c1d 2f2e 9c9d 3i3h 8c8d 3h2g 7c7d 2g2f 6c6d", matches = true),
            sample("成立しない近い手順", "2g2f 1c1d 2f2e 9c9d 3i3h 8c8d 3h2g 7c7d", matches = false),
        ),
    ),
    PlacementDef(
        name = "早繰り銀",
        slug = "hayakurigin",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(SILVER, 4, 6)),
        plyCap = 40,
        source = "shougi.jp/senpou/hayagurigin/（本文『主に角換わり、後手番一手損角換わり、相掛かりにおいて、右銀を3七〜4六に活用する作戦の総称。▲4六銀-▲3五歩と攻める筋がある』。3七はまだ途中形（一間飛車のような通過点）なので、本文が名指しする到達点▲4六銀を厳密形として採用した）",
        samples = listOf(
            sample("成立する手順", "4g4f 1c1d 4f4e 9c9d 3i4h 8c8d 4h4g 7c7d 4g4f 6c6d", matches = true),
            sample("成立しない近い手順", "4g4f 1c1d 4f4e 9c9d 3i4h 8c8d 4h4g 7c7d", matches = false),
        ),
    ),
    PlacementDef(
        name = "腰掛け銀",
        slug = "koshikakegin",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(SILVER, 5, 6), p(PAWN, 5, 7)),
        plyCap = 40,
        source = "shougi.jp/senpou/koshikakegin/（本文『先手なら▲5六銀、後手なら△5四銀と構える。先手なら▲5七歩、後手なら△5三歩があるのを前提』『銀が歩の上に腰掛けているような様子からこの名前がついた』。歩(5七)が動いていないことが定義そのものなのでemptyではなくrequiredに含めた）",
        samples = listOf(
            sample("成立する手順", "4g4f 1c1d 3i4h 9c9d 4h4g 8c8d 4g5f 7c7d", matches = true),
            sample("成立しない近い手順", "4g4f 1c1d 3i4h 9c9d 4h4g 8c8d", matches = false),
        ),
    ),
    PlacementDef(
        name = "玉頭位取り",
        slug = "gyokutou-kuraidori",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(PAWN, 7, 5), p(SILVER, 7, 6)),
        plyCap = 50,
        source = "shougi.jp/senpou/gyokutou_kuraidori/（本文『銀を7六に配置して7筋の位を取る』。盤面図(自前でtable壁のtd/altを筋ヘッダ・段のかな数字と突き合わせて読み取り)でも歩7五・銀7六を確認。対振り飛車での使用が主だが、判定は自陣の駒配置のみで行うため相手の戦法は問わない設計とした）",
        samples = listOf(
            sample("成立する手順", "7g7f 1c1d 7f7e 9c9d 7i7h 8c8d 7h7g 6c6d 7g7f 5c5d", matches = true),
            sample("成立しない近い手順", "7g7f 1c1d 7f7e 9c9d 7i7h 8c8d 7h7g 6c6d", matches = false),
        ),
    ),
    PlacementDef(
        name = "5筋位取り",
        slug = "gosuji-kuraidori",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(PAWN, 5, 5), p(SILVER, 5, 6)),
        plyCap = 30,
        source = "ja.wikipedia.org/wiki/5筋位取り（『天王山ともいわれる5五の位を取り、敵陣を圧迫していく対振り飛車戦法の一つ』『5六に右銀を展開するタイプと左銀を展開するタイプに大別される』。shougi.jp戦法一覧に本タグの専用ページは無いため、この戦法一覧ページで代替した）",
        samples = listOf(
            sample("成立する手順", "5g5f 1c1d 5f5e 9c9d 3i4h 8c8d 4h5g 7c7d 5g5f 6c6d", matches = true),
            sample("成立しない近い手順", "5g5f 1c1d 5f5e 9c9d 3i4h 8c8d 4h5g 7c7d", matches = false),
        ),
    ),
    PlacementDef(
        name = "右玉",
        slug = "migigyoku",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(KING, 4, 8), p(GOLD, 5, 8)),
        plyCap = 24,
        source = "shougi.jp/senpou/migigyoku/（本文『居飛車のまま玉将を盤面右側に囲う』。本文に明示座標が無いため、掲載図（table壁のtd/altを筋ヘッダ・段のかな数字と突き合わせて自前で読み取り）から玉4八・金5八を確認して採用。他の右玉形（玉3八型等）は同ページに明示されておらず対象外とした）",
        samples = listOf(
            sample("成立する手順", "5i4h 1c1d 6i5h 9c9d", matches = true),
            sample("成立しない近い手順", "5i4h 1c1d", matches = false),
        ),
    ),
    PlacementDef(
        name = "雀刺し",
        slug = "suzumezashi",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(LANCE, 1, 7), p(ROOK, 1, 8), p(KNIGHT, 2, 5)),
        plyCap = 80,
        source = "shougi.jp/senpou/suzumezashi/（本文『▲1七香、▲1八飛、▲6八角または▲7九角、▲2五桂の位置に攻め駒を移動させ、1三の地点に集中させる』。角だけは本文が6八・7九の二択を示していて一意に定まらないため、残る香1七・飛1八・桂2五を厳密形とした。矢倉に組んでから端へ寄せ直す攻撃形なので上限手数は玉頭位取りより広く取る）",
        samples = listOf(
            sample("成立する手順", "1g1f 3c3d 1i1g 4c4d 3g3f 5c5d 2i3g 6c6d 3g2e 7c7d 2h1h 8c8d", matches = true),
            sample("飛車を1八へ寄せていないので成立しない", "1g1f 3c3d 1i1g 4c4d 3g3f 5c5d 2i3g 6c6d 3g2e 7c7d", matches = false),
        ),
    ),
    PlacementDef(
        name = "石田流",
        slug = "ishida",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(ROOK, 7, 6), p(KNIGHT, 7, 7), p(BISHOP, 9, 7)),
        plyCap = 24,
        source = "shougi.jp/senpou/ishida/（本文『飛車を7六、桂馬を7七に配する構えを言う。角は基本的に9七が定位置である』。掲載図でも飛7六・桂7七・角9七を確認）",
        samples = listOf(
            sample("成立する手順", "7g7f 1c1d 7f7e 9c9d 9g9f 8c8d 2h7h 7c7d 7h7f 6c6d 8i7g 5c5d 8h9g 4c4d", matches = true),
            sample("成立しない近い手順", "7g7f 1c1d 7f7e 9c9d 9g9f 8c8d 2h7h 7c7d 7h7f 6c6d 8i7g 5c5d", matches = false),
        ),
    ),
    PlacementDef(
        name = "ツノ銀中飛車",
        slug = "tsunogin-nakabisha",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(SILVER, 4, 7), p(SILVER, 6, 7)),
        plyCap = 30,
        source = "shougi.jp/senpou/furibisya/tsunogin_nakabisya.html（本文『2つの銀がツノのように見えることからこの名前がついた』。掲載図で左銀6七・右銀4七を確認。飛車は中飛車へ向かう途中の一時的な配置(図では5九のまま)で、振った筋は別途『中飛車』として判定するため、本タグでは銀のツノ形のみを必須とした）",
        samples = listOf(
            sample("成立する手順", "4g4f 1c1d 6g6f 9c9d 3i4h 8c8d 4h4g 7c7d 7i6h 6c6d 6h6g 5c5d", matches = true),
            sample("成立しない近い手順", "4g4f 1c1d 6g6f 9c9d 3i4h 8c8d 4h4g 7c7d 7i6h 6c6d", matches = false),
        ),
    ),
    PlacementDef(
        name = "平目",
        slug = "hirame",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(ROOK, 5, 8), p(SILVER, 3, 8), p(KING, 2, 8), p(GOLD, 4, 9), p(GOLD, 5, 9), p(SILVER, 6, 8)),
        plyCap = 30,
        source = "shougi.jp/senpou/hirame/（本文『飛車を5筋に振り、右銀を3八に、玉を2八に、左金を5九に左銀を6八に動かす』『この囲いが平目に似ていることから「平目」という名前が付いた』。掲載図とも一致を確認。囲いを内包する戦法だが、他の囲いタグとの部分集合関係は本ファイルの範囲外(囲いはACHIEVEMENT_CASTLES、戦法はSTRATEGY_DEFSと別の達成集合で管理しているため)）",
        samples = listOf(
            sample("成立する手順", "2h5h 1c1d 5i4h 9c9d 6i5i 8c8d 4h3h 7c7d 3h2h 6c6d 3i3h 5c5d 7i6h 4c4d", matches = true),
            sample("成立しない近い手順", "2h5h 1c1d 5i4h 9c9d 6i5i 8c8d 4h3h 7c7d 3h2h 6c6d 3i3h 5c5d", matches = false),
        ),
    ),
    PlacementDef(
        name = "矢倉",
        slug = "yagura-strategy",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(SILVER, 7, 7)),
        plyCap = 24,
        conditions = listOf(
            NoSwingAfter(NoSwingAfter.Basis.ACHIEVEMENT, plies = 20),
            NoBishopExchangeBeforeAchievement,
        ),
        source = "ja.wikipedia.org/wiki/矢倉囲い（本文『▲7七銀と受け』が矢倉の骨格。完成形『玉を8八に、左金を7八、右金を6七に、左銀を7七に移動させたもの』のうち、囲いの完成を待たずに戦型と分かる7七銀だけを厳密照合の対象とした。完成形の判定は囲いタグ側が担う）",
        samples = listOf(
            sample("成立する手順", "7g7f 8c8d 7i6h 8d8e 6h7g", matches = true),
            sample("成立しない近い手順", "7g7f 3c3d 7i6h 8b4b 6h7g 1c1d 3i4h 9c9d 1g1f 7c7d 9g9f 6c6d 5i5h 5a6b 5h6h 6b7c 4g4f 7a6b 4h4g 8c8d", matches = false),
        ),
    ),
    PlacementDef(
        name = "雁木",
        slug = "gangi",
        kind = OpeningKind.STRATEGY,
        required = listOf(p(GOLD, 7, 8), p(SILVER, 6, 7), p(GOLD, 5, 8)),
        forbidden = listOf(p(SILVER, 7, 7)),
        plyCap = 40,
        conditions = listOf(NoSwingAfter(NoSwingAfter.Basis.ACHIEVEMENT, plies = 20)),
        source = "ja.wikipedia.org/wiki/雁木囲い（本文『2010年代には、居飛車で左銀を6七に置く形の囲いを総称して雁木囲い』＝現代の雁木は左銀6七が定義の核で、旧型の『6七銀、5七銀、7八金、5八金の金銀4枚』のうち右銀5七は求めない。左銀6七だけでは相居飛車の他の駒組みとも重なるため、金2枚を伴う形を厳密形とした。7七に銀が居ないことは本文『7七に銀がいないため、引き角にしなくても初期位置の8八のまま角を攻めに使える（居角）ことが大きな特徴』に基づく）",
        samples = listOf(
            sample("成立する手順", "7g7f 8c8d 6g6f 8d8e 7i6h 3c3d 6h6g 2b3c 6i7h 7a6b 4i5h 6c6d", matches = true),
            sample("成立しない近い手順", "7g7f 8c8d 6g6f 8d8e 7i6h 3c3d 6h6g 2b3c 5g5f 7a6b 3i4h 6c6d", matches = false),
        ),
    ),
)

val PLACEMENT_DEFS: List<PlacementDef> = CASTLE_DEFS + PLACEMENT_STRATEGY_DEFS
