package dev.miyado.shogisupplement.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import dev.miyado.shogisupplement.ui.license.LicenseInfoScreen

/**
 * OSSライセンス画面（Android）。
 *
 * 実体は :ui commonMain の [LicenseInfoScreen]（Android/iOS共通）。
 * Android固有の責務は一覧データの読み込み（res/raw/aboutlibraries.json を
 * `./gradlew :androidApp:exportLibraryDefinitions` で生成・コミット済み）と
 * リポジトリURLを開く Intent の発行のみ。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit, onOpenSourceRepo: () -> Unit) {
    val context = LocalContext.current
    // rememberLibraries は produceState 由来の非同期読み込みのため、
    // 決定的に描画できる同期構築を使う（VRT の安定性のため）
    val libraries = remember {
        Libs.Builder().withContext(context).build()
    }
    LicenseInfoScreen(
        libraries = libraries,
        onBack = onBack,
        onOpenSourceRepo = onOpenSourceRepo,
    )
}
