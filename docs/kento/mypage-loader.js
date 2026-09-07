// Compose Wasm本体（十数MB）の読み込み中はcomposeAppが空でページが無反応に見えるため、
// 静的HTMLのローダーを置いている。Composeはこのdivへcanvasを追加するので、
// canvasの出現をもって役目を終える。
//
// Why not インラインに書く: このページのCSPはインライン実行を許していない。
// 鍵を読めるスクリプトの出所を'self'と reCAPTCHA だけに絞るため、ハッシュやnonceで
// 例外を作らずファイルへ出す。
(function () {
  const container = document.getElementById("composeApp");
  const loading = document.getElementById("appLoading");
  if (!container || !loading) return;
  const observer = new MutationObserver(() => {
    if (container.querySelector("canvas")) {
      loading.remove();
      observer.disconnect();
    }
  });
  observer.observe(container, { childList: true, subtree: true });
})();
