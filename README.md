# 将棋サプリ（shogi-supplement）

自分の実戦棋譜を端末内で解析し、**自分の棋力帯相応の悪手だけ**を「自分専用のドリル」として
周回する将棋学習アプリ（Android / iOS、Kotlin Multiplatform）。lishogiの大量対局・局面の
解析データに基づく棋力帯×悪手カテゴリ係数表で、「その棋力帯の人が実際にどれくらい犯すミスか」を
根拠に出題を選ぶ。

- 解析はすべて端末内（やねうら王 + Háo評価関数を同梱、`go nodes 400000`）
- レポートは棋譜ビューア形式（本譜・最善の変化の手送り、任意局面からの検討）
- 棋力はレーティングの自己申告ではなく**棋譜からの推定値**で判定

詳しい仕組みは `docs/app-architecture.md` を参照。

## 構成

- `app/shared/` — KMP共通ロジック（KIFパーサ・盤面・悪手抽出/分類・相応判定・強さ推定・
  連盟式棋譜表記・ドリル判定・DB・エンジンブリッジ）
- `app/ui/` — Compose Multiplatform のUI（画面）＋KMP ViewModel。Android/iOS共通
- `app/androidApp/` — Android専用の配線: エンジン別プロセス実行・Foreground Service・
  Supabase連携・各画面のViewModel配線（Host）
- `app/iosApp/` — Xcodeプロジェクト（xcodegen管理）。`:shared`/`:ui` のKotlin/Nativeフレーム
  ワークを読み込み、エンジン本体はプロセス内静的リンクで組み込む
- `docs/` — 設計資料・利用規約

## ビルド

必要要件: JDK 21（`JAVA_HOME`をtemurin-21等に設定）／ Android SDK（compileSdk 36）。

### Android

```sh
cd app
JAVA_HOME=/path/to/jdk-21 ./gradlew :androidApp:assembleDebug
```

- Supabase接続（任意機能）: `app/local.properties` に `SUPABASE_URL` / `SUPABASE_KEY` を設定
  （`local.properties.sample` 参照。未設定でも解析・ドリルは動作する）
- リリース署名: `app/keystore.properties`（リポジトリには含まれない）

### iOS

```sh
cd app/iosApp
./engine/build_ios.sh sim      # やねうら王を静的ライブラリ(libyaneuraou.a)としてビルド
xcodegen generate              # project.yml から iosApp.xcodeproj を生成
open iosApp.xcodeproj          # Xcodeでビルド・実行（:shared/:ui のフレームワークは
                                # preBuildScripts が JAVA_HOME=temurin-21 で ./gradlew を呼んで自動リンクする）
```

## テスト

```sh
cd app
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest   # unit + VRT照合
./gradlew :androidApp:recordRoborazziDebug                # VRT golden更新
./gradlew :androidApp:connectedDebugAndroidTest           # 実機E2E
./gradlew :shared:iosSimulatorArm64Test                   # :shared のiOS実行テスト
```

VRT（スクリーンショットテスト）の手順は `app/docs/vrt.md`。

## 同梱物とライセンス

本アプリは **GPLv3** で配布される（`LICENSE`）。同梱物:

- [やねうら王](https://github.com/yaneurao/YaneuraOu)（GPLv3）— USI思考エンジン
- Háo評価関数（GPLv3、FV_SCALE=20）
- フォント: Shippori Mincho / IBM Plex Sans JP / IBM Plex Mono（いずれもOFL。
  ライセンス全文はアプリ内の設定→オープンソースライセンスおよび
  `app/androidApp/src/main/assets/font_licenses/`）
