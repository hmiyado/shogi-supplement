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

/**
 * 強制アップデートのゲート。[ShogiApp.forceUpdateDecision]（[MainActivity.onResume] が
 * 起動直後の初回resume・フォアグラウンド復帰のたびに更新する）を購読し、ブロック対象なら
 * [content] を一切コンポジションに含めず [ForceUpdateScreen] のみを全画面表示する
 * （戻るキーも [BackHandler] で吸収し抜けられなくする）。
 *
 * 取得失敗・キャッシュ無しの場合は checker 自体が fail-open（非ブロック）を返すため、
 * ここでは StateFlow の値をそのまま使うだけでよい。未チェック（null）の間は content を出す
 * （取得中に誤ってブロック画面を出さない）。
 */
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
