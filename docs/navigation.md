# 画面遷移とUIカタログ

リポジトリ直下から次のGradleタスクで遷移図を生成できる。

```sh
./app/gradlew -p app :application:generateNavigationDiagram
```

出力先は `app/androidApp/build/navigation-diagram/index.html`。
GradleタスクはKotlinの生成処理を実行し、コンパイルした `NavigationMachine.transitions` を読む。
遷移定義の複製やソースコードの正規表現解析は行わない。画面名・所属グループ・エラー画面の区別は `AppDestination`、グループ名は
`NavigationGroup` に集約する。生成側に画面一覧や配置座標を複製しない。
`NavigationTransition.kind` が遷移の種類（進む・戻る・再試行・エラー）を保持する。
表示の切り替えもこの種類で判断し、矢印の向きから意味を推測しない。
グループ内の進む遷移から自己遷移を除き、完了イベントを優先して循環を作らない配置用グラフを構築する。
その最長経路の段数で列を決め、途中の画面を飛び越す直接遷移があっても処理順を保つ。
循環を閉じる辺は配置計算からだけ除外し、遷移データには残す。孤立画面も先頭列に配置する。
各列の画面数から枠の寸法を計算し、矢印は列間・枠間の余白を直角に通す。
機能枠は個数に応じた格子状に配置し、図全体の寸法も自動計算する。カタログも更新する場合は
`app` で `./gradlew :androidApp:generateUiCatalog` を実行する。

`app/application` の `NavigationMachine.transitions` が可能な画面遷移の定義を持つ。
`AppDestination` は画面、`NavigationEvent` は操作・完了通知を表す。
`resolve` が `null` を返す操作は拒否し、`next` はその場合に現在地を保持する。
棋譜や表示データは各プラットフォームの状態に保持し、遷移可否を共通ロジックで判断する。

Androidの `MainViewModel`、iOSの `MainViewController`、Webの `KentoViewModel` と
`MyPageViewModel` が共通定義を参照する。マイページではログイン・読込中・一覧・詳細・
エラーを扱う。非同期取得は世代を照合し、ログアウトや別の取得開始より古い応答を破棄する。

図は実行履歴ではなく、共通ロジックが許可する遷移の集合である。
プラットフォームごとの機能差は統合表示し、各端末がすべての矢印を提供することは意味しない。
画面内タブ、ダイアログ、ブラウザによる外部URL移動は対象外。

`generateUiCatalog` は `generateNavigationDiagram` を先に実行する。
遷移図HTMLを `srcdoc` に埋め込むため、別のHTMLファイルを一緒に配布する必要はない。
図のJavaScript・フォントも埋め込み、外部CDNへ依存しない。

画面追加時は `AppDestination` と遷移表を変更し、対応する画面状態をその遷移先に対応させる。
同じ遷移元・操作から複数の遷移先を定義しない。
共通テストで遷移可否を、JVMテストで全遷移の図への出力を検証する。

生成後に `node tools/test_navigation_diagram.cjs` で配置・操作を検証できる。
循環、孤立画面、グループ追加、画面数増加も合成データで検証する。

生成コードはKotlinのデータ出力と、`app/application/src/jvmMain/resources/navigation/` の
HTML・CSS・JavaScriptに分かれる。出力時にまとめるため、完成HTMLは単体で開ける。
JavaScriptのロジック検証は `:application:jvmTest` の依存タスクとして実行する（Node.jsが必要）。
`tools/navigation-browser` のPlaywrightテストは実DOMでスクロール・ズーム・選択・狭い画面への
フィットを検証し、Navigation Diagramワークフローで実行する。
ローカルで実行する場合は生成後に同ディレクトリで `npm ci`、
`npx playwright install chromium`、`npm test` を実行する。
