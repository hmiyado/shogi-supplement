package dev.miyado.shogisupplement.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import dev.miyado.shogisupplement.navigation.RootTab
import platform.UIKit.*
import platform.darwin.NSObject
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents

@OptIn(ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformRootTabBar(selected: RootTab, onSelect: (RootTab) -> Unit, onAddGame: () -> Unit) {
    val tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val callback by rememberUpdatedState(onSelect)
    val delegate = remember { object : NSObject(), UITabBarControllerDelegateProtocol {
        override fun tabBarController(tabBarController: UITabBarController, didSelectViewController: UIViewController) {
            RootTab.entries.getOrNull(tabBarController.selectedIndex.toInt())?.let(callback)
        }
    } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
    Box(Modifier.weight(1f)) {
    UIKitViewController(factory = {
        UITabBarController().apply {
            view.backgroundColor = UIColor.clearColor
            viewControllers = RootTab.entries.map { tab ->
                val symbol = when(tab) { RootTab.HOME -> "house"; RootTab.GAMES -> "list.bullet.rectangle"; RootTab.REPERTOIRE -> "book" }
                UIViewController().apply {
                    view.backgroundColor = UIColor.clearColor
                    tabBarItem = UITabBarItem(title = tab.title, image = UIImage.systemImageNamed(symbol), tag = tab.ordinal.toLong())
                }
            }
            this.delegate = delegate
        }
    }, update = { controller -> controller.selectedIndex = selected.ordinal.toULong(); controller.tabBar.tintColor = UIColor(red = tint.red.toDouble(), green = tint.green.toDouble(), blue = tint.blue.toDouble(), alpha = 1.0) },
        modifier = Modifier.fillMaxWidth().height(96.dp),
        properties = UIKitInteropProperties(placedAsOverlay = true, interactionMode = UIKitInteropInteractionMode.NonCooperative, isNativeAccessibilityEnabled = true))
    }
    Box(Modifier.height(96.dp).padding(end = 16.dp, bottom = 8.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        PlatformGlassAction("plus", "棋譜を追加する", androidx.compose.material.icons.Icons.Default.Add, onAddGame)
    }
    }
}

actual val rootTabsOverlayContent: Boolean = true

@OptIn(ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformGlassAction(symbol: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val callback by rememberUpdatedState(onClick)
    val tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
    androidx.compose.ui.viewinterop.UIKitView(
        factory = {
            UIButton.buttonWithType(UIButtonTypeSystem).apply {
                configuration = if (platform.Foundation.NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }) UIButtonConfiguration.glassButtonConfiguration() else UIButtonConfiguration.tintedButtonConfiguration()
                setImage(UIImage.systemImageNamed(symbol), forState = UIControlStateNormal)
                accessibilityLabel = label
                layer.cornerRadius = 28.0
                clipsToBounds = true
                addAction(UIAction.actionWithHandler { callback() }, forControlEvents = UIControlEventTouchUpInside)
            }
        },
        update = { it.tintColor = UIColor(red = tint.red.toDouble(), green = tint.green.toDouble(), blue = tint.blue.toDouble(), alpha = 1.0) },
        modifier = Modifier.size(56.dp),
        properties = UIKitInteropProperties(placedAsOverlay = true, interactionMode = UIKitInteropInteractionMode.NonCooperative, isNativeAccessibilityEnabled = true),
    )
}
