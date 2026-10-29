package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

// Why not 各Scaffoldに既定のまま任せる: 背景を画面の端まで通す環境（iOS）はノッチを含む
// insetを全画面へ同じ値で配る必要がある。引く箇所は1つに保つ——2箇所で引くと
// CMP 1.11.1のiOSはキーボードを開いたときに上が二重に空く。
/** Scaffoldに渡すinsets。未提供ならMaterial3の既定。 */
val LocalScaffoldContentInsets = compositionLocalOf<WindowInsets?> { null }

@Composable
fun scaffoldContentInsets(): WindowInsets =
    LocalScaffoldContentInsets.current ?: ScaffoldDefaults.contentWindowInsets

/** 下端はリストのcontentPaddingに含めるため、ここでは上端と左右だけを適用する。 */
@Composable
fun Modifier.scaffoldTopAndSides(padding: PaddingValues): Modifier {
    val direction = LocalLayoutDirection.current
    return padding(
        start = padding.calculateStartPadding(direction),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(direction),
    )
}
