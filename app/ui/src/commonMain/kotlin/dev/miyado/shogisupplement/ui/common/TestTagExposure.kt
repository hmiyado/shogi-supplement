package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * ダイアログやボトムシートの中身に、UI自動化から狙える識別子を出す。
 * Why not ルートで一度だけ有効にする: 別ウィンドウに描かれるためツリーがそこで切れる。
 */
@Composable
expect fun Modifier.exposeTestTags(): Modifier
