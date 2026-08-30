// 「エンジン温存＋局面ごとisready→usinewgame」方式(TT=置換表を局面ごとにクリアして
// プロセス再起動と同等の決定性を保ちつつ、評価関数ロードは初回だけで済ませる)を採る。
// setoption群・go nodes 400000は本番解析条件と完全一致させる必要があるため、変更UIは設けない。
//
// キャンセルは呼び出し側がこのWorkerをterminate()することで行う。"go"は同期ブロッキング
// 呼び出しのため、Worker内部から解析を安全に中断する手段はないが、Worker.terminate()は
// JSが同期実行中でも即座に効く(ブラウザ実装の保証)ので、局面の区切りを待たずに止められる。

importScripts("wasm-asset-cache.js");

self.onmessage = async (ev) => {
  const { workerLabel, variant, baseSfenArg, jobs, assetDirUrl } = ev.data;
  try {
    await runSequential(workerLabel, variant, baseSfenArg, jobs, assetDirUrl);
  } catch (err) {
    post({ type: "error", workerLabel, message: String((err && err.stack) || err) });
  }
};

function post(msg) {
  self.postMessage(msg);
}

function now() {
  return performance.now();
}

// USIのinfo行を{depth, seldepth, multipv, nodes, time, nps, score:{cp|mate}, pv}にパースする。
function parseInfo(line) {
  const toks = line.split(/\s+/);
  const d = {};
  let i = 0;
  while (i < toks.length) {
    const t = toks[i];
    if (t === "depth" || t === "seldepth" || t === "multipv" || t === "nodes" || t === "time" || t === "nps") {
      d[t] = Number(toks[i + 1]);
      i += 2;
    } else if (t === "score") {
      const kind = toks[i + 1];
      const val = Number(toks[i + 2]);
      d.score = { [kind]: val };
      i += 3;
    } else if (t === "pv") {
      d.pv = toks.slice(i + 1);
      break;
    } else {
      i += 1;
    }
  }
  return d;
}

// 本番不変条件のため、これらの値を変更するUIは設けない。
const SETOPTIONS = [
  ["EvalDir", "/eval"],
  ["USI_OwnBook", "false"],
  ["Threads", "1"],
  ["USI_Hash", "128"],
  ["MultiPV", "2"],
  ["NetworkDelay", "0"],
  ["NetworkDelay2", "0"],
  ["FV_SCALE", "20"],
];
const GO_NODES = 400000;

async function runSequential(workerLabel, variant, baseSfenArg, jobs, assetDirUrl) {
  // 資産の場所(assetDirUrl)は呼び出し側で「ベースURL(設定1箇所)＋/<エンジンバージョン>/」を
  // 解決した絶対URLをpostMessageで受け取る。このWorkerは自分で相対パスを組み立てない
  // (self.location.hrefは使わない。資産参照の設定箇所を一箇所に集約するため)。
  const jsUrl = new URL(`yaneuraou-${variant}.js`, assetDirUrl).href;
  const wasmUrl = new URL(`yaneuraou-${variant}.wasm`, assetDirUrl).href;
  const nnUrl = new URL(`nn.bin`, assetDirUrl).href;

  post({ type: "stage", workerLabel, stage: "loading-module" });
  const tLoad0 = now();

  importScripts(jsUrl);
  const factory = self.createYaneuraOu;
  if (typeof factory !== "function") {
    throw new Error(`createYaneuraOu が見つかりません(${jsUrl})`);
  }

  const results = [];
  let segStart = null;
  let readyokT = null;
  let latestMultipv = { 1: null, 2: null };
  const stderrTail = [];
  let abortMessage = null;

  function handleStdout(line, t) {
    if (line === "readyok") {
      readyokT = t;
      latestMultipv = { 1: null, 2: null };
      return;
    }
    if (line.startsWith("info depth") && line.includes(" pv ")) {
      const parsed = parseInfo(line);
      const mpv = parsed.multipv === undefined ? 1 : parsed.multipv;
      if (mpv === 1 || mpv === 2) latestMultipv[mpv] = parsed;
      return;
    }
    if (line.startsWith("bestmove")) {
      const idx = results.length;
      const job = jobs[idx];
      const bestmove = line.split(/\s+/)[1] || null;
      const isreadyMs = readyokT !== null ? readyokT - segStart : null;
      const searchMs = readyokT !== null ? t - readyokT : null;

      const mpv1 = latestMultipv[1];
      const mpv2 = latestMultipv[2];

      const result = {
        ply: job ? job.ply : idx,
        isreadyMs,
        searchMs,
        bestmove,
        score: mpv1 ? mpv1.score || null : null,
        nodes: mpv1 ? mpv1.nodes ?? null : null,
        depth: mpv1 ? mpv1.depth ?? null : null,
        pv: mpv1 ? mpv1.pv || [] : [],
        multipv2: mpv2 ? { score: mpv2.score || null, pv: mpv2.pv || [] } : null,
      };
      results.push(result);
      post({ type: "position", workerLabel, index: idx, result });

      segStart = t;
      readyokT = null;
      latestMultipv = { 1: null, 2: null };
    }
  }

  const Module = await factory({
    locateFile: (path) => (path.endsWith(".wasm") ? wasmUrl : path),
    print: (line) => handleStdout(line, now()),
    printErr: (line) => {
      stderrTail.push(line);
      if (stderrTail.length > 20) stderrTail.shift();
    },
    onAbort: (what) => {
      abortMessage = String(what);
    },
  });
  const tLoad1 = now();
  post({ type: "stage", workerLabel, stage: "module-loaded", loadMs: tLoad1 - tLoad0 });

  post({ type: "stage", workerLabel, stage: "fetching-nn" });
  const tFetch0 = now();
  const nnBuf = await self.kentoWasmAssetCache.fetchCachedArrayBuffer(nnUrl);
  const tFetch1 = now();

  Module.FS.mkdir("/eval");
  Module.FS.writeFile("/eval/nn.bin", new Uint8Array(nnBuf));
  const tWrite1 = now();
  post({
    type: "stage",
    workerLabel,
    stage: "nn-ready",
    fetchMs: tFetch1 - tFetch0,
    writeMs: tWrite1 - tFetch1,
  });

  const lines = ["usi"];
  for (const [name, value] of SETOPTIONS) {
    lines.push(`setoption name ${name} value ${value}`);
  }
  for (const job of jobs) {
    lines.push("isready");
    lines.push("usinewgame");
    const posArg = job.moves.length
      ? `${baseSfenArg} moves ${job.moves.join(" ")}`
      : baseSfenArg;
    lines.push(`position ${posArg}`);
    lines.push(`go nodes ${GO_NODES}`);
  }
  lines.push("quit");

  const argv = [];
  for (const l of lines) {
    argv.push(l);
    argv.push(",");
  }

  post({ type: "stage", workerLabel, stage: "running", n: jobs.length });
  const tGoStart = now();
  segStart = tGoStart;
  Module.callMain(argv);
  const tGoEnd = now();

  if (abortMessage) {
    throw new Error(
      `Module.onAbort: ${abortMessage}` + (stderrTail.length ? ` / stderr末尾: ${stderrTail.join(" | ")}` : "")
    );
  }

  post({
    type: "done",
    workerLabel,
    loadMs: tLoad1 - tLoad0,
    fetchMs: tFetch1 - tFetch0,
    writeMs: tWrite1 - tFetch1,
    totalMs: tGoEnd - tGoStart,
    results,
  });
}
