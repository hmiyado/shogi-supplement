package dev.miyado.shogisupplement.rating

/**
 * 将棋の段級位表現。
 *
 * 級（[Kyu]）と段（[Dan]）を型で区別し、マジックエンコードを避ける。
 * UI での段級位ピッカーと DB への raw 値エンコードに使用。
 *
 * Note: lishogi 相当への換算（RatingConverter）は廃止。
 * 帯決定は申告レートではなく StrengthEstimator（実測悪手率）が担う。
 */
sealed class ShogiRank {
    /** 級位（1〜30級）。[kyu]=1 が一番強い（初段直下）、30 が最も低い。 */
    data class Kyu(val kyu: Int) : ShogiRank() {
        init { require(kyu in 1..30) { "級位は 1〜30 の範囲です: $kyu" } }
    }

    /** 段位（1〜9段）。[dan]=1 が初段。 */
    data class Dan(val dan: Int) : ShogiRank() {
        init { require(dan in 1..9) { "段位は 1〜9 の範囲です: $dan" } }
    }

    /** rating_rawへ保存する整数へ変換する。段は正、級は負、0は未申告を表す。変換式はここへ集約する。 */
    fun toRaw(): Int = when (this) {
        is Kyu -> -kyu
        is Dan -> dan
    }

    /** 段級位を慣用表記へ変換する。UI表示の変換規則をここへ集約する。 */
    fun toDisplayString(): String = when (this) {
        is Kyu -> "${kyu}級"
        is Dan -> "${DAN_KANJI[dan - 1]}段"
    }

    companion object {
        /** 段の漢字一覧（インデックス0=初段…インデックス8=九段）。 */
        private val DAN_KANJI = listOf("初", "二", "三", "四", "五", "六", "七", "八", "九")

        /**
         * rating_raw の整数エンコードから [ShogiRank] を復元する（[toRaw] の逆変換）。
         *
         * @param raw 段 = +1..+9、級 = -1..-30。範囲外（0 を含む）は null。
         */
        fun fromRaw(raw: Int): ShogiRank? = when {
            raw in 1..9 -> Dan(raw)
            -raw in 1..30 -> Kyu(-raw)
            else -> null
        }
    }
}
