# iOSリリース（fastlane・ローカル実行専用）

`app/iosApp/fastlane/` に置いた fastlane で TestFlightアップロードと審査提出を行う。
**CIには載せない・署名関連の秘密はGitHubに置かない方針**。
署名・APIキー認証・ビルド番号の取得元は `scripts/upload_testflight.sh` を踏襲している。

## 現状: 2本立て運用

fastlaneで実リリースが1回通るまでは、既存の `scripts/upload_testflight.sh` と
**並存させる**。ビルド番号の源（gradle.properties の `shogisupplement.versionCode`）・
署名方式（手動署名・Apple Distribution・プロファイル名「shogisup ios appstore」）は
共通だが、アップロード方式は違う（下記「upload_testflight.shとの違い」参照）。

## セットアップ（初回のみ）

### Ruby / bundler

Ruby 3.4.5 で動作確認済み。`ruby -v` で確認し、無ければ任意の方法で用意する。

```sh
cd app/iosApp
bundle config set --local path 'vendor/bundle'   # .bundle/config に記録済み・再実行不要
bundle install
```

`vendor/bundle` は `.gitignore` の `app/iosApp/vendor/` に含まれるため、
Sentry/FirebaseのXCFrameworkと同じ扱いでコミットされない。

### ASC APIキー

`fastlane/Fastfile` 内の `KEY_ID`（`BM62Q97564`）・`ISSUER_ID` は
`scripts/upload_testflight.sh` と同じ値で、秘密ではない。秘密なのは `.p8` の中身のみ。
ファイルの場所は実行時に環境変数 `ASC_KEY_PATH` で渡す:

```sh
ASC_KEY_PATH=/path/to/AuthKey_BM62Q97564.p8 bundle exec fastlane ios beta
```

**`.p8` はリポジトリに絶対に置かない・コピーしない**。

### エンジン非同梱ビルドの前提資材

`vendor/Sentry-Dynamic.xcframework`・`vendor/Firebase/*.xcframework` が無いとアーカイブが
リンクエラーになる（いずれも `.gitignore` 対象・都度取得する運用）。

```sh
./scripts/fetch-sentry.sh
./scripts/fetch-firebase.sh
```

## lane

### `bundle exec fastlane ios beta`

`xcodegen generate` → ビルド番号を `gradle.properties` から取得 →
`build_app`（gym。scheme `iosApp-Engineless` / configuration `Release-Engineless` /
手動署名・Apple Distribution・プロファイル名「shogisup ios appstore」。**ローカルに
.ipaを書き出すだけ**で、この時点ではまだASCへは送らない） →
`pilot`（`app_store_connect_api_key` で認証・`skip_waiting_for_build_processing: true`。
**ここで初めてTestFlightへアップロードする**） →
dSYMをSentryへ送信（`sentry-cli` が無いか `SENTRY_AUTH_TOKEN` 未設定ならスキップ。
`upload_testflight.sh` と同じガード）。

### `bundle exec fastlane ios release`

`beta` の後に `deliver`（`fastlane/metadata/ja-JP/` と `fastlane/screenshots/ja-JP/` を
アップロードし `submit_for_review: true`。`automatic_release: false` なので
**審査通過後の公開はApp Store Connect側で手動操作**が必要）。

### upload_testflight.shとの違い（重要）

`upload_testflight.sh` は `ExportOptions-testflight.plist`（`destination: upload`）を使い、
**xcodebuildのexportArchive自体がTestFlightへの直接アップロードを兼ねる**（pilotは使わない）。
fastlane版はpilotを明示的なアップロード手段にするため、`Fastfile`内の`EXPORT_OPTIONS`は
同じplistを流用せず`destination: export`のインラインhashにしてある。**ExportOptions-testflight.plist
をそのままfastlaneのexport_optionsに渡してはいけない**（渡すとexportの時点で
実アップロードが走ってしまう）。

## メタデータ・スクリーンショット

`fastlane/metadata/ja-JP/`（name・subtitle・promotional_text・description・keywords・
release_notes・support_url・privacy_url）は、App Store Connectに2026-07-19時点で
登録済みの文言（1.1のリリースノートのみ新規）をもとに作成した。
`fastlane/screenshots/ja-JP/` の7枚（1284×2778・6.5インチ）はASCへ提出済みの実機画像。


## 次回リリースで残る手順（実弾検証）

1. `bundle exec fastlane ios beta` を通しで実行し、TestFlightへ実際にアップロードされる
   ことを確認する（`upload_testflight.sh`との結果比較ができるとなお良い）
2. 問題なければ `fastlane/metadata`・`fastlane/screenshots` の内容を確定させたうえで
   `bundle exec fastlane ios release` を実行し、審査提出まで通す
3. 両方が安定して通ったら `scripts/upload_testflight.sh` の扱い（廃止するか残すか）を判断する
