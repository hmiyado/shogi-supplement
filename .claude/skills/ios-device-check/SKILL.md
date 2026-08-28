---
name: ios-device-check
description: remoteとの差分をiOS実機で動作確認する。Debugビルドを実機へ入れ、差分に含まれる機能の確認観点をチェックリストにする。「実機で動作確認して」「実機に入れて」「確認観点を出して」等で起動。/ios-device-check でも起動
---

# ios-device-check（iOS実機での動作確認）

remoteに無い差分（未pushコミット＋未コミットの変更）を実機で確かめるための手順。

**ビルドを先にバックグラウンドで走らせ、待ち時間で確認観点を作る**。ビルドは
Gradleのsharedフレームワークビルドを含むため、差分だけでも数分・クリーンなら10分以上
かかる。着手時に見積もりをmiyadoさんへ伝える。

確認そのものを行うのはmiyadoさん。こちらの成果物は「動く実機」と「チェックリスト」の2つ。

## 1. ビルドして実機へ入れる

### 1-1. 前提資材

Debug構成はエンジンを同梱するため、実機向け（`iphoneos`）の静的ライブラリと
vendorのxcframeworkが要る。どちらも`.gitignore`対象で都度取得する。

```bash
ls app/iosApp/engine/build/device/libshogiengine.a app/iosApp/vendor/Sentry-Dynamic.xcframework && ls -d app/iosApp/vendor/Firebase/*.xcframework | wc -l && grep -c "framework: vendor/Firebase/" app/iosApp/project.yml
```

リンク対象は`libyaneuraou.a`ではなく、それにwrapperを合体した`libshogiengine.a`。
Firebaseは1つ欠けてもリンクで落ちるので、最後の2つの数が一致することまで見る。
足りなければ用意する（エンジンのビルドは十数分かかる）。

```bash
cd app/iosApp && ./engine/build_ios.sh device && ./scripts/fetch-sentry.sh && ./scripts/fetch-firebase.sh
```

### 1-2. 対象の実機を選ぶ

```bash
xcrun devicectl list devices
```

`available (paired)` のiPhoneのIdentifier（UDID）を使う。候補が複数あるときは
miyadoさんに聞く。**Simulator用のツール（`simctl`）は実機を扱えない**ので、
インストールも起動も`devicectl`で行う。

### 1-3. ビルド

```bash
xcodegen generate --spec app/iosApp/project.yml --project app/iosApp
```

```bash
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination "id=<UDID>" -derivedDataPath app/iosApp/build/DerivedData_device build
```

- scheme `iosApp` / configuration `Debug`＝**エンジン同梱**。バンドルIDは
  `dev.miyado.shogisupplement.ios.debug`でストア版と同居する。
  サーバー解析側（審査に出すフレーバー）を確かめたいときだけ
  scheme `iosApp-Engineless` / configuration `Debug-Engineless`を使う。
- 署名で止まったら、`app/iosApp/project.yml`の`DEVELOPMENT_TEAM`の値を
  `xcodebuild`の引数末尾に`DEVELOPMENT_TEAM=<値>`として明示する。
- 長いのでバックグラウンド実行にして、`** BUILD SUCCEEDED **`が出るまで待つ間に手順2へ進む。

### 1-4. インストールと起動

```bash
xcrun devicectl device install app --device <UDID> app/iosApp/build/DerivedData_device/Build/Products/Debug-iphoneos/iosApp.app
```

`.app`の置き場は`Build/Products/<configuration>-iphoneos/`。Engineless構成でビルドしたなら
`Debug-Engineless-iphoneos`を指す（Debug構成のパスのまま叩くと、前に入れた
エンジン同梱版を入れ直して確認したい経路を外す）。

```bash
xcrun devicectl device process launch --device <UDID> dev.miyado.shogisupplement.ios.debug
```

入った版が最新かを必ず突き合わせる。ビルド番号が`app/gradle.properties`の
`shogisupplement.versionCode`と違えば古い版を見ている。

```bash
xcrun devicectl device info apps --device <UDID> | grep shogisupplement
```

- スキーマ変更（`.sqm`）を含む差分では**アプリを消さずに上書きインストール**する。
  消して入れ直すとマイグレーションが走らず、確認したい経路を素通りする。
- 端末がロックされているとインストール・起動が失敗する。解除を頼む。

## 2. 差分を把握する

```bash
git fetch origin && git log origin/main..HEAD && git diff --stat $(git merge-base origin/main HEAD) && git status --short
```

- **確認対象は未コミットの変更まで含む**（作業中の実装を実機で見る用途が主）。
  `merge-base`からの差分なら未コミット分まで入る。`git status --short`の`??`（未追跡）は
  この差分に出てこないので個別に開く。
- ファイル名だけでは観点にならない。`git diff $(git merge-base origin/main HEAD)`で中身を読む。
- コミットログの本文は**なぜそう変えたか**を書いてあるので、確認観点はここから引く
  （`--oneline`だと本文が落ちるので使わない）。

## 3. 確認観点を組み立てる

### 何を載せて何を落とすか

実機に上げる価値があるのは**OS・Compose・エンジン・通信をまたいだ結線**と、
**実機でしか見えない見た目**（セーフエリア、実寸の盤・駒、ダークモード、
フォントスケール、スクロールとキーボード）だけ。

VRT（Roborazzi golden）と単体テストで見える範囲は載せない。「見なくてよいもの」の節に
落として、なぜ見なくてよいか（VRT緑・スキーマ差分なし等）を1行で書く。

### 差分の層から観点へ

| 差分の場所 | 見る観点 |
| --- | --- |
| `app/ui/` | 見た目と操作。実寸・反転・ダイアログ・戻る動線 |
| `app/application/` `app/data/` | 挙動が**変わらない**ことの回帰。以前と同じに動けば合格 |
| `.sq` / `.sqm` | 上書きインストールで移行が走り、既存データが残る |
| 文言 | `docs/wording.md`どおりの表記か |
| 通信・同期 | ログインの要否、機内モードでの失敗表示、バックグラウンド復帰 |

失敗系（オフライン・エラー表示）とバックグラウンド復帰は毎回1項目は入れる。
棋譜削除やサーバーへのアップロードのように**本番データを触る項目**は、
チェックリストにその旨を明記してから出す。

### 書き方

- 1項目1行の表。`#` / 確認（何をするか）/ 期待（何が観測できれば合格か）の3列。
- 期待は観測可能な事実で書く。「正しく動く」「問題ない」は不可。
- 節は「見た目が変わるもの」「挙動が変わらないことの確認」「見なくてよいもの」の3つ。
  ログイン状態やKIFの用意など前提が要るものは冒頭にまとめる。

```markdown
| # | 確認 | 期待 |
| --- | --- | --- |
| A1 | ホームを開く | 学習の記録カードが今日の1問の上にある。数値はmono |
```

## 4. 渡す

チェックリストは作業ログ置き場（`CLAUDE.md`参照・git管理外）へ`<バージョン>-ios-device-check.md`
として書き、冒頭に対象コミット範囲・構成（Debug/Debug-Engineless）・`.app`のパスを記す。
確認結果を受け取ったら同じファイルに追記する。修正が要る指摘が出たら、
コミット前に`self-code-review`を回す。
