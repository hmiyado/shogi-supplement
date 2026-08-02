// :webApp（Kotlin/Wasm）から Worker/fetch を操作するための薄いブリッジ。
//
// Why 素のJSで書くか: Kotlin/WasmのJS相互運用（external宣言でのWorker/fetch/JSON直接操作）は
// 型付けが煩雑になりやすい一方、こちらの領域（Worker起動・postMessage・fetch）はプレーンな
// JavaScriptとして書けば従来のdocs/kento/app.js・analysis-worker.jsの実装をほぼそのまま
// 転用できる。Kotlin側との境界は「文字列とコールバック関数」だけに絞り、JSオブジェクトの
// 相互運用をこのファイル内に閉じ込める。
//
// 資産解決・Workerの起動・postMessageのメッセージ形（{workerLabel, variant, baseSfenArg,
// jobs, assetDirUrl}）は旧app.jsのロジックを踏襲する。analysis-worker.js自体は変更しない
// （そのプロトコルにこちらが合わせる）。
(function () {
  const bridgeScriptUrl = document.currentScript ? document.currentScript.src : null;

  function resolveWorkerScriptUrl() {
    if (!bridgeScriptUrl) {
      throw new Error("webapp-bridge.js: document.currentScript が取得できません");
    }
    return new URL("analysis-worker.js", bridgeScriptUrl).href;
  }

  function detectSimd128() {
    try {
      const bytes = new Uint8Array([
        0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00, 0x01, 0x05, 0x01, 0x60, 0x00, 0x01, 0x7b, 0x03, 0x02, 0x01,
        0x00, 0x0a, 0x0a, 0x01, 0x08, 0x00, 0x41, 0x00, 0xfd, 0x0f, 0xfd, 0x62, 0x0b,
      ]);
      return WebAssembly.validate(bytes);
    } catch {
      return false;
    }
  }
  const VARIANT = detectSimd128() ? "simd" : "nosimd";

  async function fetchTextAsync(url) {
    const resp = await fetch(url);
    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status} (${url})`);
    }
    return await resp.text();
  }

  function fetchText(url, onOk, onError) {
    fetchTextAsync(url).then(onOk).catch((err) => onError(String((err && err.message) || err)));
  }

  let assetDirUrlPromise = null;
  function resolveAssetDirUrlAsync(assetBaseUrl) {
    if (!assetDirUrlPromise) {
      assetDirUrlPromise = (async () => {
        const baseUrl = new URL(`${assetBaseUrl}/`, document.baseURI);
        const versionUrl = new URL("VERSION", baseUrl);
        const version = (await fetchTextAsync(versionUrl.href)).trim();
        if (!version) {
          throw new Error(`エンジンバージョン情報が空です (${versionUrl})`);
        }
        return new URL(`${version}/`, baseUrl).href;
      })();
    }
    return assetDirUrlPromise;
  }

  function resolveAssetDirUrl(assetBaseUrl, onOk, onError) {
    resolveAssetDirUrlAsync(assetBaseUrl)
      .then(onOk)
      .catch((err) => onError(String((err && err.message) || err)));
  }

  async function checkAssetsAvailableAsync(assetBaseUrl) {
    try {
      const assetDirUrl = await resolveAssetDirUrlAsync(assetBaseUrl);
      const wasmUrl = new URL(`yaneuraou-${VARIANT}.wasm`, assetDirUrl);
      const nnUrl = new URL(`nn.bin`, assetDirUrl);
      const [wasmResp, nnResp] = await Promise.all([
        fetch(wasmUrl, { method: "HEAD" }),
        fetch(nnUrl, { method: "HEAD" }),
      ]);
      return wasmResp.ok && nnResp.ok;
    } catch {
      return false;
    }
  }

  function checkAssetsAvailable(assetBaseUrl, onResult) {
    checkAssetsAvailableAsync(assetBaseUrl).then(onResult);
  }

  // 1局分の解析をW1/W2の2Workerで並列実行する。movesJsonは USI手列のJSON配列文字列。
  // onPosition(resultJson) は局面完了ごとに呼ばれる（resultJsonはanalysis-worker.jsの
  // "position"メッセージのresultフィールドをそのままJSON文字列化したもの）。
  // 戻り値はキャンセル用ハンドル（cancel()を呼ぶと即座に両Workerをterminateする）。
  function runAnalysis(baseSfenArg, movesJson, assetDirUrl, onPosition, onDone, onError) {
    const moves = JSON.parse(movesJson);
    const totalMoves = moves.length;
    const jobs = [];
    for (let ply = 0; ply <= totalMoves; ply++) {
      jobs.push({ ply, moves: moves.slice(0, ply) });
    }
    const half = Math.ceil(jobs.length / 2);
    const group1 = jobs.slice(0, half);
    const group2 = jobs.slice(half);

    const workerScriptUrl = resolveWorkerScriptUrl();
    let activeWorkers = [];
    let cancelled = false;

    function runWorker(workerLabel, jobGroup) {
      return new Promise((resolve, reject) => {
        if (!jobGroup.length) {
          resolve();
          return;
        }
        const worker = new Worker(workerScriptUrl);
        activeWorkers.push(worker);
        worker.onmessage = (ev) => {
          const msg = ev.data;
          if (msg.type === "position") {
            onPosition(JSON.stringify(msg.result));
          } else if (msg.type === "done") {
            resolve();
          } else if (msg.type === "error") {
            reject(new Error(`[${workerLabel}] ${msg.message}`));
          }
        };
        worker.onerror = (err) => {
          reject(new Error(`[${workerLabel}] Workerエラー: ${err.message || err}`));
        };
        worker.postMessage({ workerLabel, variant: VARIANT, baseSfenArg, jobs: jobGroup, assetDirUrl });
      });
    }

    Promise.all([runWorker("W1", group1), runWorker("W2", group2)])
      .then(() => {
        if (!cancelled) onDone();
      })
      .catch((err) => {
        if (!cancelled) onError(String((err && err.message) || err));
      });

    return {
      cancel() {
        cancelled = true;
        for (const w of activeWorkers) w.terminate();
        activeWorkers = [];
      },
    };
  }

  function goHome() {
    window.location.href = new URL("index.html", document.baseURI).href;
  }

  window.kentoBridge = {
    variant: VARIANT,
    fetchText,
    resolveAssetDirUrl,
    checkAssetsAvailable,
    runAnalysis,
    goHome,
  };
})();
