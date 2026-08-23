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

バージョンとリリースノートを触る6ファイル。**どれか1つでも漏らすと不整合になる**。

| ファイル | 変更する箇所 |
| --- | --- |
| `app/gradle.properties` | `shogisupplement.versionCode` を +1（Android/iOS共通のビルド番号） |
| `app/iosApp/project.yml` | `CURRENT_PROJECT_VERSION` を上と**同じ値**に |
| `app/androidApp/build.gradle.kts` | `versionName = "x.y"` |
| `app/iosApp/iosApp/Info.plist` | `CFBundleShortVersionString` を上と同じバージョン名に |
| `app/iosApp/fastlane/metadata/ja/release_notes.txt` | App Store用のリリースノート（`・`始まりの1項目1行） |
| `docs/release-notes.html` | 新しい `<h2>` 節を先頭に追加。`最終更新日` も更新 |

- ビルド番号の値源は `gradle.properties`。Androidの`versionCode`と、fastlaneが
  `xcargs`で渡すiOSの`CURRENT_PROJECT_VERSION`は、どちらもここを読む。
  `project.yml`の値が効くのはXcode/xcodebuildを直接叩くビルド（実機確認など）だけだが、
  提出物と実機確認でビルド番号が食い違わないよう手で同じ値に揃える
  （強制アップデート判定は `app_policy.min_build` とビルド番号を比較している）。
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

iOSは `fastlane ios beta` が `.maestro/run-ios.sh` を本番ビルド前に実行するので、
**シミュレータを起動し最新のDebugビルドを入れておく**（未起動だとlaneがそこで落ちる）。

## iOS（メンテナがローカル実行）

```bash
cd app/iosApp && LC_ALL=en_US.UTF-8 ASC_KEY_PATH=/path/to/AuthKey_XXXX.p8 bundle exec fastlane ios beta
```

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
