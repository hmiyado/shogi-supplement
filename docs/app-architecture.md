# 将棋サプリ アーキテクチャ設計

前提: KMP・端末内解析・GPLv3。

## 1. モジュール構成（Kotlin Multiplatform）

```
app/
├── shared/              # commonMain: OS非依存のコアロジック（androidTarget/jvm/iosArm64/iosSimulatorArm64）
│   ├── kifu/            #  KIFパーサ → USI手列＋対局者名
│   ├── engine/          #  Engine interface（USIブリッジの抽象化）＋AnalysisOrchestrator/AnalysisRunner
│   ├── board/            #  盤面・合法手生成・SFEN
│   ├── notation/        #  USI→連盟式棋譜表記（▲３四金直 等。動作優先→位置の曖昧性解消）
│   ├── blunder/          #  悪手定義（loss判定・勝率換算）
│   ├── classify/         #  タクソノミ分類（差分駒損・咎めPV）＋表示名
│   ├── judge/            #  相応判定（係数表照合・◎○△・note生成）
│   ├── pipeline/         #  抽出→強さ推定→判定の2パス（ReportPipeline）
│   ├── strength/         #  悪手率→レート相当の推定（StrengthEstimator）
│   ├── rating/           #  段級位の型・エンコード（WarsRank）
│   ├── drill/             #  正誤判定（DrillJudge）・周回ローテーション（DrillRotation）
│   ├── auth/ upload/     #  認証・アップロードinterface（commonMain）＋実装（androidMain/iosMain）
│   ├── crash/             #  CrashReporter interface（NoopCrashReporter既定）
│   ├── text/              #  AppStrings＝ユーザー向け文言の一元管理（文言修正はここだけ）
│   └── db/                #  SQLDelight（game/blunder_report/drill_attempt/user_settings/
│                          #  service_rank/service_account/position_eval。migrationはdb/N.sqm）
├── ui/                   # commonMain: Compose Multiplatform UI＋KMP ViewModel（androidTarget/iosArm64/iosSimulatorArm64）
│   ├── HomeScreen/DrillScreen/ReportScreen/AccountScreen/SettingsScreen/GameListScreen 等
│   ├── HomeViewModel/DrillViewModel/ReportViewModel/AccountViewModel（androidx.lifecycle
│   │   ViewModelのKMP版。DB/エンジンアクセスはinterface注入で、Android専用型は持たない）
│   └── theme/            #  DESIGN.mdトークンの実装（フォント3書体・色・spacing）
├── androidApp/           # Android専用の配線・実装
│   ├── engine/            #  UsiEngineProcess（別プロセスexec）＋AndroidAnalysisRunnerFactory
│   ├── service/            #  解析のForeground Service
│   ├── auth/ upload/       #  Supabase実装（Android専用、shared/androidMainではなくここに置く
│   │                        #  ものはAndroid固有APIに直接依存する部分のみ）
│   ├── crash/              #  SentryCrashReporter（手動init・自動initProviderは無効化）
│   ├── ui/                 #  :ui のScreenをホストするComposable群・MainViewModel・MainUiState
│   └── *Host.kt            #  各画面ViewModelへの配線（AccountHost/HomeHost/ReportHost/SettingsHost等）
└── iosApp/               # Xcodeプロジェクト（xcodegen管理・project.yml）。:shared/:ui の
                           # フレームワークを読み込む。エンジン本体（YaneuraOu）は
                           # engine/build_ios.sh が静的ライブラリとしてビルドし、cinterop経由で
                           # :shared/iosMain にリンクされる
```

## 2. エンジン統合

`Engine` interface（`shared/commonMain/engine/Engine.kt`）がUSIブリッジを抽象化し、
`analyze`/`analyzeSfen`/`quit`/`newGame` の4操作のみを公開する。実装はプラットフォームごとに
起動方式が異なる。

### Android: 別プロセスexec

- **Android 10+ はアプリのデータ領域から実行ファイルをexecできない（W^X制約）**ため、
  エンジンバイナリは `jniLibs/arm64-v8a/libyaneuraou_usi.so` という名前でAPKに同梱し、
  `applicationInfo.nativeLibraryDir` から**別プロセスとしてexec**する（`UsiEngineProcess`）
- 評価関数nn.bin（Háo）はAPK同梱（assets圧縮）→初回起動時にfilesDirへ展開→EvalDirで指定
- 局面並列は「1局面Threads=1」を守った上でのマルチプロセス並列（既定4ワーカー・
  USI_Hash=128MB/プロセス）。フォアグラウンドサービス上で実行する（バックグラウンドだと
  スケジューラの都合でCPUのbigコアに載らず著しく遅くなるため）
- exec方式を選ぶ理由: (1) やねうら王はmain()起動・グローバル状態前提でライブラリ化は大改造、
  (2) 物差し条件が「1局面Threads=1」なので並列は局面並列＝マルチプロセスが唯一素直、
  (3) ネイティブクラッシュの隔離、(4) USI経路が揃うためゴールデンテストが可能。
  前例: DroidFishはStockfishを別プロセスexecで運用している

### iOS: プロセス内エンジン（in-process）

- **iOSはプロセスexec不可**のため、YaneuraOuを静的ライブラリ（`libyaneuraou.a`。
  `app/iosApp/engine/build_ios.sh` がビルド）としてリンクし、専用スレッド内でUSIプロトコルを
  話す（`UsiEngineInProcess`、cinterop経由でC wrapperを呼ぶ）
