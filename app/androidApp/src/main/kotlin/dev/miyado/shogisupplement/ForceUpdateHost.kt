package dev.miyado.shogisupplement

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.miyado.shogisupplement.policy.currentBuildNumber
import dev.miyado.shogisupplement.ui.forceupdate.ForceUpdateScreen

/** 強制アップデートのゲート。ブロック時はcontentを描画せず、ForceUpdateScreenだけを表示する。未判定中はcontentを表示する。 */
@Composable
fun ForceUpdateHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ShogiApp
    val decision by app.forceUpdateDecision.collectAsState()

    if (decision?.blocked == true) {
        BackHandler {}
        ForceUpdateScreen(
            message = decision?.message,
            storeUrl = decision?.storeUrl,
            versionName = BuildConfig.VERSION_NAME,
            buildNumber = currentBuildNumber(),
            onOpenStore = {
                decision?.storeUrl?.let { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        )
    } else {
        content()
    }
}
