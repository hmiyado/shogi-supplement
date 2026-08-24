# 将棋サプリ アーキテクチャ設計

前提: KMP・端末内解析・GPLv3。

## 1. モジュール構成（Kotlin Multiplatform）

Gradleモジュールは9つ。矢印は依存の向きで、循環はない。

```mermaid
flowchart TD
    kifu[":kifu"]
    analysis[":analysis"]
    contracts[":contracts"]
    subprocess[":engine:subprocess"]
    shared[":shared"]
    ui[":ui"]
    android[":androidApp"]
    ios["iosApp（Xcode）"]
    web[":webApp"]
    worker[":server:worker"]

    analysis --> kifu
    contracts --> analysis
    subprocess --> analysis
    shared --> analysis
    shared --> contracts
    ui --> analysis
    ui --> kifu
    ui --> shared
    android --> ui
    android --> shared
    android --> subprocess
    ios --> ui
    web --> ui
    web --> analysis
    web --> kifu
    worker --> contracts
    worker --> analysis
    worker --> subprocess
```

```
app/
├── kifu/                # 盤面・合法手・SFEN（board/）とKIFパース（kifu/）。依存の最下層
│   └── ターゲット: android/jvm/iosArm64/iosSimulatorArm64/js/wasmJs
├── analysis/            # 解析domainとアプリケーション共通ロジック（:kifu に依存）
│   ├── engine/          #  Engine・StudyEngine interface（USIブリッジの抽象化）・
│   │                    #  AnalysisRunner（局面並列の解析実行）・解析条件の不変条件
│   ├── blunder/ classify/ judge/ strength/ pipeline/ pv/  # 悪手定義・分類・相応判定・
│   │                    #  強さ推定・2パスのReportPipeline・読み筋延長
│   ├── notation/ rating/ #  連盟式棋譜表記・段級位
│   ├── drill/           #  次の一手の正誤判定（DrillJudge）・周回（DrillRotation）
│   ├── db/              #  GameRepository / DrillRepository / SettingsRepository interfaceと
│   │                    #  レコード型（実装は :shared）
│   ├── auth/ upload/ download/ transfer/ policy/  # 認証・アップロード・ダウンロード・
│   │                    #  引き継ぎ・強制アップデート判定のinterfaceとオーケストレーション
│   ├── text/            #  AppStrings＝ユーザー向け文言の一元管理（文言修正はここだけ）
│   ├── crash/           #  CrashReporter interface（NoopCrashReporter既定）
│   └── util/            #  Logger・Time（expect/actual）・SHA-256
├── contracts/           # Workerとクライアントで共有する通信DTO（api/analysis・api/transfer）
│                        #  とワイヤ形式↔domainの相互変換
├── engine/subprocess/   # 別プロセスexecでUSIを話すエンジン実装（UsiEngineSubprocess）。
│                        #  ターゲットはjvm（Worker）とandroid（アプリ）
├── shared/              # 具体実装とプラットフォーム配線（:analysis / :kifu を api で再公開）
│   ├── db/              #  SQLDelight実装＋sqldelight/ にスキーマ（.sq）とmigration（N.sqm）
│   ├── supabase/ auth/ upload/ download/ policy/ transfer/  # Supabase実装
│   ├── crypto/          #  引き継ぎコードの鍵・暗号
│   ├── engine/          #  AnalysisOrchestrator・リモート解析・
│   │                    #  UsiEngineInProcess/IosEngineHost（iosEngineMain）
│   ├── kifu/            #  GameImporter（取込オーケストレーション）
│   ├── consent/ crash/ util/
│   └── ターゲット: android/jvm/iosArm64/iosSimulatorArm64
├── ui/                  # Compose Multiplatform UIとKMP ViewModel
│   ├── commonMain       #  画面（home/report/drill/gamelist/settings/account ほか）と
│   │                    #  ViewModel。theme/ がDESIGN.mdトークンの実装
│   ├── iosMain          #  IosMainController・MainViewController（iOSのcomposition root）と
│   │                    #  SharedUi framework（:shared / :analysis / :kifu をexport）
│   └── ターゲット: android/iosArm64/iosSimulatorArm64/wasmJs
├── androidApp/          # Androidアプリ本体（composition root）
│   ├── engine/ service/ #  UsiEngineProcess・解析のForeground Service
│   ├── db/ crash/       #  ドライバ生成・SentryCrashReporter
│   └── *Host.kt         #  :ui の画面へViewModelを配線するホスト
├── webApp/              # 「棋譜を検討する」ページのwasmJsアプリ（:ui / :analysis / :kifu）
├── server/worker/       # Ktorの解析ワーカー（:contracts / :analysis / :engine:subprocess に依存。
│                        #  クライアント用のDB・Supabase・暗号には依存しない）
└── iosApp/              # Xcodeプロジェクト（xcodegen・project.yml）。SharedUi frameworkを
                         # 読み込む。エンジン本体は engine/build_ios.sh が静的ライブラリを
                         # ビルドし、cinterop経由で :shared/iosMain にリンクされる
```

