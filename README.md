# 将棋サプリ（shogi-supplement）

自分の実戦棋譜を端末内で解析し、**自分の棋力帯相応の悪手だけ**を「自分専用の次の一手問題」として
周回する将棋学習アプリ（Android / iOS、Kotlin Multiplatform）。lishogiの大量対局・局面の
解析データに基づく棋力帯×悪手カテゴリ係数表で、「その棋力帯の人が実際にどれくらい犯すミスか」を
根拠に出題を選ぶ。

- 解析はすべて端末内（やねうら王 + Háo評価関数を同梱、`go nodes 400000`）
- レポートは棋譜ビューア形式（本譜・最善の変化の手送り、任意局面からの検討）
- 棋力はレーティングの自己申告ではなく**棋譜からの推定値**で判定

詳しい仕組みは `docs/app-architecture.md` を参照。

## 構成

Gradleモジュール:

- `app/kifu/`（`:kifu`）— 盤面・合法手・SFEN・KIFパース
- `app/analysis/`（`:analysis`）— 解析ドメイン・悪手判定・強さ推定・次の一手
- `app/application/`（`:application`）— portとuse case
- `app/contracts/`（`:contracts`）— 通信DTO・ワイヤ形式とドメインの相互変換
- `app/data/database/`（`:data:database`）— SQLDelightによるDB実装
- `app/data/supabase/`（`:data:supabase`）— Supabase連携・引き継ぎ用暗号
- `app/engine/ios/`（`:engine:ios`）— iOSプロセス内エンジン・WKWebView内WASMブリッジ
- `app/engine/remote/`（`:engine:remote`）— サーバー解析・失敗時のフェイルオーバー
- `app/engine/subprocess/`（`:engine:subprocess`）— Android・JVMの別プロセスエンジン
- `app/ui/`（`:ui`）— Compose Multiplatform UI・KMP ViewModel
- `app/androidApp/`（`:androidApp`）— Androidアプリ本体・具体実装とViewModelの配線
- `app/server/worker/`（`:server:worker`）— Ktor解析ワーカー
- `app/webApp/`（`:webApp`）— 棋譜検討用wasmJsアプリ

その他:

- `app/iosApp/` — Xcodeプロジェクト（xcodegen管理）。`:ui` が生成する `SharedUi`
  フレームワークを読み込み、エンジン本体はプロセス内静的リンクで組み込む
- `docs/` — 設計資料・利用規約

## ビルド

必要要件: JDK 21（`JAVA_HOME`をtemurin-21等に設定）／ Android SDK（compileSdk 36）。

### Android

```sh
cd app
JAVA_HOME=/path/to/jdk-21 ./gradlew :androidApp:assembleDebug
```

- Supabase接続（任意機能）: `app/local.properties` に `SUPABASE_URL` / `SUPABASE_KEY` を設定
  （`local.properties.sample` 参照。未設定でも解析・次の一手問題は動作する）
- リリース署名: `app/keystore.properties`（リポジトリには含まれない）

### iOS

```sh
cd app/iosApp
./engine/build_ios.sh sim      # やねうら王を静的ライブラリ(libyaneuraou.a)としてビルド
xcodegen generate              # project.yml から iosApp.xcodeproj を生成
open iosApp.xcodeproj          # Xcodeでビルド・実行（:ui の SharedUi フレームワークは
                                # preBuildScripts が JAVA_HOME=temurin-21 で ./gradlew を呼んでビルドし、Xcodeがリンクする）
```

## テスト

```sh
cd app
./gradlew jvmTest :androidApp:testDebugUnitTest           # unit + VRT照合
./gradlew :androidApp:recordRoborazziDebug                # VRT golden更新
./gradlew :androidApp:connectedDebugAndroidTest           # 実機E2E
./gradlew :data:supabase:iosSimulatorArm64Test            # iOS実行テスト（暗号・引き継ぎ）
```

VRT（スクリーンショットテスト）の手順は `app/docs/vrt.md`。

## 同梱物とライセンス

本アプリは **GPLv3** で配布される（`LICENSE`）。同梱物:

- [やねうら王](https://github.com/yaneurao/YaneuraOu)（GPLv3）— USI思考エンジン
- Háo評価関数（GPLv3、FV_SCALE=20）
- フォント: Shippori Mincho / IBM Plex Sans JP / IBM Plex Mono（いずれもOFL。
  ライセンス全文はアプリ内の設定→オープンソースライセンスおよび
  `app/androidApp/src/main/assets/font_licenses/`）
