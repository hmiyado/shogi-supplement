# DB・画面のリグレッションテスト

## DB互換性

リポジトリルートで実行する。

```sh
supabase start --workdir infra
python3 tools/db-regression/run.py
```

接続先はローカルの `supabase_db_infra` コンテナに固定している。本番の接続情報は使用しない。
不足するマイグレーションとフィクスチャはトランザクション内で適用し、各検証後にロールバックする。

`current-client.sql` は定跡テーブル追加前後で、既存クライアントのアップロード・復元・検討保存の競合制御・解析世代の置換と削除・他ユーザーからの隔離を検証する。
前後各13項目と `infra/supabase/tests/` の88項目、計114項目を実行する。
これは既存API契約の検証であり、ストア配布済みアプリのバイナリを動かすE2Eではない。

GitHub Actionsの `Supabase regression` がSQL・ランナー変更時のPRとmainへのpushで実行する。

## ラベル読み込み待ち

```sh
cd app
./gradlew :androidApp:testDebugUnitTest --tests '*GameListScreenScreenshotTest.labelsAreReadyBeforeTheFirstCardAndStaleOwnersCannotPublish'
```

iOS棋譜一覧が利用する共通Composableについて、ラベル確定前は一覧を描画しないこと、アカウント切替後に古い結果を表示しないことを検証する。

## iOS Liquid Glass

同意済みのテスト用iOS 26シミュレータを指定する。研究利用への同意をこのテストでは自動操作しない。

```sh
xcodebuild -project app/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,id=<SIMULATOR_UUID>' \
  -only-testing:iosAppUITests/RootNavigationRegressionTests \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- test
```

タブと追加ボタンの重なり、円形サイズ、下部セーフエリアとタイトル背景の帯、追加・設定・各ルートタブへの遷移を検証する。
ネイティブ素材そのものの描画品質は画像の目視確認も必要。端末・テーマ全組合せのVRTではない。
