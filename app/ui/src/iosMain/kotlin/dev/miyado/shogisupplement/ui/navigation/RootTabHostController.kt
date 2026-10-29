package dev.miyado.shogisupplement.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeUIViewController
import dev.miyado.shogisupplement.navigation.RootTab
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSProcessInfo
import platform.UIKit.*
import platform.darwin.NSObject

internal const val RootTabActionWidthPoints = 72

internal val LocalNativeRootTabs = staticCompositionLocalOf<NativeRootTabs> {
    error("Native root tab host is required")
}

@OptIn(ExperimentalForeignApi::class)
internal class NativeRootTabs {
    var onSelect: ((RootTab) -> Unit)? = null
    private val delegate = object : NSObject(), UITabBarControllerDelegateProtocol {
        override fun tabBarController(tabBarController: UITabBarController, didSelectViewController: UIViewController) {
            RootTab.entries.getOrNull(tabBarController.selectedIndex.toInt())?.let { onSelect?.invoke(it) }
        }
    }
    val controller = UITabBarController().apply {
        view.backgroundColor = UIColor.clearColor
        view.hidden = true
        this.delegate = this@NativeRootTabs.delegate
    }

    fun setVisible(visible: Boolean) {
        controller.view.hidden = !visible
        controller.parentViewController?.view?.setNeedsLayout()
    }

    fun update(selected: RootTab, titles: List<String>, tint: Color, onSelect: (RootTab) -> Unit) {
        this.onSelect = onSelect
        if (controller.viewControllers.isNullOrEmpty()) {
            controller.viewControllers = RootTab.entries.map { tab ->
                UIViewController().apply {
                    view.backgroundColor = UIColor.clearColor
                    view.userInteractionEnabled = false
                    val symbol = when (tab) {
                        RootTab.HOME -> "house"
                        RootTab.GAMES -> "list.bullet.rectangle"
                        RootTab.REPERTOIRE -> "book"
                    }
                    tabBarItem = UITabBarItem(title = titles[tab.ordinal], image = UIImage.systemImageNamed(symbol), tag = tab.ordinal.toLong())
                }
            }
        }
        controller.viewControllers?.filterIsInstance<UIViewController>()?.forEachIndexed { index, child ->
            child.tabBarItem.title = titles[index]
        }
        controller.selectedIndex = selected.ordinal.toULong()
        controller.tabBar.tintColor = UIColor(red = tint.red.toDouble(), green = tint.green.toDouble(), blue = tint.blue.toDouble(), alpha = 1.0)
        controller.parentViewController?.view?.setNeedsLayout()
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun rootTabHostController(content: @Composable () -> Unit): UIViewController {
    val tabs = NativeRootTabs()
    val compose = ComposeUIViewController {
        CompositionLocalProvider(LocalNativeRootTabs provides tabs) { content() }
    }
    return RootTabHostController(compose, tabs.controller)
}

@OptIn(ExperimentalForeignApi::class)
private class NativeTabTouchView : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    override fun hitTest(point: CValue<CGPoint>, withEvent: UIEvent?): UIView? {
        val target = super.hitTest(point, withEvent) ?: return null
        var ancestor: UIView? = target
        while (ancestor != null && ancestor !== this) {
            if (ancestor is UIControl) return target
            ancestor = ancestor.superview
        }
        return null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class RootTabHostController(
    private val compose: UIViewController,
    private val tabs: UITabBarController,
) : UIViewController(nibName = null, bundle = null) {
    private val tabTouchView = NativeTabTouchView()
    private var appliedInsets: HorizontalInsets? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.clearColor
        addChildViewController(compose)
        view.addSubview(compose.view)
        compose.didMoveToParentViewController(this)
        addChildViewController(tabs)
        tabTouchView.backgroundColor = UIColor.clearColor
        view.addSubview(tabTouchView)
        tabTouchView.addSubview(tabs.view)
        tabs.didMoveToParentViewController(this)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        compose.view.setFrame(view.bounds)
        // Duoの縦型バーは96ptを超えるため、ネイティブコンテナを下端だけに閉じ込めない。
        view.bounds.useContents {
            tabTouchView.setFrame(CGRectMake(0.0, 0.0, (size.width - RootTabActionWidthPoints).coerceAtLeast(0.0), size.height))
        }
        tabs.view.setFrame(tabTouchView.bounds)
        tabs.view.layoutIfNeeded()
        updateComposeInsets()
    }
    private fun updateComposeInsets() {
        val insets = if (tabs.view.hidden || tabs.viewControllers.isNullOrEmpty()) {
            HorizontalInsets(0.0, 0.0)
        } else {
            val contentFrame = if (NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }) {
                tabs.contentLayoutGuide.layoutFrame
            } else {
                tabs.selectedViewController?.view?.safeAreaLayoutGuide?.layoutFrame ?: tabs.view.bounds
            }
            // システムの安全領域は既にComposeにも届くため、バーが追加した横幅だけを足す。
            val boundsWidth = tabs.view.bounds.useContents { size.width }
            val nativeInsets = tabs.view.safeAreaInsets
            contentFrame.useContents {
                HorizontalInsets(
                    (origin.x - nativeInsets.useContents { left }).coerceAtLeast(0.0),
                    (boundsWidth - origin.x - size.width - nativeInsets.useContents { right }).coerceAtLeast(0.0),
                )
            }
        }
        if (insets != appliedInsets) {
            appliedInsets = insets
            compose.setAdditionalSafeAreaInsets(UIEdgeInsetsMake(0.0, insets.left, 0.0, insets.right))
        }
    }

    private data class HorizontalInsets(val left: Double, val right: Double)

}
