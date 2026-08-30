// 検討モード・ドリル等の対話的解析（単発局面）専用のWorker本体。
//
// analysis-worker.js（1局まるごとをジョブ配列で受け取り、末尾に"quit"を積んでcallMainを
// 1回だけ実行するバッチ専用プロトコル）とは別ファイルにする: 対話的解析はホスト側が
// このWorkerを「1回分析したら使い捨てて次を事前初期化しておく」方式で運用し、
// バッチ側のジョブ配列・2並列という前提を持ち込みたくないため。
//
// メッセージプロトコル（2段階）:
//   1. "prepare" → このWorkerを事前初期化して待機させる（wasmモジュール読込＋評価関数
//      ロード完了済みの状態にする。USIハンドシェイクも解析もまだ行わない）。完了したら
//      "prepared"を返す。ここでモジュール初期化・評価関数ロードの重いコストを先払い
//      しておくことで、実際のタップ（次のanalyzeメッセージ）はisready＋探索だけで
//      応答できる。
//   2. "analyze" → usiハンドシェイク〜1局面の解析〜quitまでを1回のModule.callMainで
//      実行する（analysis-worker.jsの1ジョブぶんと同じUSIコマンド列）。結果は"result"、
//      失敗時は"error"。
//      wasmインスタンスはModule.callMainを1回しか安全に実行できない（YaneuraOu本体の
//      main()が"quit"でプロセス終了処理を行うため）ため、analyzeは1Workerにつき1回のみ
//      有効で、その回で使い切る（analyzeOnce実行後はpreparedModuleがnullになり、
//      2回目のanalyzeを送っても結果は返らない）。

/**
 * @typedef {Object} PrepareMessage
 * @property {"prepare"} type
 * @property {string} variant "simd" または "nosimd"（kentoBridge.variantの検出結果）。
 * @property {string} assetDirUrl エンジン資産（yaneuraou-*.js/.wasm・nn.bin）ディレクトリの絶対URL。
 */

/**
 * @typedef {Object} AnalyzeMessage
 * @property {"analyze"} type
 * @property {string} baseSfenArg USIの `position` コマンドへ連結する文字列
 *   （`"startpos"` または `"sfen <SFEN文字列>"`）。
 * @property {string} movesJson baseSfenArgの局面からさらに進めるUSI手列のJSON配列文字列。
 */

/** @typedef {PrepareMessage | AnalyzeMessage} HostToWorkerMessage このWorkerが受け取るメッセージ。 */

/** @typedef {{cp: number}} ScoreCp */
/** @typedef {{mate: number}} ScoreMate */

/**
 * @typedef {Object} MultiPv2
 * @property {ScoreCp | ScoreMate | null} score
 * @property {string[]} pv
 */

/**
 * @typedef {Object} PositionResult
 * @property {number} ply 常に0（1局面のみを扱うため）。
 * @property {string | null} bestmove
 * @property {ScoreCp | ScoreMate | null} score MultiPV1のスコア。
 * @property {number | null} nodes MultiPV1の探索ノード数。
 * @property {string[]} pv MultiPV1の読み筋。
 * @property {MultiPv2 | null} multipv2
 */

/** @typedef {{type: "prepared"}} PreparedMessage */
/** @typedef {{type: "result", result: PositionResult}} ResultMessage */
/** @typedef {{type: "error", message: string}} ErrorMessage */

/**
 * @typedef {PreparedMessage | ResultMessage | ErrorMessage} WorkerToHostMessage
 * このWorkerが送るメッセージ（`post`関数経由）。
 */

importScripts("wasm-asset-cache.js");

/** @param {MessageEvent<HostToWorkerMessage>} ev */
self.onmessage = async (ev) => {
  const msg = ev.data;
  try {
    if (msg.type === "prepare") {
      await prepareEngine(msg.variant, msg.assetDirUrl);
      post({ type: "prepared" });
    } else if (msg.type === "analyze") {
      const result = await analyzeOnce(msg.baseSfenArg, msg.movesJson);
      post({ type: "result", result });
    }
  } catch (err) {
    post({ type: "error", message: String((err && err.stack) || err) });
  }
};

