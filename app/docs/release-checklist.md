# リリース手順（チェックリスト）

新しいバージョンを出すときの手順。iOS fastlaneの仕様・セットアップは
[release.md](release.md) を、告知文は `.claude/skills/release-x-post/` を参照する。

## 担当の分け方

| 担当 | 範囲 |
| --- | --- |
| Claude（sonnetへの委譲可） | リリース準備コミット・Maestro実行・TestFlightアップロード（`fastlane ios beta`） |
| メンテナ | リリース対象の決定・push許可・審査情報の入力・Play Consoleの操作・ストア公開・Xポスト投稿 |

- **何をこのリリースに含めるかはメンテナが決める**。勝手に対象を足さない。
- pushはメンテナの明示許可を得てから行う。
- Play Consoleへのアップロードとストアの公開ボタンは取り消せないのでメンテナが行う。
  TestFlightアップロードとApp Storeの審査提出はClaudeが実行してよい（2026-08-23許可）。
- 署名の秘密（keystoreパスワード・ASCの`.p8`）は取得も表示もしない。

## 全体の流れ

1. メンテナがリリース対象を決める。バージョン番号はマイルストーンに従う（下記）
2. リリース準備コミットを作る（下記「準備コミット」）
3. Maestroをローカル実行する（Android／iOS）
4. iOS: `fastlane ios beta` → `fastlane ios submit`（下記「審査提出」）
5. Android: `bundleRelease` → Play Consoleへアップロード
6. 公開後: リリースノートの日付を実際の公開日に合わせる／Xポストを作る

### バージョン番号

**番号を自分で決めない**。マイルストーンで決まっているので、リリース対象と一緒に
メンテナから受け取る。確認先と手順はリポジトリ外の運用メモにある。

## 準備コミット

バージョンとリリースノートで触る4ファイル。

| ファイル | 変更する箇所 |
| --- | --- |
| `app/gradle.properties` | `shogisupplement.versionCode` を +1、`shogisupplement.versionName` をマイルストーンのバージョンに |
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
- 裏側だけの変更は載せないことがある（判断はメンテナ）。
- 過去バージョンの文言は遡及変更しない。
- Play Consoleの「新機能」欄はメンテナが手入力する。文面は`release_notes.txt`と揃える。

### コミット

- コミット前に必ず self-code-review（`.claude/skills/self-code-review/SKILL.md`）を実施する。
- メッセージの型:

```
1.6のリリース準備をする

<このリリースに含む変更>を含む1.6をリリースする。
バージョンを1.5→1.6・ビルド番号6→7へ上げ、リリースノートを反映する。
公開予定日は2026-08-24。
```

## Maestro（リリース前ゲート）

```bash
.maestro/run-android.sh
```

スクリプトが `adb push` でKIFを置くため、エミュレータ（または実機）が起動していて
Debugビルドが入っていることが前提。`./gradlew :androidApp:installDebug` で入る。

iOSは `fastlane ios beta` が `.maestro/run-ios.sh` を本番ビルド前に実行するので、
**シミュレータを起動し最新のDebugビルドを入れておく**（未起動だとlaneがそこで落ちる）。
入っているビルドが古くてもlaneは進んでしまうので、投入コマンドと最新かどうかの
確認方法は [e2e-testing.md](e2e-testing.md) の手順に従う。

Maestro CLIの導入やビルド前提の詳細も [e2e-testing.md](e2e-testing.md)。

## iOS

TestFlightへのアップロードまではClaudeが実行してよい。

```bash
cd app/iosApp && LC_ALL=en_US.UTF-8 bundle exec fastlane ios beta
```

APIキーは `ASC_KEY_CONTENT`（`.p8` の中身）か `ASC_KEY_PATH`（`.p8` のパス）で渡す。
渡し方はリポジトリ外の運用メモにある。

**`SENTRY_AUTH_TOKEN` も一緒に渡す**。無くてもlaneは成功で終わるが、dSYMの送信だけが
黙って飛ばされ、そのバージョンのクラッシュはアプリのフレームがredactedのままになる。
送り忘れたビルドには、あとから `sentry-cli debug-files upload` で送れる。

`SENTRY_AUTH_TOKEN`だけでは`An organization ID or slug is required`で失敗する。
`SENTRY_ORG`・`SENTRY_PROJECT`も一緒に渡す（値は`app/local.properties`の
`SENTRY_DSN`から機械的に読み取れる。DSN自体はアプリバイナリに同梱される公開情報）。

### 審査提出

アップロード済みのビルドを審査に出すときは `fastlane ios submit` を使う。

```bash
cd app/iosApp && LC_ALL=en_US.UTF-8 bundle exec fastlane ios submit
```

`release` レーンは内部で `beta` を再実行するので、アップロード済みのビルド番号のまま
叩くと同じビルドを送り直そうとして失敗する。beta済みなら `submit` を使う。

2点、そのままだと詰まる:

- **新しいバージョンは審査情報（App Review Information）の連絡先が空で作られる**。
  空のままでは `appStoreVersions ... is not in valid state` で提出が弾かれる。
  姓名・電話・メールは個人情報なのでリポジトリには置かず、App Store Connectの画面で
  入力してから実行する。
- **提出に成功しても`submit`が異常終了することがある**（提出後のビルド再紐付けで
  `The specified pre-release build could not be added`）。終了コードではなく
  バージョンの状態で判断する。`WAITING_FOR_REVIEW` になっていれば提出できている。

審査通過後の公開もApp Store Connectでの手動操作（`automatic_release: false`）。
詳細・前提資材の取得は [release.md](release.md)。

## Android（メンテナがローカル実行）

1. `app/keystore.properties`（git管理外）を用意する。キーは `storeFile` / `storePassword` /
   `keyAlias` / `keyPassword`。取得元はリポジトリ外の運用メモにある。値は決してコミットしない
2. `./gradlew :androidApp:bundleRelease` でAABを作る
3. Play Consoleへアップロードし、「新機能」欄を埋めて審査に出す
4. ビルド後に `app/keystore.properties` を消す

## 公開後

- 実際の公開日がリリースノートの日付と違ったら訂正コミットを作る。
- 告知のXポストは `/release-x-post` で作る（投稿はメンテナ）。
