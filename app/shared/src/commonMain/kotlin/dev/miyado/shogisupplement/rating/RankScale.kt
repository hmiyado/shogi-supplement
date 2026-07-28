package dev.miyado.shogisupplement.rating

/**
 * 段級位ピッカーの目盛り（最低級 [maxKyu]級 〜 九段）。
 *
 * サービスによって申告できる段級位の下限が異なるため
 * （将棋ウォーズ=30級〜、棋桜=どのルールも10級〜）、下限をパラメータにして
 * ラベル列とインデックス変換をここに集約する。ピッカーのインデックスは
 * 0=[maxKyu]級 … [maxKyu]-1=1級, [maxKyu]=初段 … [maxKyu]+8=九段。
 */
class RankScale(val maxKyu: Int) {
    init { require(maxKyu in 1..30) { "最低級は 1〜30 の範囲です: $maxKyu" } }

    /** ピッカー表示用ラベル（[maxKyu]級〜1級・初段〜九段）。 */
    val labels: List<String> =
        (maxKyu downTo 1).map { ShogiRank.Kyu(it).toDisplayString() } +
            (1..9).map { ShogiRank.Dan(it).toDisplayString() }

    /** ピッカーのインデックス → [ShogiRank]。 */
    fun fromIndex(index: Int): ShogiRank {
        require(index in labels.indices) { "インデックスが範囲外です: $index" }
        return if (index < maxKyu) ShogiRank.Kyu(maxKyu - index) else ShogiRank.Dan(index - maxKyu + 1)
    }

    /**
     * [ShogiRank] → ピッカーのインデックス。
     * この目盛りの下限より低い級（例: maxKyu=10 の目盛りに 30級）は
     * 最下位の目盛りへ丸める（保存済みデータが目盛り外でもピッカーを壊さない）。
     */
    fun toIndex(rank: ShogiRank): Int = when (rank) {
        is ShogiRank.Kyu -> (maxKyu - rank.kyu).coerceAtLeast(0)
        is ShogiRank.Dan -> maxKyu - 1 + rank.dan
    }
}
