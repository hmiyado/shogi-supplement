package dev.miyado.shogisupplement.ui.forceupdate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.IbmPlexMonoFamily
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/** 強制アップデートを全画面表示する。Why not 戻る導線: 制限中に他の画面へ進めない契約のため。 @param message お知らせ文。 @param storeUrl ストアURL。 @param versionName バージョン名。 @param buildNumber ビルド番号。 */
@Composable
fun ForceUpdateScreen(
    message: String?,
    storeUrl: String?,
    versionName: String,
    buildNumber: Int,
    onOpenStore: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = AppStrings.FORCE_UPDATE_TITLE,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = AppStrings.FORCE_UPDATE_BODY,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = AppStrings.FORCE_UPDATE_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.shogiColors.ink2,
            )
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.shogiColors.ink2,
                )
            }
            Spacer(Modifier.height(26.dp))
            if (!storeUrl.isNullOrBlank()) {
                Button(
                    onClick = onOpenStore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(AppStrings.FORCE_UPDATE_OPEN_STORE)
                }
                Spacer(Modifier.height(16.dp))
            }
            Row {
                Text(
                    text = AppStrings.FORCE_UPDATE_VERSION_PREFIX,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.shogiColors.ink3,
                )
                Text(
                    text = AppStrings.forceUpdateVersionValue(versionName, buildNumber),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = IbmPlexMonoFamily),
                    color = MaterialTheme.shogiColors.ink3,
                )
            }
        }
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewForceUpdateScreen() {
    ShogiTheme {
        ForceUpdateScreen(
            message = null,
            storeUrl = "https://play.google.com/store/apps/details?id=dev.miyado.shogisupplement",
            versionName = "1.2.0",
            buildNumber = 42,
        )
    }
}

@Preview
@Composable
private fun PreviewForceUpdateScreenNoStoreUrl() {
    ShogiTheme {
        ForceUpdateScreen(
            message = null,
            storeUrl = null,
            versionName = "1.2.0",
            buildNumber = 42,
        )
    }
}

@Preview
@Composable
private fun PreviewForceUpdateScreenWithMessage() {
    ShogiTheme {
        ForceUpdateScreen(
            message = "本日3時よりメンテナンスを予定しています。",
            storeUrl = "https://play.google.com/store/apps/details?id=dev.miyado.shogisupplement",
            versionName = "1.2.0",
            buildNumber = 42,
        )
    }
}

@Preview
@Composable
private fun PreviewForceUpdateScreenDark() {
    ShogiTheme(themeMode = "dark") {
        ForceUpdateScreen(
            message = null,
            storeUrl = "https://play.google.com/store/apps/details?id=dev.miyado.shogisupplement",
            versionName = "1.2.0",
            buildNumber = 42,
        )
    }
}
