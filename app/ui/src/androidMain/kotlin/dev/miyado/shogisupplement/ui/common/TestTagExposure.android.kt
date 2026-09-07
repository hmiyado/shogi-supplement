package dev.miyado.shogisupplement.ui.common

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Why not BuildConfig.DEBUG: :ui はBuildConfigを生成していない。判定したいのは
 * 「リリース版のアクセシビリティツリーに内部識別子を載せない」ことで、debuggableフラグは
 * その線と一致する（リリースビルドでは落ちる）。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.exposeTestTags(): Modifier {
    val flags = LocalContext.current.applicationInfo.flags
    return if (flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        semantics { testTagsAsResourceId = true }
    } else {
        this
    }
}
