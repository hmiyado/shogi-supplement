package dev.miyado.shogisupplement.ui.license

import org.jetbrains.compose.resources.vectorResource

import dev.miyado.shogisupplement.ui.text.UiStrings

import org.jetbrains.compose.resources.stringResource
import dev.miyado.shogisupplement.ui.generated.resources.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.miyado.shogisupplement.ui.common.adaptiveContentWidth
import dev.miyado.shogisupplement.ui.common.scaffoldContentInsets
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/** OSSライセンス画面。ライブラリ一覧とソースリポジトリへのリンクを表示する。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseInfoScreen(
    libraries: Libs?,
    onBack: () -> Unit,
    onOpenSourceRepo: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = scaffoldContentInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.license_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_auto_mirrored_filled_arrow_back),
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .adaptiveContentWidth(),
            header = { item { LicenseInfoHeader() } },
            footer = { item { LicenseSourceFooter(onOpenSourceRepo = onOpenSourceRepo) } },
        )
    }
}

/**
 * ライセンス画面冒頭の固定ヘッダ。
 * 本アプリ・同梱エンジン（やねうら王・Háo）・フォントライセンスをこの順で明記し、
 * 続く依存OSS一覧の見出しで締める。
 */
@Composable
fun LicenseInfoHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.license_app_header),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = UiStrings.LICENSE_APP_BODY,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.license_engine_header),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = UiStrings.LICENSE_ENGINE_BODY,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.license_font_header),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = UiStrings.LICENSE_FONT_INTRO,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = UiStrings.LICENSE_FONT_BODY,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.license_oss_list_header),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** ライセンス画面末尾。依存OSS一覧のあとにソースリポジトリへのリンクを置く。 */
@Composable
fun LicenseSourceFooter(onOpenSourceRepo: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.license_source_header),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.license_source_url),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSourceRepo),
        )
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun PreviewLicenseInfoScreen() {
    ShogiTheme {
        Surface {
            LicenseInfoScreen(libraries = null, onBack = {}, onOpenSourceRepo = {})
        }
    }
}

@Preview
@Composable
private fun PreviewLicenseInfoScreenDark() {
    ShogiTheme(themeMode = "dark") {
        Surface {
            LicenseInfoScreen(libraries = null, onBack = {}, onOpenSourceRepo = {})
        }
    }
}