## 2. composition rootと依存規則

具体実装の組み立ては次の4か所だけで行う。

| プラットフォーム | composition root |
| --- | --- |
| Android | `androidApp/ShogiApp.kt`・`MainActivity.kt`・`ui/MainViewModel.kt`・各`*Host.kt` |
| iOS | `ui/iosMain/MainViewController.kt`・`IosMainController.kt`（Supabase系は`SupabaseServices`） |
| Web | `webApp/wasmJsMain/main.kt`・`KentoViewModel.kt` |
| Worker | `server/worker/Application.kt`・`Routes.kt` |

守る規則:

1. `:ui` のViewModelはSupabase・SQLDelight・Android・UIKitの具体型を参照しない。
   受け取るのは `:analysis` が定義するinterfaceと関数だけ
2. Gradleのproject依存に循環を作らない
3. `api(project(...))` による再公開は増やさない。使うモジュールへ直接依存する
4. ユーザー向け文言は `analysis/text/AppStrings.kt` 以外に直書きしない

## 3. 目標とする境界（移行中）

現在の `:analysis` は解析domainとアプリケーション層が同居し、`:shared` はDB・Supabase・
暗号・通信DTO・エンジン実装を1つに抱えている。`:server:worker` が必要とするのは通信DTOと
解析だけなのに `:shared` 全体へ依存しており、クライアント側の変更がWorkerへ波及し得る。

段階的に次の形へ寄せる。

```mermaid
flowchart TD
    contracts[":contracts 通信DTO"]
    kifu2[":kifu 盤面・KIF"]
    analysis2[":analysis 解析domain"]
    application[":application use case・port"]
    ui2[":ui 画面・ViewModel"]
    data[":data:database / :data:supabase"]
    engine[":engine:android / :engine:ios / :engine:jvm / :engine:remote"]

    analysis2 --> kifu2
    application --> analysis2
    ui2 --> application
    data --> application
    engine --> analysis2
    engine --> contracts
    worker2[":server:worker"] --> contracts
    worker2 --> analysis2
```

- `:application` から `:data:*` や `:engine:*` の具体実装へは依存しない
- `:contracts` はワイヤ形式↔domainの相互変換を持つため `:analysis` に依存する。
  変換を持たせない場合はサーバーとクライアントが同じ変換を二重に持つことになるため
- 別プロセスexecのエンジン実装はjvm（Worker）とandroid（アプリ）で同一のため、
  `:engine:jvm` と `:engine:android` に分けず `:engine:subprocess` 1つにしている
- Workerはクライアント用インフラ（DB・Supabase・暗号）へ依存しない
- この移行で、SQLDelightスキーマ・Supabaseスキーマ・API wire format・
  エンジン解析条件は変えない

## 4. エンジン統合

`Engine` interface（`analysis/commonMain/engine/Engine.kt`）がUSIブリッジを抽象化し、
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
  （`analysis/iosMain/util/Logger.ios.kt` 参照）
- in-processは1インスタンス＝局面並列不可のため、iOSの解析は直列（`workers=1` が既定運用）

### 共通の不変条件

- ノード数（既定400,000固定）・Threads=1・MultiPV=2・FV_SCALE=20（Háo）は両実装で共通。
  これらと評価関数のSHA-256・悪手定義バージョン・係数表バージョンを**解析結果レコードに記録**し、
  係数表と解析条件の不一致を検出したら再解析を促す
- `AnalysisOrchestrator`（`shared/commonMain/engine`）が「KIFパース→エンジン解析→悪手判定→
  強さ推定→DB保存」を共通化し、Android（`AnalysisService`経由）・iOS
  （クリップボード/ファイル取込フロー）の両方から呼ばれる。プラットフォーム差は
  `engineFactory`/`disposeEngine`の注入だけに閉じ込めている
