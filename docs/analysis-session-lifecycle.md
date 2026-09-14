# 解析セッションのライフサイクル

1.13では、解析セッションのライフサイクルとエンジン選択を共通の規則で扱う。OS固有の実装は、通知、プロセスの生存、バックグラウンド復帰の入口に限定する。

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
| 解析中の表示 | `AnalysisServiceBus`と共通レジストリ | 共通レジストリと`ImportState` |
| 解析中の通知 | Foreground通知で進捗を表示 | 解析開始時に通知権限を要求し、完了・失敗時に必要ならローカル通知 |
| 完了通知 | `gameId`付きPendingIntentでレポートを開く | `gameId`を通知情報に持たせ、タップ時にComposeへ伝えてレポートを開く |
| バックグラウンド復帰 | Foreground Serviceが解析を継続する | `PendingAnalysisStore`を起動時・フォアグラウンド復帰時に読み、必要なら同じ入力で再問い合わせ |
| キャンセル | Serviceのジョブキャンセル時にCoordinatorが後始末 | 現在の解析Jobをキャンセルし、Coordinatorが後始末 |

iOSはアプリが停止・サスペンドされると任意の解析処理を継続できないため、`PendingAnalysisStore`を解析開始前に保存する。解析結果が保存済みなら再起動時にpendingだけを破棄し、未保存なら同じ入力で再問い合わせする。通知権限が拒否されても、解析の保存と復帰には影響しない。

## 検証

- `AnalysisSessionCoordinatorTest`: 開始、局面進捗、完了、失敗、`CancellationException`後の後始末
- `AnalysisSessionPolicyTest`: 復帰時の再問い合わせ条件
- `AnalysisEngineSelectionTest`: サーバー、ネイティブ、WASMの選択優先順位
- Android: Foreground Serviceの進捗・完了・失敗通知、通知タップの`gameId`復帰（実機未接続のため動的確認は未実施）
- iOS: ローカル通知の完了・失敗、通知タップのレポート復帰、バックグラウンド後のpending再問い合わせ（シミュレータ起動とビルドのみ確認。実機はDeveloper Disk Image未対応のため動的確認は未実施）
