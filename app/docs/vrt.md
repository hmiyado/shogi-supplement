# VRT（スクリーンショットテスト）

Roborazzi + Robolectric で JVM 上でレンダリングし、`androidApp/src/test/snapshots/` にゴールデン画像を保存する。

| 操作 | コマンド |
|------|---------|
| ゴールデン更新 | `./gradlew :androidApp:recordRoborazziDebug` |
| CI 照合（差分検出） | `./gradlew :androidApp:verifyRoborazziDebug` |
| テストのみ（照合なし） | `./gradlew :androidApp:testDebugUnitTest` |

## ゴールデンの所在と命名

- 正本はテストコード: `androidApp/src/test/kotlin/.../ui/*ScreenshotTest.kt` の各テストが
  `filePath = "src/test/snapshots/<画面>_<状態>.png"` で1枚ずつ撮影する。
  一覧が必要なときは `ls androidApp/src/test/snapshots/` か
  `grep -r "src/test/snapshots" androidApp/src/test` を見る
- 新しい画面・状態を追加するときは既存の `<画面>_<状態>` 命名に合わせる
  （例: `drill_result_with_eval` / `report_viewer_best_pv_mate`）
- 未使用ゴールデンを作らない: テストから参照されない PNG は削除する

## 注意

- `account_delete_dialog` は AlertDialog（別ウィンドウ）を含むため `captureScreenRoboImage`＋
  `createComposeRule` で撮影する（ComponentActivity は `ui-test-manifest` を
  debugImplementation にして解決）
- `licenses_screen` は `res/raw/aboutlibraries.json`（`./gradlew :androidApp:exportLibraryDefinitions`
  で生成・コミット）に依存する。依存ライブラリを追加・更新したら export を再実行し
  golden も更新すること
- レポート/ドリルのナビ行ラベルは `TextOverflow.MiddleEllipsis` を使い、長い手表記のときは
  「42手目 ▲６…（−350）」のように両端（手数プレフィックスと形勢サフィックス）を保護する
  （`KifuLineViewer.kt` にも同じ overflow を適用）
- `drill_result_with_eval_ply1` は No-jitter 検証用: 1手送った状態でナビラベルより下の
  Y座標が `drill_result_with_eval` と不変であることを確認する
