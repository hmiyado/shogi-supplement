# #97 解析エンジン更新の実現性確認

確認日: 2026-09-15

## 判断

技術的な移行経路はあるが、現時点では「全プラットフォームで最新化し、強くなった」として完了にはできない。単一のタグを書き換えるのではなく、エンジン更新を独立した移行・計測作業として扱う。

公式の v9.40 配布物はふかうら王の Windows 版であり、Android・iOS・WASM に同梱している NNUE バイナリの代用品ではない。公開 NNUE 候補として V9.00 などはあるが、採用する版、完全なコミット SHA、評価関数、アーキテクチャを別途固定する必要がある。

## 現行実装で確認した課題

| 対象 | 課題 |
| --- | --- |
| iOS | `build_ios.sh` は既存の `upstream/` を無条件に再利用する。タグだけ変えると旧ソースを再ビルドし得るため、取得 SHA とビルド条件の識別が必要。 |
| Android | `libyaneuraou_usi.so` は外部 checkout を使うローカルの研究用ビルド成果物で、再現可能な取得・NDK ビルド・成果物ハッシュの記録がない。 |
| Linux/ネイティブ | 現行の iOS/Linux/Android のソース一覧は v7 向け。新版ではファイル名や探索部の構成が変わるため、一覧を据え置いた版更新は成立しない。 |
| WASM | 現行は v7 に独自のスレッド無しパッチを適用している。新版の pthread/SIMD 経路を採用するなら、SharedArrayBuffer、COOP/COEP、WebView、配信条件まで再検証が必要。 |
| キャッシュ | サーバーの解析キャッシュキーにエンジン版が含まれない。更新後も旧結果を返し得るため、解析プロファイルをキーと来歴へ含め、旧結果との互換・ロールバックを定義する必要がある。 |
| 保存結果 | `engine_meta` はサーバー結果に存在するが、端末側の保存モデルや一部のリモート変換ではエンジン来歴が十分に保持されない。 |
| 較正 | 400k nodes、MultiPV、FV_SCALE を維持しても探索部が変われば評価値・悪手判定・推定棋力・ドリル判定が変わり得る。既存の係数と Golden は新版の強さを証明しない。 |

## 完遂時の受入条件

1. エンジン候補 SHA、評価関数 SHA、ライセンス、コンパイル条件、USI オプションを固定する。
2. 既存成果物を上書きせず、Linux x86_64、Android arm64、iOS simulator/device、WASM の全経路を新版ソースでビルドする。
3. エンジン版・評価関数版・パッチ・オプションを解析キャッシュ、保存結果、同期結果の識別情報へ通す。旧クライアント、旧キャッシュ、失敗時のロールバックも確認する。
4. 同一条件で既存の 108 局面を各プラットフォーム比較し、bestmove、PV、評価値、詰み、中断・復帰、連続呼出しを確認する。旧 Golden は保存し、新結果を別管理する。
5. 新旧で同一ノード数と同一実時間の両方を計測し、強さ、待ち時間 p50/p95、NPS、メモリ、初回ロード、発熱、サーバーコストを比較する。
6. 悪手一致率、判定逆転、推定棋力の偏りを確認し、必要なら係数と `coef_version` を再較正する。
7. Android/iOS 実機、Web/iOS WebView、旧版からの上書き更新、サーバーと端末の混在を確認する。

## 1.13 での扱い

上記のビルド、互換性、強さ・性能計測、実機確認が未完了のため、1.13 のリリースノートに「AI を最新化・強化した」とは記載しない。#97 はこの受入条件を満たすまで未完了として残す。

## 2026-09-17 の追加確認（#101）

v9.40 の固定コミット `717da871e7a620702b8b9433bd8f9f181710435a` と、`YANEURAOU_ENGINE_NNUE` 構成のソース一覧を使い、ターゲット別のビルドを追加確認した。

| 対象 | 結果 | 証跡 |
| --- | --- | --- |
| macOS arm64 ネイティブ | ビルド成功。`TARGET_CPU="APPLEM1"` と macOS target を指定した再ビルドも成功 | 一時成果物 `YaneuraOu-NNUE-m1`。旧 SHA-256 `8ca6eb694904acaa03406fa8c61cf6b8aa9ee9f5b43b0b2b8e938b91997894de`、新 SHA-256 `cf0e887643028987f753d85a0e2fec56377fb28902700dcbcb741e32d8e04074` |
| iOS simulator/device | 静的ライブラリのビルド成功 | `build_ios.sh sim/device` |
| Android arm64-v8a | ビルド成功 | SHA-256 `33574c5f5c4bc6d043cb1239b9acc7d85a55e86f747d097d8ab3274dc0bca58d` |
| Linux x86_64 | 既存 ELF 成果物を確認 | Docker daemon が利用できず、この環境での実行比較は未実施 |

再現条件は、Apple clang `21.0.0 (clang-2100.1.1.101)` と、固定コミット・ソース一覧・コンパイル条件を検証する `app/iosApp/engine/build_yaneura_v940_native.sh` を使うものとした。出力先を指定して `app/iosApp/engine/build_yaneura_v940_native.sh <output-dir>` を実行し、生成された新バイナリと既存の旧バイナリへ次の USI 入力を送る。`REPO_ROOT` は checkout のルートを指す。評価関数ファイルの SHA-256 は `1141d275bceec911156801f27303dc9ff5beb24f4f59144cc069306c59e80782` である。

```sh
REPO_ROOT="$(git rev-parse --show-toplevel)"
EVAL_DIR="$REPO_ROOT/research/engine/eval_hao"
printf '%s\n' \
  'usi' \
  'setoption name USI_OwnBook value false' \
  'setoption name Threads value 1' \
  'setoption name USI_Hash value 128' \
  'setoption name MultiPV value 2' \
  'setoption name NetworkDelay value 0' \
  'setoption name NetworkDelay2 value 0' \
  "setoption name EvalDir value $EVAL_DIR" \
  'setoption name FV_SCALE value 20' \
  'isready' 'quit' | timeout 90 <engine-path>
```

`<engine-path>` を旧新それぞれのバイナリへ置き換え、計測はプロセス起動時から開始する。`loading eval file` と `readyok` の出力、および 90 秒の終了状態を記録する。

実行時確認では、旧 V7.00 macOS arm64 バイナリは評価関数を読み込み `readyok` に到達した。一方、v9.40 macOS arm64 バイナリは評価関数のロード開始メッセージを返したが、プロセス起動から 90 秒以内に `readyok` へ到達しなかった。この観測だけでは評価関数の非互換性までは確定できないため、v9.40 は現行評価関数での実行時互換性が未確認で、採用可能とは判定しない。

この結果により、完遂時の受入条件 2 はビルド範囲に限って前進した。条件 3（解析キャッシュ・保存結果・同期結果への来歴付与、旧クライアントとロールバック対応）、条件 4（全プラットフォームの parity と実行時互換性）、条件 5（強さ・性能）、条件 6（較正）、条件 7（実機・混在環境）は未完了である。

参照:

- https://github.com/yaneurao/YaneuraOu/releases/tag/v9.40
- https://github.com/yaneurao/YaneuraOu/releases/tag/V9.00