- エンジン解析には探索の揺れがある（ワーカー割当→置換表状態）。分類境界局面のビット一致
  assertは書かない

## 5. `:ui` の KMP ViewModel

`androidx.lifecycle.ViewModel`/`ViewModelProvider` はKMP対応版を使用しており、
`HomeViewModel`/`DrillViewModel`/`ReportViewModel`/`AccountViewModel` は `:ui` の
commonMainに置かれ、Android/iOS両方から同一実装で使われる。ViewModelはAndroid固有の型
（`Application`・`File`・`UsiEngineProcess`）を直接知らず、`:analysis` が定義する
Repository interfaceと必要最小限の関数（エンジン呼び出しが要る場合は `judgeWithEngine` のような関数注入）だけを
受け取る。Android/iOS専用の解決（`ApplicationInfo`・`nativeLibraryDir`・ファイルI/O等）は
それぞれ `androidApp/*Host.kt` と `ui/iosMain/IosMainController.kt` 側に閉じ込める。

DB/エンジン処理向けの既定コルーチンディスパッチャは `expect val defaultIoDispatcher` で
プラットフォームごとに分離する（Android= `Dispatchers.IO`、iOS= `Dispatchers.Default`。
kotlinx.coroutines の Native向けAPIでは `Dispatchers.IO` が公開されていないため）。

## 6. DB

SQLDelightで `shared/commonMain/sqldelight/.../ShogiSupplement.sq` にスキーマを定義し、
Android/iOS双方でネイティブドライバを使う。テーブル: `game` / `blunder_report` /
`user_settings` / `drill_attempt` / `service_rank` / `service_account` / `position_eval`。
スキーマ変更は `db/N.sqm` のマイグレーションファイルを追加して行う
（DB保存文字列を変更する場合は必ずmigrationを伴わせること）。

## 7. 相応判定ロジック

- **帯の決定は申告レートではなく棋譜からの強さ推定値**: `ReportPipeline` は
  ①悪手抽出（レート非依存）→ ②（過去累計＋当該局）の悪手率から `StrengthEstimator` で
  レート相当値を推定 → `bandOf` → ③各悪手の相応判定、の2パス。推定値は `game.rating` に
  来歴として保存する。申告のサービス/ルール/段級位/アカウント名は研究較正用の記録＋
  先後自動選択用の記録で、判定には使わない
- スイング系: 自帯率<2/1000→△見送り ／ 最上位帯比≥4倍→◎優先 ／ 他→○出題対象
- 詰み見逃し: 帯別見逃し率 <5%→△ ／ 5〜60%→◎ ／ >60%→○（背伸び）
- noteは「あなたの帯: 約N局に1回 ／ R2200+: 約M局に1回」の2点形式（AppStringsのテンプレート）
- 表示は連盟式棋譜表記（`notation/JapaneseNotation`、▲３四金直 等）。USIは内部表現のみ

## 8. テスト戦略

- **ゴールデンフィクスチャ**: 既知の期待値をテストリソース化
  - KIFパーサ: 実KIF（`app/data/kifu_samples/`）→期待USI出力と一致
  - 係数表ローダー: coefficients JSONの読み込みと帯引き
- UI: VRT（Roborazzi golden、手順は `app/docs/vrt.md`）。開発ループはJVM完結、
  実機E2E（`connectedAndroidTest`／iOS UIテスト／Maestro、手順は `app/docs/e2e-testing.md`）
  はフェーズ最終1回＋依存/manifest変更時のスモークに限定する
- `:shared` と `:ui` はiOSターゲット（`iosArm64`/`iosSimulatorArm64`）でコンパイル・テスト実行を
  継続的に確認する（`./gradlew :shared:compileKotlinIosArm64` 等）。JVM専用APIは
  `expect`/`actual` で分離する（`util/Time.kt` 等）

## 9. 開発時の注意

- パッケージ名: `dev.miyado.shogisupplement`。係数表= `app/androidApp/src/main/assets/coefficients_hao_isolate_v1.json`
  （テストリソースにも同梱）
- ユーザー向け文言は `analysis/text/AppStrings.kt` 以外に直書きしない
- 依存追加時: AboutLibraries `exportLibraryDefinitions` を再実行し、licenses系VRT goldenも更新する。
  manifest・依存変更を含む変更は実機起動スモークを必須とする
