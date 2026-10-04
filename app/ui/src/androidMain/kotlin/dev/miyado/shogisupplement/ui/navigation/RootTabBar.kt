package dev.miyado.shogisupplement.ui.navigation

import androidx.compose.runtime.Composable
import dev.miyado.shogisupplement.navigation.RootTab

@Composable
actual fun PlatformRootTabBar(selected: RootTab, onSelect: (RootTab) -> Unit, onAddGame: () -> Unit) = MaterialRootTabBar(selected, onSelect)

actual val rootTabsOverlayContent: Boolean = false

@Composable
actual fun PlatformGlassAction(symbol: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) = StandardAction(label, icon, onClick)
