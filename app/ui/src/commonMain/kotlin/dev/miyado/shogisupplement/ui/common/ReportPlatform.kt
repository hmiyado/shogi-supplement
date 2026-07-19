package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable

/**
 * ReportScreen の検討モード終了用システムバック処理。
 *
 * androidx.activity.compose.BackHandler は Android 専用 API のため expect/actual 化している。
 * Android: BackHandler へ委譲。
 * iOS: no-op（iOS にシステムバックの概念がないため。検討モード終了はUIボタンで行う）。
 */
@Composable
expect fun ReportBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * 解析日時表示用フォーマッタ（"yyyy/MM/dd HH:mm"）。
 *
 * java.text.SimpleDateFormat は JVM専用で commonMain から使えないため expect/actual 化
 * している。ReportScreen の GameInfoDialog と GameCard の両方から共用する共通フォーマッタ。
 */
expect fun formatDateTime(epochSeconds: Long): String
