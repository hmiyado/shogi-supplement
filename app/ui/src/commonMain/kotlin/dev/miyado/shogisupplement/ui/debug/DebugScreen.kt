package dev.miyado.shogisupplement.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.miyado.shogisupplement.text.AppStrings
import dev.miyado.shogisupplement.ui.theme.ShogiTheme
import dev.miyado.shogisupplement.ui.theme.shogiColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * DEBUGビルド限定のデバッグ画面（iOS。SettingsScreenの「デバッグ画面」行から遷移）。
 *
 * 現状の内容はWASMバイナリ（検討モードの端末内解析）の配信元URL切替のみ。実機
 * （`devicectl`起動）では環境変数 `KENTO_SITE_BASE_URL_OVERRIDE` を注入できないため、
 * この画面から入力・永続保存して起動方法を問わず切り替えられるようにする狙い
 * （実際の優先順位判定・保存はプラットフォーム側。iOSは
 * `IosDebugScreenHost`/`WasmSiteOverrideStore`、Swift側は `KentoAssetCache.swift`）。
 *
 * Androidの駒配置デバッグ（androidApp/.../DebugScreen.kt）とは別画面。Android側は
 * Context・NotificationManager等プラットフォームAPI依存が強く、この画面（:ui commonMain）へ
 * 統合するメリットが薄いため対象外とした。
 *
 * [siteBaseUrlInputInitial] は入力欄の初期値（保存済みならその値、無ければ空文字）。
 * [effectiveSiteBaseUrl]・[effectiveSiteBaseUrlSource] は現在実際に使われる値とその由来
 * （環境変数／保存値／本番。優先順位の実体はSwift側 `KentoAssetCache.siteBaseURL` にある。
 * ここは表示専用）。この関数自身は複製した内部状態を持たない
 * （保存・クリア後の反映は、更新後の値をこの関数へ渡し直すことで行う。楽観的にここで
 * 書き換えると、実際の優先順位判定（環境変数の有無等）とズレる可能性があるため）。
 * [onSave] は正規化・保存を行い、成功なら true を返す
 * （失敗＝不正なURL入力は保存せずエラー表示のみ）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    siteBaseUrlInputInitial: String,
    effectiveSiteBaseUrl: String,
    effectiveSiteBaseUrlSource: String,
    onSave: (String) -> Boolean,
    onClear: () -> Unit,
) {
    var input by remember { mutableStateOf(siteBaseUrlInputInitial) }
    // 保存直後の一時フィードバック用。表示種別だけを持ち、テキストはStatusSlotが解決する
    // （エラー/成功どちらでもスロットの高さを変えないため。DESIGN.mdのNo-jitter原則）。
    var status by remember { mutableStateOf<SaveStatus?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(AppStrings.DEBUG_SCREEN_TITLE) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                AppStrings.debugWasmSiteEffective(effectiveSiteBaseUrl, effectiveSiteBaseUrlSource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.shogiColors.ink2,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    status = null
                },
                label = { Text(AppStrings.DEBUG_WASM_SITE_FIELD_LABEL) },
                placeholder = { Text(AppStrings.DEBUG_WASM_SITE_PLACEHOLDER) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 固定高さのステータス表示スロット（保存成否でレイアウトの高さを変えない）。
            Column(modifier = Modifier.height(24.dp).padding(top = 4.dp)) {
                when (status) {
                    SaveStatus.INVALID -> Text(
                        AppStrings.DEBUG_WASM_SITE_INVALID,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.shogiColors.loss,
                    )
                    SaveStatus.SAVED -> Text(
                        AppStrings.DEBUG_WASM_SITE_SAVED,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    null -> Unit
                }
            }

            Spacer(Modifier.height(8.dp))

            Row {
                Button(
                    onClick = { status = if (onSave(input)) SaveStatus.SAVED else SaveStatus.INVALID },
                ) { Text(AppStrings.DEBUG_WASM_SITE_SAVE) }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        onClear()
                        input = ""
                        status = null
                    },
                ) { Text(AppStrings.DEBUG_WASM_SITE_CLEAR) }
            }
        }
    }
}

private enum class SaveStatus { INVALID, SAVED }

@Preview
@Composable
private fun PreviewDebugScreen() {
    ShogiTheme {
        Surface {
            DebugScreen(
                onBack = {},
                siteBaseUrlInputInitial = "",
                effectiveSiteBaseUrl = "https://shogi-supplement.miyado.dev/",
                effectiveSiteBaseUrlSource = AppStrings.DEBUG_WASM_SITE_SOURCE_PRODUCTION,
                onSave = { true },
                onClear = {},
            )
        }
    }
}

@Preview
@Composable
private fun PreviewDebugScreenOverridden() {
    ShogiTheme {
        Surface {
            DebugScreen(
                onBack = {},
                siteBaseUrlInputInitial = "http://127.0.0.1:8925/",
                effectiveSiteBaseUrl = "http://127.0.0.1:8925/",
                effectiveSiteBaseUrlSource = AppStrings.DEBUG_WASM_SITE_SOURCE_SAVED,
                onSave = { true },
                onClear = {},
            )
        }
    }
}
