package dev.miyado.shogisupplement.ui.theme

import androidx.compose.ui.text.font.FontFamily

// DESIGN.mdの書体指定（Shippori Mincho/IBM Plex Sans JP/IBM Plex Mono）からの逸脱＝
// システムフォントへフォールバックする。要承認。
//
// Why not compose resourcesの非同期ロード: [ShipporiMinchoFamily]等はcommonMainの
// トップレベルvalとしてモジュール初期化時に同期評価される。Android/iOS実装が使う
// 同期ロード（バイト読み込みをrunBlockingで待つ）はwasmJsにactualが無く使えず、
// フォント読み込み自体は本来ネットワーク経由の非同期I/Oのため、対応するには
// Theme.kt側の再構成（Composable化・読み込み完了までのフォールバック表示状態の追加）が要る。
// Why not 同梱バイトの埋め込み: 実書体は合計8MB超で、Kotlinソースへのbase64埋め込みは
// ビルド時間・バンドルサイズの両方を悪化させる。

internal actual fun buildShipporiMinchoFamily(): FontFamily = FontFamily.Serif

internal actual fun buildIbmPlexSansJpFamily(): FontFamily = FontFamily.SansSerif

internal actual fun buildIbmPlexMonoFamily(): FontFamily = FontFamily.Monospace
