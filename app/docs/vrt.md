# VRT（画面のスクリーンショット比較）の回し方

Roborazziで画面を描画し、`androidApp/src/test/snapshots/` の画像と比較する。

## 開発ループでは変更に関係する画面だけ流す

全件は30〜70分かかり、テストJVMがOOMしやすい。**変更した画面のテストクラスだけ**を指定する。

```sh
cd app
./gradlew :androidApp:verifyRoborazziDebug --tests "*ReportViewer*"
```

画面と対応するテストクラスは `androidApp/src/test/kotlin/**/…ScreenshotTest.kt` にある。
どれが関係するか迷ったら、変更したComposable名でテストを検索する。

意図した見た目の変更なら記録し直してから差分を目視する。

```sh
./gradlew :androidApp:recordRoborazziDebug --tests "*ReportViewer*"
git diff --stat androidApp/src/test/snapshots
```

**記録し直したら、変更したつもりのない画像が混ざっていないか必ず確認する**
（描画のゆらぎで無関係な画像が書き換わることがある。その場合は `git checkout` で戻す）。

## 全件はCIに任せる

`.github/workflows/vrt.yml` がPull Requestで全件を検証する。失敗時は比較画像
（期待・実際・差分）が成果物として残るので、そこで回帰かどうかを判断する。

手元で全件を流したいときは、CIと同じコマンドを使う。

```sh
./gradlew :androidApp:verifyRoborazziDebug
```

## goldenを追加するとき

テストJVMのメモリは画像の枚数に比例して逼迫する。追加後に一括実行がOOMで落ちるように
なったら、`androidApp/build.gradle.kts` の `maxHeapSize` と `setForkEvery` を見直す。

**スピナーなど終わらないアニメーションを含む状態は `createComposeRule` で撮る**
（`GameRestoreScreenScreenshotTest` や `ReportViewerScreenshotTest.captureViaComposeRule` が例）。
ラムダ版の `captureRoboImage { }` はメインルーパーがidleになるまで待つため、
終わらないアニメーションがあるとテストが停止したまま戻らない。
