package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

// 通常のラムダを別の親へ移すと状態が破棄されるため、配置をまたいで同じcontentを保持する。
@Composable
internal fun rememberMovablePane(content: @Composable () -> Unit): @Composable () -> Unit {
    val currentContent by rememberUpdatedState(content)
    return remember { movableContentOf { currentContent() } }
}
