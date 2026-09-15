# 解析セッションのライフサイクル

1.13では、解析セッションのライフサイクルとエンジン選択を共通の規則で扱う。OS固有の実装は、プロセスの生存とバックグラウンド復帰の入口に限定する。

## 共通の遷移

```text
開始
  │
  ▼
解析中 ── 完了 ──▶ 保存済み
  │
  ├──── 失敗 ──▶ 再開可能な失敗
  │
  └──── キャンセル ──▶ 終了
```

`AnalysisSessionCoordinator` は開始時に共通レジストリへ登録し、局面結果を更新し、完了・失敗・キャンセルを問わず終了時に登録を削除する。バックグラウンド復帰時の再問い合わせは、解析中かつ最後の進捗から5秒以上経過した場合だけ行う。再問い合わせは同じ入力を使うため、サーバー側の冪等性を保つ。

エンジン経路は次の優先順位を共通化する。

1. サーバー解析（障害時は同条件のWASMへ切り替え）
2. サーバーが使えず、ネイティブエンジンがある場合は端末ネイティブ
3. それ以外は端末WASM

## OSごとの責務

| 観点 | Android | iOS |
| --- | --- | --- |
| 解析の実行主体 | Foreground Service | `IosMainController`のスコープ |
| 解析中の表示 | Foreground Serviceの必須通知で進捗を表示 | 共通レジストリと`ImportState` |
| 完了・失敗の通知 | 実装しない。ServiceBusで画面へ結果を伝える | 実装しない。画面を再表示した時に保存状態を読み直す |
| バックグラウンド復帰 | Foreground Serviceが解析を継続する | `PendingAnalysisStore`を起動時・フォアグラウンド復帰時に読み、必要なら同じ入力で再問い合わせ |
| キャンセル | Serviceのジョブキャンセル時にCoordinatorが後始末 | 現在の解析Jobをキャンセルし、Coordinatorが後始末 |

iOSはアプリが停止・サスペンドされると任意の解析処理を継続できないため、`PendingAnalysisStore`を解析開始前に保存する。解析結果が保存済みなら再起動時にpendingだけを破棄し、未保存なら同じ入力で再問い合わせする。

## 検証

- `AnalysisSessionCoordinatorTest`: 開始、局面進捗、完了、失敗、`CancellationException`後の後始末
- `AnalysisSessionPolicyTest`: 復帰時の再問い合わせ条件
- `AnalysisEngineSelectionTest`: サーバー、ネイティブ、WASMの選択優先順位
- Android: APK同梱エンジンはAVD上で`usiok`応答を確認。KIF取込からの解析再実行はAVDのSystem UI/アプリANRで未確認。
- iOS: iPhone 16eシミュレータでKIF取込→解析→レポート自動遷移のE2Eを確認。バックグラウンド後のpending再問い合わせは未確認。接続済みiPhone 16eはロック中のためDeveloper Disk Imageをマウントできず、実機動的確認は未実施。
