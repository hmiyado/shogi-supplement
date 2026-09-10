package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ウィンドウ幅の区分。境界値はMaterial 3のwindow size class（600dp / 840dp）に合わせる。
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/** 本文（カード・フォーム・一覧）に許す最大幅。DESIGN.mdのAdaptive Layoutが値の正。 */
val ContentMaxWidth: Dp = 600.dp

/** 2ペインで盤に許す高さの比率。1.0にしない理由: 盤が高さを使い切るとナビ行が画面外へ出る。 */
const val TwoPaneBoardHeightFraction = 0.75f

/** 2ペインへ切り替える下限幅。 */
val TwoPaneMinWidth: Dp = 840.dp

// Why not 測った幅だけで決める: 区分の境界はウィンドウ幅に対する定義で、ノッチの分だけ
// 狭くなった幅で判定すると横向きの電話が境界の手前で落ちる。
/** safe areaを外側で引いてから画面へ渡す環境（iOS）が、引く前の幅から求めた区分を入れる。 */
val LocalWindowWidthClass = compositionLocalOf<WindowWidthClass?> { null }

/** [LocalWindowWidthClass] があればそれを、無ければ [measured] から求める。 */
@Composable
fun rememberWindowWidthClass(measured: Dp): WindowWidthClass =
    LocalWindowWidthClass.current ?: windowWidthClassOf(measured)

fun windowWidthClassOf(width: Dp): WindowWidthClass = when {
    width < 600.dp -> WindowWidthClass.COMPACT
    width < TwoPaneMinWidth -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

/**
 * 内容の幅を[max]で止めて中央へ寄せる。[max]未満の幅では何も変えない。
 *
 * 画面全体ではなく内容にだけ掛ける: トップバー・区切り線・背景は画面の端まで通す。
 */
fun Modifier.adaptiveContentWidth(max: Dp = ContentMaxWidth): Modifier =
    this.fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
        // wrapContentWidthが最小幅を0まで緩めるため、上限まで広げ直さないと内容の実寸で止まる。
        .fillMaxWidth()
