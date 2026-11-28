// docs/kento/wasm-analysis-host.html の実装本体（このページが公開する契約は同HTMLの
// 冒頭コメント参照）。バッチ側("wasmAnalysis"ハンドラ)と対話側("wasmStudy"ハンドラ)を
// 同一ページ内で両立させるため、通知先ハンドラごとに post()/postStudy() を分けている。

function post(type, extra) {
  try {
    window.webkit.messageHandlers.wasmAnalysis.postMessage(Object.assign({ type: type }, extra || {}));
  } catch (e) {
    // WKScriptMessageHandler未登録などpostMessage自体が失敗する状況では
    // 通知のしようがないため何もしない。
  }
}

function postStudy(type, extra) {
  try {
    window.webkit.messageHandlers.wasmStudy.postMessage(Object.assign({ type: type }, extra || {}));
  } catch (e) {
    // post() と同じ理由（バッチ用WKWebViewには"wasmStudy"ハンドラが無い）。
  }
}

window.onerror = function (message) {
  post("page-error", { message: String(message) });
  postStudy("study-page-error", { message: String(message) });
};

var activeHandle = null;

// baseSfenArgは常に"startpos"固定（GameAnalyzer.analyzeGameは常に平手初期局面基準）。
window.__startAnalysis = function (movesJson, assetBaseUrl) {
  window.kentoBridge.resolveAssetDirUrl(
    assetBaseUrl,
    function (assetDirUrl) {
      activeHandle = window.kentoBridge.runAnalysis(
        "startpos",
        movesJson,
        assetDirUrl,
        function (resultJson) {
          post("position", { result: JSON.parse(resultJson) });
        },
        function () {
          activeHandle = null;
          post("done");
        },
        function (message) {
          activeHandle = null;
          post("error", { message: message });
        },
      );
    },
    function (message) {
      post("error", { message: "エンジン資産バージョンの解決に失敗: " + message });
    },
  );
};

window.__cancelAnalysis = function () {
  if (activeHandle) {
    activeHandle.cancel();
    activeHandle = null;
  }
};

// 対話的単発局面解析（検討モード等）。study-worker.js を「1回分析したら使い捨てて
// 次のWorkerを事前初期化して待機させる」方式で運用する（常駐WKWebViewはページごと
// 生かしたまま、wasmインスタンスがModule.callMainを1回しか安全に実行できない制約は
// Workerの使い捨てで吸収する。docs/kento/study-worker.js ファイル冒頭のコメント参照）。
var studyAssetBaseUrl = null;
var nextWorker = null;
var nextWorkerReady = false;
var studyBusy = false;

function discardNextWorker(worker) {
  if (nextWorker !== worker) return;
  nextWorker = null;
  nextWorkerReady = false;
  worker.terminate();
  postStudy("study-worker-availability", { available: false });
}

function prepareNextWorker() {
  if (!studyAssetBaseUrl || nextWorker || studyBusy) return;
  var worker = new Worker(new URL("study-worker.js", document.baseURI).href);
  nextWorker = worker;
  worker.onmessage = function (ev) {
    if (nextWorker !== worker) return;
    if (ev.data.type === "prepared") {
      nextWorkerReady = true;
      postStudy("study-worker-availability", { available: true });
    } else if (ev.data.type === "error") {
      discardNextWorker(worker);
      postStudy("study-init-error", { message: ev.data.message });
    }
  };
  worker.onerror = function () {
    discardNextWorker(worker);
  };
  window.kentoBridge.resolveAssetDirUrl(
    studyAssetBaseUrl,
    function (assetDirUrl) {
      if (nextWorker === worker) {
        worker.postMessage({ type: "prepare", variant: window.kentoBridge.variant, assetDirUrl: assetDirUrl });
      }
    },
    function (message) {
      if (nextWorker !== worker) return;
      discardNextWorker(worker);
      postStudy("study-init-error", { message: "エンジン資産バージョンの解決に失敗: " + message });
    },
  );
}

// [assetBaseUrl] はローカル資産キャッシュのベースURL（絶対URL）。
window.__initStudy = function (assetBaseUrl) {
  studyAssetBaseUrl = assetBaseUrl;
  prepareNextWorker();
};

// [requestId] は一意な文字列であればよい（応答メッセージにそのまま付けて返す）。
// [baseSfenArg] は "startpos" または "sfen <SFEN文字列>"。
// [multiPv] は省略可（公開済みのアプリは渡してこない。省略時はstudy-worker.jsの既定）。
// 待機Workerの準備が済んでいない・既に別リクエストが進行中のときは即座にエラーを返す
// （検討中に何秒も待たせてからサーバーへ切り替えるのを避けるための即時失敗）。
window.__analyzePosition = function (requestId, baseSfenArg, movesJson, multiPv, purpose) {
  if (studyBusy || !nextWorkerReady || !nextWorker) {
    prepareNextWorker();
    postStudy("study-error", { requestId: requestId, message: "対話的解析ホストが未準備です" });
    return;
  }
  var worker = nextWorker;
  studyBusy = true;
  nextWorker = null;
  nextWorkerReady = false;

  function finish(type, payload) {
    worker.onmessage = null;
    worker.onerror = null;
    worker.terminate();
    studyBusy = false;
    postStudy("study-worker-availability", { available: false });
    postStudy(type, Object.assign({ requestId: requestId }, payload));
    prepareNextWorker();
  }

  worker.onmessage = function (ev) {
    var msg = ev.data;
    if (msg.type === "result") {
      finish("study-result", { result: msg.result });
    } else if (msg.type === "error") {
      finish("study-error", { message: msg.message });
    }
  };
  worker.onerror = function (err) {
    finish("study-error", { message: "Workerエラー: " + (err.message || err) });
  };
  worker.postMessage({ type: "analyze", baseSfenArg: baseSfenArg, movesJson: movesJson, multiPv: multiPv, purpose: purpose });
};

post("ready");
postStudy("study-ready");
