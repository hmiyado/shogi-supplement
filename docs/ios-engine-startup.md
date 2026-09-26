# iOS同梱エンジンの起動

同梱版はYaneuraOu v9.40をアプリ内スレッドで実行する。起動時のUSI通信と評価データ読み込みもブロッキング処理なので、検討エンジンの生成はIOディスパッチャで行う。

## 静的ライブラリのリンク

探索本体は静的初期化の `EngineFuncRegister` で登録される。通常の `-lshogiengine` だけでは探索本体のオブジェクトが省かれ、登録が空のまま `run_engine_entry` が `exit(1)` する。

XcodeとKotlin/Nativeテストでは、対象SDKの `libshogiengine.a` を `-force_load` でリンクする。エンジン非同梱構成には適用しない。

## 評価データ読み込み時の停止

v9.40の `shm.h` は、共有メモリを使えずローカルメモリへ移る際のログで、同じ出力の途中に `sync_cout` を繰り返す。このマクロは非再帰mutexを取るため、`readyok` を返す前に停止する。

`app/iosApp/engine/prepare_source.py` は固定した上流ソースからビルド用コピーを作り、このログの2回目以降を `std::cout` へ置き換える。最初のロックと末尾の `sync_endl` によるアンロックは維持する。評価データ・探索ロジックは変更しない。

上流checkoutは書き換えない。想定した修正対象が見つからなければビルドを失敗させる。ソース変更時はヘッダ変更も含めてオブジェクトを再生成する。

## 回帰確認

リポジトリルートで以下を実行する。

```sh
bash app/iosApp/engine/build_yaneura_v940_native.sh app/iosApp/engine/build/native-smoke
python3 app/iosApp/engine/test_usi_startup.py \
  app/iosApp/engine/build/native-smoke/YaneuraOu-NNUE-m1 \
  app/androidApp/src/main/assets/eval
```

同じ修正ソースを使うMac arm64実行体で、USI準備完了、40万ノードの候補手3本・2本、次局の準備、正常終了を期限付きで確認する。iOSのリンクと画面の接続は、別途実機ビルドを上書きインストールして既存棋譜の「レポート → 検討」で評価値・候補手が出ることを確認する。
