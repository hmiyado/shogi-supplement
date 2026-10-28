# iPhone Duo 対応

## 画面構成

[構成モック](mockups/iphone-duo.html)に沿い、既存の幅区分を使用する。
600dp未満はcompact、600〜839dpはmediumで1列、840dp以上はexpandedで
盤と本文を左右に配置する。端末名や物理的な向きではなく、現在のウィンドウ幅から選ぶ。
モックの幅は比較用であり、端末の論理座標・安全領域を確定した値ではない。

レポート・次の一手（出題、結果、検討）は、配置変更でペインの内部状態を破棄しない。
局面、入力済みの手順、本文のスクロール位置を保ち、変更後の状態・コールバックを使う。
スクロールは同じ内容位置を引き継ぐが、表示可能範囲が広がった際は末尾位置へ制限される。

iOSホストはComposeの現在のコンテナ幅とシステムのinsetを使う。
タブ画面の親も左右のinsetを保持し、ホーム・棋譜一覧・戦法のリスト本文へ適用する。
下端の余白はスクロール可能なcontentPaddingへ残す。
SwiftのUIViewControllerRepresentableは画面サイズ変更を理由にコントローラを再生成しない。
UIKitのタブコンテナは画面全高を管理し、縦配置でも操作領域を確保する。
タブの操作だけをネイティブ側で受け取り、それ以外はComposeの画面へ渡す。
縦型バーが占める横幅もComposeの安全領域へ反映し、本文をバーの下へ置かない。
ホーム・棋譜・定跡の切替は、バーの表示だけでなく遷移と復帰まで確認する。

## SDKと検証

AppleはiOS 27.1 SDK以降のビルドをDuoに最適化された表示として説明している。
検証にはXcode 27.1以降と、Duoに対応するDevice Hub／ランタイムを用いる。
既定のXcodeを変更する必要はなく、対象コマンドへ`DEVELOPER_DIR`を渡せる。

実端末またはDuo環境で次を確認する。

- ホーム、棋譜一覧、設定、同意、共有ダイアログで文字やボタンが欠けない。
- レポートで局面と本文位置を変え、開閉・回転を繰り返して維持を確認する。
- 検討中の入力手順、駒選択、成り選択を開閉・回転で失わない。
- 次の一手の出題・判定待ち・結果・検討で同様に確認する。
- キーボード、システムバー、安全領域と内容が重ならない。
- 大きな文字と明暗両テーマでも主要操作に到達できる。

幅区分を切り替える共通UIテストは、Duo上の実動作検証を代替しない。

## ストア画像

掲載用画像は`StoreImageTest`が共通UIとキャッチコピーを一緒に描画して生成する。
実機・Simulatorで撮った画像や、HTMLモックの画像は掲載用の入力にしない。
既存iPhone向けと同じ6画面（ホーム、ダークホーム、形勢グラフ、悪手一覧、出題、結果）を使う。
棋譜は`miyado_game1.kif`、解析結果は対応する`evals_game1.ndjson`から読み、合法手・手数・最善手の整合をテストで確認する。

```sh
cd app
./gradlew :androidApp:recordRoborazziDebug --tests '*StoreImageTest*' -Pshogi.storeImages=true --rerun-tasks
```

| 対象 | 完成画像 | アプリ表示幅 | アプリ表示の縦横比 |
| --- | --- | --- | --- |
| 外側 | 1398 × 2034 px・PNG | 466dp | 1398:2034 |
| 内側 | 2853 × 2007 px・PNG | 950dp | 2853:2007 |

大きな台紙で直接描画した後、完成画像を指定寸法へ縮小して透過を除去する。
小さい画像の拡大は検査で拒否する。OSのステータスバー・カメラ穴・折り目は描き足さない。
内側は1行のキャッチコピーと約77%のアプリ表示面積を確保し、盤のある画面は2ペインになる。
通常iPhoneの完成画像は従来の`fastlane/screenshots/ja/`へ、Duoの完成画像12枚は
`app/iosApp/fastlane/duo_screenshots/ja/`へ書き出す。

