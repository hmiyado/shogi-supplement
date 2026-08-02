# E2E テスト（Maestro）

Maestro による実機（シミュレータ/エミュレータ）E2E。フローは `.maestro/` に置く。

## 位置づけ・実行タイミング

`app/docs/vrt.md` に記載の検証方針のとおり、開発ループは unit テストと VRT で JVM 内に
閉じる。実機E2E（本ドキュメントのMaestroフロー、および `connectedAndroidTest`／iOS UIテスト）
は**フェーズ最終の1回＋依存/manifest変更時のスモークに限定**し、常時CIには組み込まない。

- ビルド前提（iOS: vendor/フレームワーク・エンジンのネイティブビルド／Android: エミュレータ
  起動）が重く、Maestro自体の実行よりビルド待ちがボトルネックになりやすい
- 解析はオンデバイスの実エンジンで動くため1フロー完走に数十秒〜かかり、CIの通常フィードバック
  ループ（分オーダー）には向かない
- 画面ごとの回帰は既存のVRT（Roborazzi golden）でJVM内に閉じて検出できる。Maestroフローが
  拾うのは「実機上でOS・Compose・エンジンをまたいだ結線が壊れていないか」であり、
  日々のUI変更の検出手段としては使わない

## 前提

- **Maestro CLI 2.8.0**: `brew tap mobile-dev-inc/tap && brew install mobile-dev-inc/tap/maestro`
  （`brew install --cask maestro` はMaestro Studio、CLIとは別物なので注意）
- CMPのCompose semanticsはiOSのアクセシビリティツリーにそのまま見えるため、iOS側は追加設定なしで
  `testTag` が `accessibilityIdentifier` として露出する。Androidは既定では露出しないため、
  `MainActivity` で `testTagsAsResourceId` を **DEBUGビルドに限り** 有効化している
  （`app/androidApp/src/main/kotlin/dev/miyado/shogisupplement/MainActivity.kt`）。
  現状のフローはテキストセレクタのみで組んでおり、この設定への依存はまだ無い

### iOS

1. Xcode・iOS Simulatorが利用可能なこと
2. `app/iosApp/vendor/`（Sentry・Firebase XCFramework）と `app/iosApp/engine/build/`
   （エンジンのシミュレータ向け静的ライブラリ）を用意する
   （`app/iosApp/scripts/fetch-sentry.sh` / `fetch-firebase.sh`、`app/iosApp/engine/build_ios.sh`）
3. `xcodegen generate` → `iosApp` スキームをDebug構成でシミュレータ向けにビルドし、対象
   シミュレータへインストールする
4. クリップボード経由の取込フロー（`03_kif_import_via_clipboard.yaml`）はOSクリップボードに
   有効なKIFが入っていることが前提。KIF投入込みの実行入口 `.maestro/run-ios.sh` を使えば
   手作業は不要（MaestroのsetClipboardは内部クリップボード専用でUIPasteboardには効かないため、
   投入はフロー外のスクリプトで行う）

### Android

1. AVD（電話フォームファクタ、API 29+）を起動する
2. `02_kif_import_via_file_picker.yaml` はKIFが `/sdcard/Download/` にあり
   メディアスキャナ認識済みであることが前提。配置込みの実行入口 `.maestro/run-android.sh` を
   使えば手作業は不要。Androidにはシェルからクリップボードへ書き込む標準手段が無いため、
   KIF投入はファイルピッカー経由のみ

## 実行コマンド

```
# 一括実行（事前準備込み。iOSはフローごとのクリップボード設定、AndroidはKIF配置＋メディアスキャン）
.maestro/run-ios.sh
.maestro/run-ios.sh <UDID> app/kifu/src/jvmTest/resources/wars_game3.kif
.maestro/run-android.sh

# 単体のフロー
maestro test --udid <シミュレータ/エミュレータのUDID> .maestro/ios/03_kif_import_via_clipboard.yaml

# ディレクトリ一括（同一OSのフローのみ。iOS/Androidを同時に起動していると
# 「複数デバイス接続」エラーになるため --udid か --platform で明示する。
# iOSはフロー02と03でクリップボードの前提が相反するため一括実行不可、run-ios.shを使う）
maestro test --platform android .maestro/android/
```

## ディレクトリ構成

各フローの目的と前提はファイル冒頭のコメントに書く。

```
.maestro/
├── ios/          # iOSシミュレータ向けフロー（subflows/ はiOS専用の共通ステップ）
├── android/      # Androidエミュレータ向けフロー
└── common/       # 両OS共通の後半ステップ。runFlowで各OSのフローから呼ぶ
```

## 既知の制約

- **進捗表示への `assertVisible` は避ける**: 「解析中... N / M 局面」はNが高速に更新され
  続けるため、Composeの再コンポジションとMaestroのスナップショット取得タイミングが競合して
  flaky になることがある。フローは進捗表示を見ず、遷移先の安定した終端状態
  （`.maestro/common/wait_for_analysis_report.yaml`）を待つ
- **Androidのクリップボード注入は現状手段が無い**: `adb shell service call clipboard` は
  Androidバージョン依存のバイナリプロトコルで不安定なため使わない。取込フローの検証は
  ファイルピッカー経由に統一している
