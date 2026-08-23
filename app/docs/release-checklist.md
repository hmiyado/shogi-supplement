# リリース手順（チェックリスト）

新しいバージョンを出すときの手順。iOS fastlaneの仕様・セットアップは
[release.md](release.md) を、告知文は `.claude/skills/release-x-post/` を参照する。

## 担当の分け方

| 担当 | 範囲 |
| --- | --- |
| Claude（sonnetへの委譲可） | リリース準備コミットの作成まで。Maestroのローカル実行 |
| メンテナ | push許可・実機ビルド・TestFlight/Play提出・ストアの公開操作・Xポスト投稿 |

- pushはメンテナの明示許可を得てから行う。
- 署名の秘密（keystoreパスワード・ASCの`.p8`）は取得も表示もしない。

## 全体の流れ

1. リリース対象を確定する（issue・mainに入っている変更）
2. リリース準備コミットを作る（下記「準備コミット」）
3. Maestroをローカル実行する（Android／iOS）
4. iOS: `fastlane ios beta` → `fastlane ios release`
5. Android: `bundleRelease` → Play Consoleへアップロード
6. 公開後: リリースノートの日付を実際の公開日に合わせる／Xポストを作る

## 準備コミット

バージョンとリリースノートで触る4ファイル。

| ファイル | 変更する箇所 |
| --- | --- |
| `app/gradle.properties` | `shogisupplement.versionCode` を +1、`shogisupplement.versionName` を新バージョン名に |
| `app/iosApp/project.yml` | `CURRENT_PROJECT_VERSION` / `MARKETING_VERSION` を上と**同じ値**に |
| `app/iosApp/fastlane/metadata/ja/release_notes.txt` | App Store用のリリースノート（`・`始まりの1項目1行） |
| `docs/release-notes.html` | 新しい `<h2>` 節を先頭に追加。`最終更新日` も更新 |

- バージョン名・ビルド番号の値源は `gradle.properties` だけ。Androidの`versionName`/
  `versionCode`、fastlaneが`xcargs`で渡すiOSの`MARKETING_VERSION`/`CURRENT_PROJECT_VERSION`は
  すべてここを読む（Androidの`build.gradle.kts`とiOSの`Info.plist`は触らない）。
- `project.yml`の2値だけは手で写す（xcodegenの入力のため）。ずれたままXcodeで
  ビルドすると突合スクリプトがエラーで止める。fastlane経由の提出物は`xcargs`で
  上書きされるので`gradle.properties`の値になる。
- `docs/release-notes.html` の日付は公開**予定**日を入れる。実際の公開日とずれたら
  公開後に訂正コミットを作る（例: `4cab1dee`）。
- 強制アップデートを発動させたい場合だけ、別途Supabaseの`app_policy.min_build`を上げる。
  通常のリリースでは触らない。

### リリースノートの書き方

- `docs/release-notes.html` の項目は `<strong>機能名</strong>: 何ができるようになったか`。
- 文言は `docs/wording.md` に従う。トーンは `DESIGN.md`（誇張しない・根拠で語る）。
- 裏側だけの変更は載せないことがある（判断はメンテナ。例: 1.6の#27は非掲載）。
- 過去バージョンの文言は遡及変更しない。
- Play Consoleの「新機能」欄はメンテナが手入力する。文面は`release_notes.txt`と揃える。

### コミット

- コミット前に必ず self-code-review（`.claude/skills/self-code-review/SKILL.md`）を実施する。
- メッセージの型:

```
1.6のリリース準備をする

棋譜の個別削除（#26）・手動棋譜入力を含む1.6をリリースする。
バージョンを1.5→1.6・ビルド番号6→7へ上げ、リリースノートを反映する。
公開予定日は2026-08-24。
```

## Maestro（リリース前ゲート）

```bash
.maestro/run-android.sh
```

iOSは `fastlane ios beta` が `.maestro/run-ios.sh` を本番ビルド前に実行するので、
**シミュレータを起動し最新のDebugビルドを入れておく**（未起動だとlaneがそこで落ちる）。

## iOS（メンテナがローカル実行）

```bash
cd app/iosApp && LC_ALL=en_US.UTF-8 bundle exec fastlane ios beta
```

APIキーは `ASC_KEY_CONTENT`（`.p8` の中身）か `ASC_KEY_PATH`（`.p8` のパス）で渡す。

TestFlightまで通ったら `fastlane ios release` で審査提出。審査通過後の公開は
App Store Connectでの手動操作。詳細・前提資材の取得は [release.md](release.md)。

## Android（メンテナがローカル実行）

1. `app/keystore.properties`（git管理外）を用意する。キーは `storeFile` / `storePassword` /
   `keyAlias` / `keyPassword`。取得元はリポジトリ外の運用メモにある。値は決してコミットしない
2. `./gradlew :androidApp:bundleRelease` でAABを作る
3. Play Consoleへアップロードし、「新機能」欄を埋めて審査に出す
4. ビルド後に `app/keystore.properties` を消す

## 公開後

- 実際の公開日がリリースノートの日付と違ったら訂正コミットを作る。
- 告知のXポストは `/release-x-post` で作る（投稿はメンテナ）。
