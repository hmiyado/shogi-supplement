package dev.miyado.shogisupplement.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Scaffoldに渡すinsets。未提供ならMaterial3の既定。safe areaを外側で引き切った環境には
 * ゼロを渡す——CMP 1.11.1のiOSはキーボードを開くと消費の申し送りがScaffoldへ届かず、
 * 上のsafe areaが二重適用される（外側にconsumeWindowInsetsを足す方法では解消しない）。
 */
val LocalScaffoldContentInsets = compositionLocalOf<WindowInsets?> { null }

@Composable
fun scaffoldContentInsets(): WindowInsets =
    LocalScaffoldContentInsets.current ?: ScaffoldDefaults.contentWindowInsets
