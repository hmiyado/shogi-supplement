# engine-wasm — やねうら王 WASM版ビルド一式

将棋サプリの検討機能（アプリ本体の一部機能、および `docs/kento.html` のWebページ）で
使うやねうら王のWASMビルド（ブラウザ内で完結する将棋エンジン）一式。

## これは何か

`docs/kento.html`（GitHub Pages配信）はこのディレクトリのビルド成果物（WASMバイナリ）
を配信する。WASMバイナリはやねうら王の思考エンジン本体＋Emscripten向けパッチを含む
プログラムの実行形式であり、GPLv3の頒布条件（対応するソースコードの提供）が生じる。
本リポジトリ（LICENSE = GPLv3）内にビルド一式を置くことで、配信物と対応ソースを
揃えている。製品として配信するビルドの実体はこのディレクトリを正とする。

## 構成

| ファイル/ディレクトリ | 内容 |
|---|---|
| `patches/0001-wasm-build-fixes.patch` | やねうら王v7.00へのEmscripten向け最小パッチ（5ファイル・84行）。wasm32でのビット走査判定追加と、`-pthread`なしビルドのためのstd::thread同期実行フォールバック |
| `fetch_upstream.sh` | やねうら王上流ソースを固定コミットへ取得し、パッチを適用する |
| `build_wasm_browser.sh` | ブラウザ向けWASM（simd/nosimd 2変種）をビルドする。副産物としてスモークテスト専用のNode向けビルドも作る |
| `smoke_test.mjs` | Node上でUSIプロトコル（usi→isready→position→go→bestmove）が一通り通ることを確認する最小テスト |
| `upstream/`（.gitignore対象） | `fetch_upstream.sh`が取得するやねうら王のソース。コミットしない |
| `out-browser/`（.gitignore対象） | ビルド成果物。コミットしない（サイズが大きく、`fetch_upstream.sh`→`build_wasm_browser.sh`で再現可能なため） |

## 上流バージョンとパッチ

- リポジトリ: https://github.com/yaneurao/YaneuraOu
- 固定コミット: `0640f43c7efb84630d657e99d6c8b5353062be1c`（タグ`v7.00`が指すコミット。
  タグ名ではなくコミットSHAへ直接ピン止めしている）
- 構成: `YANEURAOU_ENGINE_NNUE`（NNUE型評価関数）
- パッチの要旨:
  - `source/config.h`: `__EMSCRIPTEN__`かつ`-pthread`なし（`__EMSCRIPTEN_PTHREADS__`未定義）
    の場合にのみ有効になる`YO_WASM_NO_THREAD`マクロを追加
  - `source/extra/bitop.h`: wasm32ではx86専用のビット走査命令が使えないため、
    汎用スカラー実装を使うよう条件分岐を追加
  - `source/misc.cpp` / `source/thread.cpp` / `source/usi.cpp`: `YO_WASM_NO_THREAD`
    構成時、実スレッドを生成する3箇所（isreadyのkeep-aliveスレッド・置換表クリアの
    並列化・探索本体のstd::thread）を、呼び出し元での同期実行に置き換え

パッチにより失われる挙動: `stop`/`go infinite`等、探索を非同期に中断する対話的
シーケンスが動かない（`go nodes N`が完了するまでブロッキングする）。バッチ解析用途
（本アプリの使い方）には影響しない。

## ビルド手順

前提: Docker（`emscripten/emsdk:4.0.23`イメージを使用。Apple Siliconではarm64
ネイティブ実行）。

```bash
./fetch_upstream.sh        # 上流を固定コミットへ取得しパッチを適用（初回のみ。以後は冪等）
./build_wasm_browser.sh    # ブラウザ向け2変種(simd/nosimd)をビルド → out-browser/
node smoke_test.mjs        # Node向けスモークビルドでUSI疎通を確認
```

評価関数（Háo、GPLv3）は複製せず `app/androidApp/src/main/assets/eval/nn.bin` を直接
参照する。`build_wasm_browser.sh`はビルド成果物の配布物一式を`out-browser/`ひとつに
まとめる目的でこのファイルを`out-browser/nn.bin`へ複製するが、リポジトリへ
コミットする複製を新たに作るわけではない（`out-browser/`自体が.gitignore対象）。

`docs/copy-kento-assets.sh`が`out-browser/`から`docs/kento-assets/`
（同じく.gitignore対象・GitHub Pagesが実際に配信するディレクトリ）へコピーする。

## 変種（simd / nosimd）

- `nosimd`: SIMD命令を使わない素のスカラーコード
- `simd`: 同じソースに`-msimd128`を足しただけ（LLVMの自動ベクトル化のみ・ソース書き換えなし）

x86用SIMDマクロ（USE_AVX2等）はwasm32ではコンパイル不能なため両変種とも未定義で、
SIMD有無はコンパイラフラグだけで切り替わる。ページ側（`docs/kento/app.js`）が
`WebAssembly.validate`でSIMD対応を自動判定し、対応ブラウザには`simd`、非対応には
`nosimd`を配信する。

## 解析条件（固定・不変）

`go nodes 400000` / `Threads=1` / `USI_Hash=128` / `MultiPV=2` / `FV_SCALE=20`。
アプリ本体・サーバー解析・このWASMビルドで条件を統一し、解析結果の再現性を
保証している（変更するUIは意図的に設けていない）。

## ライセンス・帰属表示

- やねうら王本体: GPLv3。© yaneurao and contributors.
  https://github.com/yaneurao/YaneuraOu
- 評価関数 Háo: GPLv3。作者 nodchip（tanuki-プロジェクト）。
  https://github.com/nodchip/tanuki-/releases/tag/tanuki-.halfkp_256x2-32-32.2023-05-08
  （`app/androidApp/src/main/assets/eval/nn.bin`のSHA-256が上記配布物と一致することを
  確認済み）
- 本ディレクトリの追加コード（パッチ・ビルドスクリプト）も本リポジトリ全体と同じ
  GPLv3のもとで公開する（`LICENSE`参照）。
