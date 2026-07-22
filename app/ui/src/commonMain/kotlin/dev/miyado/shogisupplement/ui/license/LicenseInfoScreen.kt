package dev.miyado.shogisupplement.ui.license

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * OSSライセンス画面（Android/iOS共通の唯一の実装）。
 *
 * 本アプリ（GPLv3）・同梱エンジン（やねうら王・Háo）・フォントライセンスを
 * 固定ヘッダとして表示し、続けて依存OSSの完全な一覧を
 * [libraries]（AboutLibraries の [Libs]）から [LibrariesContainer] で描画する。末尾に
 * ソースリポジトリへのリンクを置く。
 *
 * [libraries] の読み込み手段はプラットフォーム側の責務（Android は
 * `Libs.Builder().withContext(context)`、iOS は compose resources から読んだ JSON を
 * `Libs.Builder().withJson()`）。ここではパラメータとして受け取るだけにして、
 * VRT で決定的に描画できるようにする。
 *
 * URL を開く実処理もプラットフォーム側が [onOpenSourceRepo] として渡す
 * （commonMain は URL オープンAPIを持たないため）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseInfoScreen(
    libraries: Libs?,
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
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
            text = AppStrings.LICENSE_FONT_HEADER,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = AppStrings.LICENSE_FONT_INTRO,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = AppStrings.LICENSE_FONT_BODY,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = AppStrings.LICENSE_OSS_LIST_HEADER,
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