### アップロード対応

fastlane 2.241.2はDuoの内外寸法を`APP_IPHONE_DUO`として認識する。
[Appleの受付開始案内](https://developer.apple.com/news/?id=kkphp5qo)に従い、
提出にはテストで生成した完成画像を使う。

Duoの内外は同じ表示区分に入る。[表示区分あたりの上限10枚](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications)に合わせ、
ホーム・形勢グラフ・悪手一覧・出題・結果を内外各5枚、計10枚提出する。
ダークホームも含む生成物12枚はそのまま保持する。

`fastlane/store_screenshot_upload.rb`は通常iPhone向け12枚とDuo向け10枚を
`app/iosApp/build/store_screenshots/ja/`へコピーする。画像の再描画や変形はしない。
fastlaneと同じ寸法判定で表示区分と枚数を検査し、不足・異なる寸法・10枚超過では停止する。
`beta`の画像生成後と`submit`の提出前に準備し、`deliver`の`screenshots_path`へ渡す。

```sh
cd app/iosApp
bundle exec ruby fastlane/tests/store_screenshot_upload_test.rb
bundle exec ruby fastlane/store_screenshot_upload.rb
```

アップロード後はApp Store ConnectのDuo区分に内外の画像が反映されたことを確認する。
ローカルの分類・準備テストの成功は、サーバーでの受理確認を代替しない。

### 掲載用画像

| 画面 | 外側 | 内側 |
| --- | --- | --- |
| ホーム | [画像](../app/iosApp/fastlane/duo_screenshots/ja/01_home_duo_outer.png) | [画像](../app/iosApp/fastlane/duo_screenshots/ja/01_home_duo_inner.png) |
| ダークホーム | [画像](../app/iosApp/fastlane/duo_screenshots/ja/02_home_dark_duo_outer.png) | [画像](../app/iosApp/fastlane/duo_screenshots/ja/02_home_dark_duo_inner.png) |
| 形勢グラフ | [画像](../app/iosApp/fastlane/duo_screenshots/ja/03_report_graph_duo_outer.png) | [画像](../app/iosApp/fastlane/duo_screenshots/ja/03_report_graph_duo_inner.png) |
| 悪手一覧 | [画像](../app/iosApp/fastlane/duo_screenshots/ja/04_blunder_list_duo_outer.png) | [画像](../app/iosApp/fastlane/duo_screenshots/ja/04_blunder_list_duo_inner.png) |
| 次の一手の出題 | [画像](../app/iosApp/fastlane/duo_screenshots/ja/05_drill_question_duo_outer.png) | [画像](../app/iosApp/fastlane/duo_screenshots/ja/05_drill_question_duo_inner.png) |
| 次の一手の結果 | [画像](../app/iosApp/fastlane/duo_screenshots/ja/06_drill_result_duo_outer.png) | [画像](../app/iosApp/fastlane/duo_screenshots/ja/06_drill_result_duo_inner.png) |

## 検証結果

[検証記録と画像のSHA256](verification/iphone-duo.json)を参照する。
掲載用のテスト生成と、Duo Simulatorによるアプリの動作確認は別に実施する。
開いたレポートで1手進め、閉じて再び開いても選択局面を保持することをSimulatorで確認した。
機能テスト9件、iOS Simulator用フレームワーク、Xcodeのテスト用ビルド、Web向け共通UIコンパイルが成功した。
画像比較は51件中50件成功。残る`report_viewer_study_branch`は変更前の画像とSHA256が一致する既存の失敗で、基準画像は更新していない。
実機、回転の全組合せ、キーボード、大きな文字、すべての一時選択状態は未確認である。

## 既存UIの確認事項

次の一手の結果画面にある「あなたの手／最善手」の切替は枠線付きの丸い形を使っている。
`DESIGN.md`の画面内タブの規定との既存の不一致で、今回の対応では形を変更していない。

## 公式資料

- [Three steps to make your app shine on iPhone Duo](https://developer.apple.com/iphone-duo/prepare/)
- [Screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications)
