package dev.miyado.shogisupplement.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.navigation.RootTab
import dev.miyado.shogisupplement.ui.common.LocalScaffoldContentInsets
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import androidx.compose.ui.platform.LocalDensity

val LocalRootTabBottomPadding = staticCompositionLocalOf { 0.dp }

expect val rootTabsOverlayContent: Boolean

val LocalRootTabHost = staticCompositionLocalOf { false }
val LocalRootDetailChanged = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun RootTabShell(selected: RootTab, visible: Boolean, screenKey: String, onSelect: (RootTab) -> Unit, holder: androidx.compose.runtime.saveable.SaveableStateHolder = rememberSaveableStateHolder(), completed: Boolean = false, onOpenCompleted: () -> Unit = {}, onResumeAnalysis: (String) -> Unit = {}, onAddGame: () -> Unit = {}, content: @Composable () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val sessions by dev.miyado.shogisupplement.pipeline.InProgressAnalysisRegistry.shared.sessions.collectAsState()
    val show = visible && !editing
    val insets = scaffoldContentInsets()
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        if (show && completed) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("解析が完了しました", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onOpenCompleted) { Text("開く") }
        }
        if (show && !completed && sessions.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("解析中…", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { sessions.values.firstOrNull()?.let { onResumeAnalysis(it.id) } }) { Text("開く") }
        }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalRootTabBottomPadding provides if (show && rootTabsOverlayContent) 96.dp else 0.dp, LocalRootTabHost provides true, LocalRootDetailChanged provides { editing = it },
                LocalScaffoldContentInsets provides if (show) WindowInsets(0, insets.getTop(density), 0, if (rootTabsOverlayContent) with(density) { 96.dp.roundToPx() } else 0) else insets) {
                holder.SaveableStateProvider(screenKey) { content() }
            }
        }
        if (show && !rootTabsOverlayContent) PlatformRootTabBar(selected, onSelect, onAddGame)
    }
    if (show && rootTabsOverlayContent) Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) { PlatformRootTabBar(selected, onSelect, onAddGame) }
    }
}

@Composable
expect fun PlatformRootTabBar(selected: RootTab, onSelect: (RootTab) -> Unit, onAddGame: () -> Unit)

@Composable
fun MaterialRootTabBar(selected: RootTab, onSelect: (RootTab) -> Unit) {
    NavigationBar {
        RootTab.entries.forEach { tab ->
            NavigationBarItem(selected = selected == tab, onClick = { onSelect(tab) },
                icon = { Icon(when(tab) { RootTab.HOME -> Icons.Default.Home; RootTab.GAMES -> Icons.AutoMirrored.Filled.List; RootTab.REPERTOIRE -> Icons.AutoMirrored.Filled.MenuBook }, contentDescription = null) },
                label = { Text(tab.title) })
        }
    }
}

@Composable
expect fun PlatformGlassAction(symbol: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit)

@Composable
fun StandardAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}
