package dev.miyado.shogisupplement.ui.navigation

import dev.miyado.shogisupplement.ui.text.title

import org.jetbrains.compose.resources.stringResource

import org.jetbrains.compose.resources.vectorResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import dev.miyado.shogisupplement.navigation.RootTab
import platform.UIKit.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents

@OptIn(ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformRootTabBar(selected: RootTab, onSelect: (RootTab) -> Unit, onAddGame: () -> Unit) {
    val labelHomeOpenKif = stringResource(Res.string.home_open_kif)

    val tabTitles = RootTab.entries.map { it.title }
    val tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val host = LocalNativeRootTabs.current
    SideEffect { host.update(selected, tabTitles, tint, onSelect) }
    DisposableEffect(host) {
        host.setVisible(true)
        onDispose { host.setVisible(false); host.onSelect = null }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
    Spacer(Modifier.weight(1f))
    Box(Modifier.width(RootTabActionWidthPoints.dp).height(96.dp).padding(end = 16.dp, bottom = 8.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        PlatformGlassAction("plus", labelHomeOpenKif, vectorResource(Res.drawable.ic_filled_add), onAddGame)
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
