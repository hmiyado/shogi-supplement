# 検討専用エンジンのworker検証

2026-09-27。Macでの[同一実時間比較](engine-study-match-results.md)を棋力の予備評価として使い、Linuxで再対局は行わない。

## 実装した分離

`purpose: "study"`の単発局面要求だけ、サーバーの専用エンジン設定を選択する。1局解析へのpurpose指定は拒否。`purpose: "drill"`と目的を省略する旧クライアントは従来のエンジン・倍率20を使う。既存クライアントが送るJSONは変更していない。

専用設定は次の5環境変数。すべて未設定なら従来エンジンで検討し、一部だけの設定は起動時に拒否する。

| 変数 | 意味 |
| --- | --- |
| `STUDY_ENGINE_PATH` | 専用USI実行ファイルのパス |
| `STUDY_ENGINE_EVAL_DIR` | nn.binを置いたディレクトリ |
| `STUDY_ENGINE_REV` | ソース版・コミットの識別子 |
| `STUDY_ENGINE_EVAL_SHA256` | 評価関数のSHA-256。起動時に実ファイルと照合 |
| `STUDY_ENGINE_FV_SCALE` | 評価値倍率。今回のAobaは40 |

検討profileの設定からエンジン起動と結果の`engine_meta`を作り、版・評価関数・倍率・探索条件・入力purpose・候補数をキャッシュ識別へ反映する。異なる条件のキャッシュを返さず、設定を戻すと対応する旧条件のキャッシュを利用できる。保存ペイロードにもpurposeを残す。探索量は引き続き400k、Threads 1、Hash 128MB。クライアントから任意の倍率やノード数を指定する経路は増やしていない。

単発局面用の日次上限を引き続き使う。課金権限、ノード予算台帳、取消・lease/fencingの新実装は含めない。既存処理はHTTP切断後も結果保存まで続ける設計で、今回それを変更していない。

## ローカルで確認済み

- worker・contracts・remoteの211テスト成功（123＋8＋80）。新規検証は通常解析・旧要求・ドリルとの分離、候補数・版・評価関数・倍率ごとのキャッシュ分離、切り戻し、設定不備、評価関数SHA不一致、実際に送るUSI倍率、JSON互換。
- Macの実AobaバイナリをKtorのテストHTTP経路から呼び、3候補・倍率40・期待する版と評価関数の来歴を確認。2回目の同一要求はエンジンを起動せず保存結果を返した。
- 上記HTTP検証の認証・リポジトリはメモリ内のテスト実装。本番Supabaseへの書込みやCloud Runへデプロイした試験ではない。[結果JSON](verification/engine-study-worker.json)を参照。

専用の実エンジンHTTPプローブは5環境変数を設定し、`app/`で実行する。未設定では失敗するため、通常の単体テストに混ぜて合格扱いにしない。

```sh
./gradlew :server:worker:studyEngineSmoke --configure-on-demand
```

## Linux候補イメージの隔離検証

ユーザー承認後、Cloud BuildのLinux x86_64環境で、本番ベースの固定digestに最新worker配布物とAobaを加えた候補イメージを実行した。通常のworkerエントリーポイントを使い、4 CPU・2GiBの上限、外部へ接続できないDocker internal network、検証専用JWT/JWKS・PostgREST互換fixtureで確認した。検証コードは `tools/worker-image-check/`。

- study: Aoba、倍率40、400k、3候補。PVの合法性と保存metaを確認。
- 同一要求: 保存結果を再利用。初回2.666秒、キャッシュ0.031秒（各1回のスモーク測定）。
- 旧クライアント相当のpurpose省略: Háo、倍率20、3候補を維持。
- drill: Háo、倍率20、2候補を維持。3用途の保存・キャッシュを分離。
- 不正JWT拒否、エンジン停止・再探索・正常終了が成功。
- OOMなし。停止・再開プローブ中のエンジン単体VmHWMは495,296 KiB。worker全体のピーク値ではない。

候補は `analysis-worker-study:verify-20260927-02` に保存。digestは `sha256:622a8479c177cbb208ae79c3acd9cd7d6228d108d67f4e9dde858f3252c827b9`。[結果JSON](verification/engine-study-image.json)に記録した。

初回はCloud Build既定ホストが2 CPUだったため4 CPUコンテナ起動前に失敗。ホストを8 CPUへ変更した再試行は成功。本番ready revision `analysis-worker-00099-28f` と本番タグのdigestが前後で一致することも確認した。本番へのデプロイ・設定変更は行っていない。検証のビルド・保存料金と共通クォータは使用する。

fixtureは実Supabaseではなく、PostgreSQL制約・RLS・並行トランザクションや負荷耐性は未検証。今回の成功はCloud Run上の性能保証ではない。

候補イメージにはAobaを同梱済みだが、専用profileの有効化には5環境変数が必要。本番への同digestの配布、環境変数設定、新クライアントによるstudy送信は未実施。新しいpurposeは古いworkerでは解釈できないため、クライアントで有効化する場合は対応サーバーを先に配布する。既存要求を一律に候補へ切り替えない。
