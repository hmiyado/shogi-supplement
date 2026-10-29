package dev.miyado.shogisupplement.ui.forceupdate


import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

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
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
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
                .padding(24.dp)
                .adaptiveContentWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.force_update_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(Res.string.force_update_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = UiStrings.FORCE_UPDATE_NOTE,
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
                    Text(stringResource(Res.string.force_update_open_store))
                }
                Spacer(Modifier.height(16.dp))
            }
            Row {
                Text(
                    text = stringResource(Res.string.force_update_version_prefix),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.shogiColors.ink3,
                )
                Text(
                    text = UiStrings.forceUpdateVersionValue(versionName, buildNumber),
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
    val labelPreviewMaintenanceNotice = stringResource(Res.string.preview_maintenance_notice)

    ShogiTheme {
        ForceUpdateScreen(
            message = labelPreviewMaintenanceNotice,
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