- エンジンスレッドは C wrapper 側の設計で `std::thread(...).detach()` されており、
  Kotlin/Swift側から明示的に終了させる手段がない。`quit()` は "quit" コマンドの送信のみで、
  一度 `quit` するとプロセスの再起動なしにはエンジンを再作成できない。そのため iOS 側は
  **1インスタンスをプロセス生存中ずっと使い回す**（`IosEngineHost` が保持し、局の区切りは
  `Engine.newGame()` で行う）
- `shogi_engine_start()` 呼び出し後はプロセスのfd0/fd1がエンジン用パイプに専有されるため、
  アプリ側のログはOSLog/NSLog等を使い、println/print を使ってはならない
  （`shared/iosMain/util/Logger.ios.kt` 参照）
- in-processは1インスタンス＝局面並列不可のため、iOSの解析は直列（`workers=1` が既定運用）

### 共通の不変条件

- ノード数（既定400,000固定）・Threads=1・MultiPV=2・FV_SCALE=20（Háo）は両実装で共通。
  これらと評価関数のSHA-256・悪手定義バージョン・係数表バージョンを**解析結果レコードに記録**し、
  係数表と解析条件の不一致を検出したら再解析を促す
- `AnalysisOrchestrator`（`shared/commonMain`）が「KIFパース→エンジン解析→悪手判定→
  強さ推定→DB保存」を共通化し、Android（`AnalysisService`経由）・iOS
  （クリップボード/ファイル取込フロー）の両方から呼ばれる。プラットフォーム差は
  `engineFactory`/`disposeEngine`の注入だけに閉じ込めている
- エンジン解析には探索の揺れがある（ワーカー割当→置換表状態）。分類境界局面のビット一致
  assertは書かない

## 3. `:ui` の KMP ViewModel

`androidx.lifecycle.ViewModel`/`ViewModelProvider` はKMP対応版を使用しており、
`HomeViewModel`/`DrillViewModel`/`ReportViewModel`/`AccountViewModel` は `:ui` の
commonMainに置かれ、Android/iOS両方から同一実装で使われる。ViewModelはAndroid固有の型
（`Application`・`File`・`UsiEngineProcess`）を直接知らず、`DatabaseRepository` と
必要最小限の関数（エンジン呼び出しが要る場合は `judgeWithEngine` のような関数注入）だけを
受け取る。Android/iOS専用の解決（`ApplicationInfo`・`nativeLibraryDir`・ファイルI/O等）は
それぞれ `androidApp/*Host.kt` と `ui/iosMain/IosMainController.kt` 側に閉じ込める。

DB/エンジン処理向けの既定コルーチンディスパッチャは `expect val defaultIoDispatcher` で
プラットフォームごとに分離する（Android= `Dispatchers.IO`、iOS= `Dispatchers.Default`。
kotlinx.coroutines の Native向けAPIでは `Dispatchers.IO` が公開されていないため）。

## 4. DB

SQLDelightで `shared/commonMain/sqldelight/.../ShogiSupplement.sq` にスキーマを定義し、
Android/iOS双方でネイティブドライバを使う。テーブル: `game` / `blunder_report` /
`user_settings` / `drill_attempt` / `service_rank` / `service_account` / `position_eval`。
スキーマ変更は `db/N.sqm` のマイグレーションファイルを追加して行う
（DB保存文字列を変更する場合は必ずmigrationを伴わせること）。

## 5. 相応判定ロジック

- **帯の決定は申告レートではなく棋譜からの強さ推定値**: `ReportPipeline` は
  ①悪手抽出（レート非依存）→ ②（過去累計＋当該局）の悪手率から `StrengthEstimator` で
  レート相当値を推定 → `bandOf` → ③各悪手の相応判定、の2パス。推定値は `game.rating` に
  来歴として保存する。申告のサービス/ルール/段級位/アカウント名は研究較正用の記録＋
  先後自動選択用の記録で、判定には使わない
- スイング系: 自帯率<2/1000→△見送り ／ 最上位帯比≥4倍→◎優先 ／ 他→○出題対象
- 詰み見逃し: 帯別見逃し率 <5%→△ ／ 5〜60%→◎ ／ >60%→○（背伸び）
- noteは「あなたの帯: 約N局に1回 ／ R2200+: 約M局に1回」の2点形式（AppStringsのテンプレート）
- 表示は連盟式棋譜表記（`notation/JapaneseNotation`、▲３四金直 等）。USIは内部表現のみ

## 6. テスト戦略

- **ゴールデンフィクスチャ**: 既知の期待値をテストリソース化
  - KIFパーサ: 実KIF（`app/data/kifu_samples/`）→期待USI出力と一致
  - 係数表ローダー: coefficients JSONの読み込みと帯引き
- UI: VRT（Roborazzi golden、手順は `app/docs/vrt.md`）。開発ループはJVM完結、
  実機E2E（`connectedAndroidTest`／iOS UIテスト）はフェーズ最終1回＋依存/manifest変更時の
  スモークに限定する
- `:shared` はiOSターゲット（`iosArm64`/`iosSimulatorArm64`）でコンパイル・テスト実行を
  継続的に確認する（`./gradlew :shared:compileKotlinIosArm64` 等）。JVM専用APIは
  `expect`/`actual` で分離する（`util/Time.kt` 等）

## 7. 開発時の注意

- パッケージ名: `dev.miyado.shogisupplement`。係数表= `app/androidApp/src/main/assets/coefficients_hao_v1.json`
  （テストリソースにも同梱）
- ユーザー向け文言は `shared/text/AppStrings.kt` 以外に直書きしない
- 依存追加時: AboutLibraries `exportLibraryDefinitions` を再実行し、licenses系VRT goldenも更新する。
  manifest・依存変更を含む変更は実機起動スモークを必須とする
