package dev.miyado.shogisupplement.ui.license

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * OSSライセンス画面（iOS向け簡易版）。
 *
 * Android版（androidApp/ui/LicensesScreen.kt）は AboutLibraries が生成した依存関係の
 * 完全な一覧を表示するが、その仕組み（Gradle プラグイン＋JSON エクスポート）は
 * Android専用のため iOS には持ち込めない。GPLv3 の表示義務を満たす最小限として、
 * 本アプリ自体・同梱エンジン（やねうら王・Háo）・主要な依存OSSライブラリを
 * 静的テキストで列挙する。冒頭ヘッダの文言は Android版 LicensesHeader と揃えてある。
 *
 * URL を開く実処理はプラットフォーム側（iOS の MainViewController.kt）が
 * [onOpenSourceRepo] として渡す（commonMain は URL オープンAPIを持たないため）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseInfoScreen(
    onBack: () -> Unit,
    onOpenSourceRepo: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.LICENSE_SCREEN_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.BACK,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = AppStrings.LICENSE_APP_HEADER,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = AppStrings.LICENSE_APP_BODY,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = AppStrings.LICENSE_ENGINE_HEADER,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = AppStrings.LICENSE_ENGINE_BODY,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = AppStrings.LICENSE_OSS_HEADER,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = AppStrings.LICENSE_OSS_BODY,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = AppStrings.LICENSE_SOURCE_HEADER,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = AppStrings.LICENSE_SOURCE_URL,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSourceRepo),
            )
        }
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewLicenseInfoScreen() {
    ShogiTheme {
        Surface {
            LicenseInfoScreen(onBack = {}, onOpenSourceRepo = {})
        }
    }
}

@Preview
@Composable
private fun PreviewLicenseInfoScreenDark() {
    ShogiTheme(themeMode = "dark") {
        Surface {
            LicenseInfoScreen(onBack = {}, onOpenSourceRepo = {})
        }
    }
}
