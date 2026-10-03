# 検討 WASM の隔離条件プローブ

製品アプリやエンジンを変更せず、共有メモリを利用できるページ配信条件を比較する。
通常 HTTP、COOP/COEP 付き HTTP、Service Worker による応答ヘッダー付与を調べる。
iOS は製品と同じ `kento` カスタムスキームについて、ヘッダーなし／ありも比較する。
これは pthread エンジンの起動・性能・中断の合格を意味しない。

リポジトリのルートから実行:

```sh
python3 tools/study-isolation-probe/server.py
# 別ターミナル。tools/navigation-browser の Playwright と Chromium を利用する。
node tools/study-isolation-probe/browser.cjs build/study-isolation-chromium.json
# xcrun simctl list devices available で対象を選び、起動してから実行する。
bash tools/study-isolation-probe/run-ios.sh <booted-simulator-UDID> build/study-isolation-ios.json
```

サーバーは `127.0.0.1:4178` のみで待ち受ける。終了は Ctrl-C。
iOS は Apple Silicon と Xcode を前提とする。専用アプリ `dev.miyado.issue102-probe` をインストールする。
終了後に不要なら `xcrun simctl uninstall <UDID> dev.miyado.issue102-probe` で削除する。
ビルド資産は `build/study-isolation.*` に残す。製品アプリのデータは読み取らない。

出力の `isolated`、`sab`、`sharedMemory` がすべて true かを確認する。
Service Worker が利用不能な場合は `serviceWorker: false` / `controlled: false` となる。
プローブは失敗も記録するため、スクリプトの終了コードだけで利用可能と判定しない。
WKWebView の Service Worker 可否はアプリの設定にも依存する。このプローブは製品同様、
App-Bound Domains や非公開設定を追加していない。実機・Safari・実際の Pages 配信は別途確認する。
