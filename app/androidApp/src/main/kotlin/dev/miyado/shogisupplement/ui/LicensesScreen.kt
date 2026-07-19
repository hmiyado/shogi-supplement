package dev.miyado.shogisupplement.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import dev.miyado.shogisupplement.text.AppStrings
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withContext

/**
 * OSSライセンス一覧画面。
 * 冒頭に本アプリ自体（GPLv3）と同梱エンジン（やねうら王・Háo、いずれもGPLv3）の
 * 手書きヘッダを明記し、続けて AboutLibraries による依存OSS一覧を表示する。
 * 一覧データは `./gradlew :androidApp:exportLibraryDefinitions` で生成した
 * res/raw/aboutlibraries.json（コミット済み）から同期読み込みする。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // rememberLibraries は produceState 由来の非同期読み込みのため、
    // 決定的に描画できる同期構築を使う（VRT の安定性のため）
    val libraries = remember {
        Libs.Builder().withContext(context).build()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ライセンス") },
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
            header = {
                item { LicensesHeader() }
            },
        )
    }
}

/**
 * ライセンス画面冒頭の手書きヘッダ。
 * 本アプリ・やねうら王・Háo評価関数がいずれも GPLv3 であることを明記する。
 */
@Composable
fun LicensesHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = "本アプリのライセンス",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "将棋サプリは GNU General Public License v3.0（GPLv3）のもとで" +
                "公開されるオープンソースソフトウェアです。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "同梱している将棋エンジン・評価関数",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "・やねうら王（YaneuraOu） — GPLv3\n" +
                "・Háo 評価関数 — GPLv3",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "フォントライセンス",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "本アプリは以下のフォントを SIL Open Font License 1.1（OFL-1.1）のもとで使用しています。" +
                "OFL の全文は assets/font_licenses/ に同梱しています。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "・Shippori Mincho — SIL Open Font License 1.1\n" +
                "・IBM Plex Sans JP — SIL Open Font License 1.1\n" +
                "・IBM Plex Mono — SIL Open Font License 1.1",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "使用しているOSSライブラリ",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
