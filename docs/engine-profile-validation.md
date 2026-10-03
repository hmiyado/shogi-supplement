# #101 エンジン比較の追試

以下の `/path/to/` は、別途用意する入力・出力先の例です。個別棋譜を含む生データは同梱していません。

確認日: 2026-09-27。移行計画・採用条件の正本は[research Issue #101](https://github.com/hmiyado/shogi-supplement-research/issues/101)。本書は測定結果と再現手順を記録する。

## 判断

9.40＋HáoのmacOS初期化停止は解消している。9.80＋Háoと9.80＋Aobaもネイティブで連続解析・停止・復帰が動作する。ただし、どちらも正式採用の条件を満たしたとは判断しない。

- 9.80＋Háoは今回の固定ノード測定でp95が約5.2%短い。最善手が108局面中18局面で変わるため、評価関数が同じでも品質不変とはみなせない。
- 9.80＋Aobaはp95が約58.1%長い。既存300局の棋力推定検証でも改善を実証できていないため、通常解析への一括置換を支持する結果ではない。
- Aobaを検討専用として扱う場合も、同一実時間の棋力、Linux nativeでのコスト、端末資源、Web接続の検証が必要。今回の結果だけではその価値を否定も証明もしない。

製品のエンジン・評価関数・係数・配信設定は変更していない。#101全体は未完了。

追試: [サーバー検討専用の同一実時間比較](engine-study-match-results.md)では、Aobaが40勝23敗・未判定1局となった。固定ノードでの遅さと通常解析の推定精度だけでは、検討専用での有用性を判断できない。

## 固定した構成

| 項目 | 9.40＋Háo | 9.80＋Háo | 9.80＋Aoba |
| --- | --- | --- | --- |
| ソースSHA | `717da871e7a620702b8b9433bd8f9f181710435a` | `d47c9bfb94210fb1f9b741d5ee16ce081b35cacd` | 同左 |
| FV_SCALE | 20 | 20 | 40 |
| 評価関数サイズ | 64,217,066 bytes | 同左 | 192,624,720 bytes |

評価関数SHA-256:

- Háo: `1141d275bceec911156801f27303dc9ff5beb24f4f59144cc069306c59e80782`
- Aoba: `f8ee839ae8c08537036f23345dd5ed0416958b22425476fc60177942903219b5`

過去の検証用macOS arm64バイナリを再使用。バイナリSHAと測定スクリプトSHAは[機械可読結果](verification/engine-profile-benchmark.json)に保存した。今回再ビルドはしていないため、現在のビルド経路の再現性を証明するものではない。

9.40比較バイナリには共有メモリ非対応時のログ二重ロックを直す変更がある。この問題への製品側修正は`2ee15e6c`にあり、[iOS起動の説明](ios-engine-startup.md)を参照。今回確認したiOS/WASM/workerのビルド設定は9.40固定SHAを参照している。配布済みバイナリや本番workerの版を今回監査したわけではない。

## 今回実行した測定

macOS 27.0 (26A428)、arm64。#102で使用した1,670件のドリル候補から、canonical JSONのSHA-256順で108件を選択した。以前のGolden 108局面とは異なる。悪手候補に偏った標本で、通常局面全体・終局条件の網羅検証ではない。

条件は400,000 nodes、Threads 1、USI_Hash 128MB、MultiPV 2、定跡無効。各探索前に`isready → readyok → usinewgame`、全着手履歴を渡す。3エンジンを起動した状態で一つずつ探索し、局面ごとに実行順を循環させた。測定中、別のビルド・テストは開始していないが、OS全体の負荷や温度を制御した専用測定機ではない。

時間は既存Engineクライアントの`go`送信から`bestmove`受信後の出力解析まで。起動、評価関数ロード、readyok待ち、通信、保存、UI、サーバー待ち行列は含まない。各局面各構成1回で、p95は線形補間。統計的な速度優位や本番待ち時間を保証しない。

| 構成 | 探索数 | 中央値 | p95 | 最善手変更数（9.40比） |
| --- | ---: | ---: | ---: | ---: |
| 9.40＋Háo | 108 | 0.486秒 | 0.533秒 | — |
| 9.80＋Háo | 108 | 0.458秒 | 0.506秒 | 18 / 108 |
| 9.80＋Aoba | 108 | 0.777秒 | 0.843秒 | 33 / 108 |

324探索の入力履歴・SFEN一致、bestmove、要求PV数、USI出力の全PV中14,678手をpython-shogiで検証して成功。変更数は強さの指標ではない。評価値のboundを生ログへ残しており、bound付き値を確定CPとして比較する集計は行っていない。

別の停止試験では、各構成で`go infinite`後に最初の探索infoを待って`stop`し、10秒以内のbestmove返却を確認。続けて`7g7f 3c3d`後を400kで解析し、bestmoveと最終PV2本の合法性を確認した。各構成1回・探索開始直後の試験で、長時間探索中の中断レイテンシ保証ではない。

既存のNode向け再リンクWASMでも`node engine-wasm/smoke_test.mjs`を実行し、SIMD・非SIMDともusiok、readyok、400k探索のbestmoveを確認した。今回の9.80候補をWebアプリへ組み込んだ試験ではなく、Safari・WKWebView・pthreadの保証には使わない。

## 過去の品質検証の再照合

ローカルの`/path/to/aoba-retraining/oof_predictions.csv`と`evaluation.json`を使用し、600予測行・300局・600人・対局内fold一致を確認。MAE/RMSEをCSVから再計算し、保存済み評価と一致した。エンジン再実行・係数学習・bootstrapの再実行はしていない。

| モデル | OOF MAE | OOF RMSE |
| --- | ---: | ---: |
| 9.40＋Háo・再学習 | 181.455 | 230.900 |
| 9.80＋Aoba・再学習 | 177.966 | 226.683 |
| 9.80＋Aoba・Háo係数流用 | 177.337 | 226.183 |
| 定数予測 | 195.095 | 255.750 |

元検証はプレイヤーを跨がない5-fold（240局学習・60局評価）、400k、Threads 1、Hash 128MB、MultiPV 2。再学習モデル同士のMAE差（Aoba−Háo）は−3.489、保存済み95% bootstrap区間は[−8.117, +1.135]。改善なしを含む。Aoba再学習と係数流用の差も+0.629、区間[−0.503, +1.763]で、再学習の改善は示していない。

区間は固定OOF予測に対して対局単位・層内で10,000回再標本化した元の値であり、毎回モデルを再学習した区間ではない。同じコーパスで特徴量検討をしており、独立外部データによる検証でもない。複数棋譜を持つ同一ユーザーの偏差値・帯判定、悪手/ドリル品質の非劣性は別に残る。集計と入力SHAは[再照合結果](verification/engine-quality-recheck.json)に保存した。

過去のAoba対Háo予備対局は28局17勝11敗だったが、標本数・開始局面の相関・同時負荷の制約がある。これを棋力向上の確証とは扱わない。

## 採用判定に足りない証拠

全経路を置換する場合の未確認範囲は、native Linux x86_64実行とコスト、候補のAndroid/iOS実機・配布構成、Web/Safari/WKWebView接続、悪手/ドリル/棋力推定の品質、同一実時間の強さ、ロード・メモリ・発熱、旧新版混在・rollbackを含む。従来のLinux QEMU不正命令やAndroidエミュレータ完走だけで、この条件を埋めない。

サーバー検討専用への追加は別に判断する。この範囲では通常解析の係数学習や端末への候補エンジン同梱を採用条件に含めない。同一実時間の検討上の有用性、native Linuxの資源・費用、各クライアントからの利用、条件を識別した保存・キャッシュ、通常解析との分離、混在・rollbackを確認する。[同一実時間比較のプロトコル](engine-study-match-protocol.md)で予備対局を定義した。

現行workerの`Application.kt`と`UsiEngineSubprocess.kt`はFV_SCALEを`EngineInvariants.FV_SCALE`（20）から設定し、来歴・キャッシュもその値を使う。Aobaを検討専用で提供する場合も、倍率40を含む専用条件を実行と識別に一貫して通す必要がある。評価関数ファイルの単純置換では成立しない。今回この製品設定は変更していない。

#87の世代分離が本番適用済みでも、候補エンジンを通した保存・キャッシュ・遅延結果の実証は別。pthread配信と探索停止の現状は[#102の調査](study-depth-assessment.md)を参照。

## 再現手順

必要なものはPython 3、合法性検証用`python-shogi`、評価関数、検証用ネイティブ実行ファイル、入力JSON。入力は`moves_before`（USI文字列配列）と`sfen_before`を持つレコードの配列。実測バイナリ・個別棋譜・生ログはgit管理外のため、別環境では同じSHAの入力・成果物を用意する必要がある。

プロファイルJSONの例（最低2件、名前は一意。パスは実行時の作業ディレクトリ基準）:

```json
[
  {"name":"940-hao","binary":"/path/to/940-hao","evaluation":"/path/to/hao-eval","fv_scale":20},
  {"name":"980-aoba","binary":"/path/to/980-aoba","evaluation":"/path/to/aoba-eval","fv_scale":40}
]
```

今回のローカル成果物を使用する場合、リポジトリルートで:

```sh
python3 tools/benchmark_engine_profiles.py \
  --profiles /path/to/engine-profiles/profiles.json \
  --positions /path/to/positions.json \
  --output /path/to/recheck-output --count 108
python3 tools/verify_engine_profiles.py \
  /path/to/recheck-output --stop-check
```

既存出力への上書きは拒否する。計測はCPU負荷のある作業と並列にしない。検証時はassertを無効化する`python -O`を使用しない。元の生ログ・manifest・全入力は`/path/to/engine-profiles/serial108/`にあり、公開用集計には個別棋譜を含めていない。
