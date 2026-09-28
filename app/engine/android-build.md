# Android USIエンジン

同梱するarm64-v8a実行ファイルは、v9.40の固定コミット
`717da871e7a620702b8b9433bd8f9f181710435a`から生成する。

```sh
bash app/engine/build_android.sh /path/to/YaneuraOu /path/to/android-ndk /path/to/output
```

未変更のcheckoutだけを受け入れる。共通のソース一覧と、iOSの`prepare_source.py`にある
共有メモリフォールバック時の非再帰ロック修正を使用する。未修正のAndroidバイナリは
評価関数の読み込みで`readyok`を返さず停止するため、ビルド成功だけでは配布しない。

NDK 29.0.13846066で生成。Android API 26、arm64、NEON、静的C++ランタイム、
16KiBのELFページ境界を使う。評価関数Háoと解析条件は変更しない。
出力は`libyaneuraou_usi.so`。実体は共有ライブラリではなくUSI実行ファイルで、
AndroidのnativeLibraryDirへ配置するためにこの名前を使う。

同梱先は`app/androidApp/src/main/jniLibs/arm64-v8a/libyaneuraou_usi.so`。
2026-09-28の修正版SHA-256:
`c0cde515ce1e9aac2d1253c67bc2f2dbe20949a2ee163b582da397321406def3`。

実行ゲートは実機またはエミュレータでのUSI初期化、400k探索（MultiPV3と2）、
アプリからの棋譜取り込み・解析完了。単体USI検証には
`app/iosApp/engine/test_usi_startup.py`を利用できる。