/** @param {WorkerToHostMessage} msg */
function post(msg) {
  self.postMessage(msg);
}

// analysis-worker.js と同じパース仕様（項目を揃えないと結果の形が一致しない）。
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

// 本番不変条件のため、これらの値を変更するUIは設けない（analysis-worker.jsと同一）。
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

let preparedModule = null;

/**
 * @param {PrepareMessage["variant"]} variant
 * @param {PrepareMessage["assetDirUrl"]} assetDirUrl
 */
async function prepareEngine(variant, assetDirUrl) {
  const jsUrl = new URL(`yaneuraou-${variant}.js`, assetDirUrl).href;
  const wasmUrl = new URL(`yaneuraou-${variant}.wasm`, assetDirUrl).href;
  const nnUrl = new URL(`nn.bin`, assetDirUrl).href;

  importScripts(jsUrl);
  const factory = self.createYaneuraOu;
  if (typeof factory !== "function") {
    throw new Error(`createYaneuraOu が見つかりません(${jsUrl})`);
  }

  const state = { latestMultipv: { 1: null, 2: null }, results: [] };
  const Module = await factory({
    locateFile: (path) => (path.endsWith(".wasm") ? wasmUrl : path),
    print: (line) => handleStdout(state, line),
    printErr: () => {},
    onAbort: (what) => {
      state.abortMessage = String(what);
    },
  });

  const nnBuf = await self.kentoWasmAssetCache.fetchCachedArrayBuffer(nnUrl);
  Module.FS.mkdir("/eval");
  Module.FS.writeFile("/eval/nn.bin", new Uint8Array(nnBuf));

  preparedModule = { Module, state };
}

function handleStdout(state, line) {
  if (line === "readyok") {
    state.latestMultipv = { 1: null, 2: null };
    return;
  }
  if (line.startsWith("info depth") && line.includes(" pv ")) {
    const parsed = parseInfo(line);
    const mpv = parsed.multipv === undefined ? 1 : parsed.multipv;
    if (mpv === 1 || mpv === 2) state.latestMultipv[mpv] = parsed;
    return;
  }
  if (line.startsWith("bestmove")) {
    const bestmove = line.split(/\s+/)[1] || null;
    const mpv1 = state.latestMultipv[1];
    const mpv2 = state.latestMultipv[2];
    state.results.push({
      ply: 0,
      bestmove,
      score: mpv1 ? mpv1.score || null : null,
      nodes: mpv1 ? mpv1.nodes ?? null : null,
      pv: mpv1 ? mpv1.pv || [] : [],
      multipv2: mpv2 ? { score: mpv2.score || null, pv: mpv2.pv || [] } : null,
    });
  }
}

/**
 * @param {AnalyzeMessage["baseSfenArg"]} baseSfenArg USIの position コマンドへそのまま連結される。
 * @param {AnalyzeMessage["movesJson"]} movesJson
 * @returns {Promise<PositionResult>}
 */
async function analyzeOnce(baseSfenArg, movesJson) {
  if (!preparedModule) {
    throw new Error("study-worker: prepare前にanalyzeが呼ばれました");
  }
  const { Module, state } = preparedModule;
  preparedModule = null; // callMainは1インスタンス1回のみ（ファイル冒頭のコメント参照）。

  const moves = JSON.parse(movesJson);
  const posArg = moves.length ? `${baseSfenArg} moves ${moves.join(" ")}` : baseSfenArg;

  const lines = ["usi"];
  for (const [name, value] of SETOPTIONS) {
    lines.push(`setoption name ${name} value ${value}`);
  }
  lines.push("isready");
  lines.push("usinewgame");
  lines.push(`position ${posArg}`);
  lines.push(`go nodes ${GO_NODES}`);
  lines.push("quit");

  const argv = [];
  for (const l of lines) {
    argv.push(l);
    argv.push(",");
  }

  Module.callMain(argv);

  if (state.abortMessage) {
    throw new Error(`Module.onAbort: ${state.abortMessage}`);
  }
  const result = state.results[0];
  if (!result) {
    throw new Error("study-worker: bestmoveが得られませんでした");
  }
  return result;
}
