package dev.miyado.shogisupplement.ui.common

import androidx.compose.runtime.Composable

/**
 * 検討モード終了用システムバック処理。androidx.activity.compose.BackHandler は Android専用APIのため
 * expect/actual化している。iOSはno-op（システムバックの概念が無く、終了操作はUIボタンで行うため）。
 */
@Composable
expect fun ReportBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** 解析日時表示用フォーマッタ（"yyyy/MM/dd HH:mm"）。SimpleDateFormatがJVM専用のためexpect/actual化している。 */
expect fun formatDateTime(epochSeconds: Long): String

/** 手動棋譜入力の対局日時初期値（端末ローカル時刻、"yyyy/MM/dd HH:mm"）。 */
expect fun currentLocalDateTime(): String

/** 月日のみの短縮表示（"M/d"）。[formatDateTime] と同じ理由でexpect/actual化している。 */
expect fun formatShortDate(epochSeconds: Long): String
