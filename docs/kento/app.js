// 相対URLの扱い: fetch()・new Worker()に渡す相対URLは、ドキュメントの場所ではなく
// このモジュール自身の場所を基準に解決したいので、すべてnew URL(path, import.meta.url)
// を経由する(document.baseURIに対する暗黙解決には依存しない)。ドキュメントとこの
// モジュールは異なるディレクトリに配置されているため、この区別を怠ると資産パスがズレる。

import { renderEvalChart } from "./chart.mjs";

// SFEN＋USI指し手列の直接入力をパースする。受け付ける形式は「position」コマンドの
// 引数そのもの:
//   - "startpos moves 7g7f 3c3d ..."（movesは省略可）
//   - "sfen <board> <turn> <hands> <move#> moves 7g7f ..."（movesは省略可）
// バリデーションは最小限(先頭トークンの判定のみ)。SFEN文字列自体の正当性は
// エンジン側のisreadyやgoが失敗すれば分かるため、ここでは再実装しない。
// Why not KIF入力: KIFパーサはアプリ本体(Kotlin)の実装の再発明になるため、
// このページではSFEN+USI直接入力のみを受け付ける。KIF対応はアプリ側の
// Kotlin実装をKotlin/JS化して共有する形で別途行う予定。
function parseSfenUsiInput(text) {
  let trimmed = text.trim();
  if (!trimmed) {
    return { ok: false, error: { message: "入力が空です" } };
  }
  if (trimmed.startsWith("position ")) {
    trimmed = trimmed.slice("position ".length).trim();
  }

  const movesIdx = trimmed.indexOf(" moves ");
  let basePart;
  let movesPart;
  if (movesIdx >= 0) {
    basePart = trimmed.slice(0, movesIdx).trim();
    movesPart = trimmed.slice(movesIdx + " moves ".length).trim();
  } else if (trimmed === "startpos" || trimmed.startsWith("sfen ")) {
    basePart = trimmed;
    movesPart = "";
  } else {
    return {
      ok: false,
      error: { message: '入力は "startpos" または "sfen <盤面> <手番> <持駒> <手数>" で始めてください' },
    };
  }

  if (basePart !== "startpos" && !basePart.startsWith("sfen ")) {
    return {
      ok: false,
      error: { message: '入力は "startpos" または "sfen <盤面> <手番> <持駒> <手数>" で始めてください' },
    };
  }

  const moves = movesPart.length ? movesPart.split(/\s+/) : [];
  // 指し手トークンの形式チェックのみ(盤面上の合法性は見ない)。実際の妥当性は
  // エンジンのgo応答が空/エラーになることで気づける。
  for (const m of moves) {
    if (!/^([1-9][a-i][1-9][a-i]\+?|[A-Z]\*[1-9][a-i])$/.test(m)) {
      return { ok: false, error: { message: `指し手として解釈できないトークンです: "${m}"` } };
    }
  }

  return { ok: true, baseSfenArg: basePart, moves };
}

// 解析条件は固定でUIは設けない。Worker側の設定値と重複するが、これは表示専用の定数であり、Workerには渡さない。
const FIXED_CONDITIONS_LABEL =
  "go nodes 400000 / Threads=1 / USI_Hash=128 / MultiPV=2 / FV_SCALE=20";
const LOSS_THRESHOLD_CP = 500;
const CAP = 2000;

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

const sfenInput = document.getElementById("sfenInput");
const startBtn = document.getElementById("startBtn");
const cancelBtn = document.getElementById("cancelBtn");
const inputError = document.getElementById("inputError");
const statusText = document.getElementById("statusText");
const progressFill = document.getElementById("progressFill");
const resultsSection = document.getElementById("resultsSection");
const chartSvg = document.getElementById("chartSvg");
const movesTableBody = document.getElementById("movesTableBody");
const assetBanner = document.getElementById("assetBanner");
const inputCard = document.getElementById("inputCard");

document.getElementById("conditionsLabel").textContent = FIXED_CONDITIONS_LABEL;

function setInputError(message) {
  inputError.textContent = message || "";
  inputError.hidden = !message;
}

// 設定箇所はASSET_BASE_URLの1行だけ。既定は相対パス(GH Pages配信)。将来この資産を
// 外部ホストへ移す場合は、ここを絶対URL(例: "https://cdn.example.com/kento-assets")へ
// 差し替えるだけでよい(バージョン付きパスの組み立て方自体は変わらない)。
// document.baseURI基準で解決する(ドキュメントの実際の配信URLからの相対パスとして
// 振る舞わせるため。import.meta.url基準にすると"./kento-assets"の意味がこのモジュール
// 自身の場所基準にズレてしまう)。
const ASSET_BASE_URL = "./kento-assets";

// バージョンはビルドのたびに変わりうるため、ここにハードコードせず、ASSET_BASE_URL直下の
// VERSIONファイルを実行時に読んで決める(ビルドプロセスがバージョン情報をそこへ複製する)。
// これにより、バージョンが更新されてもこのファイルを手で同期する必要がない。
let assetDirUrlPromise = null;
function resolveAssetDirUrl() {
  if (!assetDirUrlPromise) {
    assetDirUrlPromise = (async () => {
      const baseUrl = new URL(`${ASSET_BASE_URL}/`, document.baseURI);
      const versionUrl = new URL("VERSION", baseUrl);
      const resp = await fetch(versionUrl);
      if (!resp.ok) {
        throw new Error(`エンジンバージョン情報を取得できません: HTTP ${resp.status} (${versionUrl})`);
      }
      const version = (await resp.text()).trim();
      if (!version) {
        throw new Error(`エンジンバージョン情報が空です (${versionUrl})`);
      }
      return new URL(`${version}/`, baseUrl).href;
    })();
  }
  return assetDirUrlPromise;
}

// 解析エンジン資産はビルド成果物(61MB級)のためリポジトリに含めず、ローカルコピーする
// 運用(.gitignore対象)。コピーされていない状態でこのページを開いた利用者に、
// 素の404ではなく分かりやすい案内を出す。
async function checkAssetsAvailable() {
  try {
    const assetDirUrl = await resolveAssetDirUrl();
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

(async () => {
  const available = await checkAssetsAvailable();
  if (!available) {
    assetBanner.textContent = "この機能は準備中です。しばらくしてからもう一度お試しください。";
    assetBanner.hidden = false;
    inputCard.hidden = true;
  }
})();

let activeWorkers = [];
let cancelResolve = null;

function setRunningUI(running) {
  startBtn.disabled = running;
  cancelBtn.disabled = !running;
  sfenInput.disabled = running;
}

function setStatus(text) {
  statusText.textContent = text;
}

function setProgress(done, total) {
  const pct = total > 0 ? Math.round((done / total) * 100) : 0;
  progressFill.style.width = `${pct}%`;
}

function fmtElapsed(startMs) {
  return ((performance.now() - startMs) / 1000).toFixed(1);
}

function fmtScore(score) {
  if (!score) return "-";
  if ("cp" in score) {
    const v = score.cp;
    return v > 0 ? `+${v}` : String(v);
  }
  if ("mate" in score) {
    const v = score.mate;
    return v >= 0 ? `+詰${v || ""}` : `−詰${Math.abs(v)}`;
  }
  return "-";
}

// スコアを先手視点のcpへ変換する(符号反転)。詰みは±CAPへ張り付け。
// plyは「この局面に到達するまでに指された手数」(0=開始局面)。
// ply が奇数 → 直前に先手が指した → 手番は後手 → エンジンのスコアは後手視点なので反転。
// ply が偶数(0含む) → 手番は先手 → エンジンのスコアはそのまま先手視点。
function toSenteCp(score, ply) {
  if (!score) return null;
  const flip = ply % 2 === 1;
  if ("cp" in score) {
    const raw = flip ? -score.cp : score.cp;
    return Math.max(-CAP, Math.min(CAP, raw));
  }
  if ("mate" in score) {
    const raw = flip ? -score.mate : score.mate;
    // mate:0(手番側が詰まされている)は非負として扱われる仕様上の曖昧さがあるため、
    // ここでは「詰みは常にCAPへ張り付け、符号はraw(反転後)がゼロ以上なら+、負なら-」
    // という単純な規約にする(既知の限界として報告に明記する)。
    return raw >= 0 ? CAP : -CAP;
  }
  return null;
}

function displayMoveText(index, source) {
  const mark = index % 2 === 1 ? "▲" : "△";
  return `${mark}${source}`;
}

async function startAnalysis() {
  setInputError("");

  const parsed = parseSfenUsiInput(sfenInput.value);
  if (!parsed.ok) {
    setInputError(parsed.error.message);
    return;
  }

  const totalMoves = parsed.moves.length;
  if (totalMoves === 0) {
    setInputError("指し手が0件です");
    return;
  }

  // ply=0は「1手目の損失」を計算する基準として必要(手ごとの一覧には出さないが
  // グラフの起点としては表示する)。
  const jobs = [];
  for (let ply = 0; ply <= totalMoves; ply++) {
    jobs.push({ ply, moves: parsed.moves.slice(0, ply) });
  }
  const total = jobs.length;

  const half = Math.ceil(jobs.length / 2);
  const group1 = jobs.slice(0, half);
  const group2 = jobs.slice(half);

  // 結果テーブル・グラフの骨組みを先に描く(no-jitter原則: 行の出現/消失ではなく、既存スロットの中身の排他的な入れ替えで進捗を表す)
  movesTableBody.innerHTML = "";
  const rowByPly = new Map();
  for (let i = 1; i <= totalMoves; i++) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="col-ply">${i}</td>
      <td class="col-move">…</td>
      <td class="col-eval">…</td>
      <td class="col-best">…</td>
      <td class="col-pv">…</td>
    `;
    movesTableBody.appendChild(tr);
    rowByPly.set(i, tr);
  }
  resultsSection.hidden = false;

  const evalByPly = new Map();
  const resultByPly = new Map();

  function redrawChart() {
    const points = [];
    for (let ply = 0; ply <= totalMoves; ply++) {
      const r = resultByPly.get(ply);
      const value = r ? toSenteCp(r.score, ply) : null;
      const isLoss = ply >= 1 && isLossMove(ply);
      points.push({ ply, value, isLoss });
    }
    renderEvalChart(chartSvg, points);
  }

  function isLossMove(ply) {
    const before = evalByPly.get(ply - 1);
    const after = evalByPly.get(ply);
    if (before === null || before === undefined || after === null || after === undefined) return false;
    const moverIsSente = ply % 2 === 1;
    const loss = moverIsSente ? before - after : after - before;
    return loss >= LOSS_THRESHOLD_CP;
  }

  function updateRow(ply) {
    if (ply < 1 || ply > totalMoves) return; // ply=0は表には出さない(グラフの起点のみ)
    const tr = rowByPly.get(ply);
    if (!tr) return;
    const r = resultByPly.get(ply);
    if (!r) return;
    const moveCell = tr.querySelector(".col-move");
    moveCell.textContent = displayMoveText(ply, parsed.moves[ply - 1]);
    const evalCell = tr.querySelector(".col-eval");
    evalCell.textContent = r.score ? fmtScore(senteCpForDisplay(r.score, ply)) : "-";
    const bestCell = tr.querySelector(".col-best");
    bestCell.textContent = r.bestmove || "-";
    const pvCell = tr.querySelector(".col-pv");
    pvCell.textContent = r.pv && r.pv.length ? r.pv.join(" ") : "-";

    tr.classList.toggle("row-loss", isLossMove(ply));
  }

  // 表には「先手視点の符号付きcp/詰み」を出す(生のエンジン視点ではなく、グラフと同じ先手視点に統一するため)。
  function senteCpForDisplay(score, ply) {
    if (!score) return null;
    const flip = ply % 2 === 1;
    if ("cp" in score) return { cp: flip ? -score.cp : score.cp };
    if ("mate" in score) return { mate: flip ? -score.mate : score.mate };
    return null;
  }

  let doneCount = 0;
  const startTime = performance.now();
  const tickTimer = setInterval(() => {
    setStatus(`解析中... ${doneCount}/${total}局面・経過${fmtElapsed(startTime)}秒`);
  }, 200);

  function onPosition(result) {
    doneCount++;
    resultByPly.set(result.ply, result);
    evalByPly.set(result.ply, toSenteCp(result.score, result.ply));
    updateRow(result.ply);
    // 隣接する手の悪手判定は「前後どちらかが今更新された」場合に変わりうるので
    // 両方再評価しておく(表示のちらつきは起きない: クラスの付け外しのみ)。
    updateRow(result.ply + 1);
    setProgress(doneCount, total);
    redrawChart();
  }

  activeWorkers = [];
  setRunningUI(true);
  setStatus(`解析中... 0/${total}局面・経過0.0秒`);
  setProgress(0, total);

  const cancelPromise = new Promise((resolve) => {
    cancelResolve = resolve;
  });

  // 既にページ読み込み時に解決済み(キャッシュされたPromiseを再利用するだけで追加のfetchは発生しない)。
  const assetDirUrl = await resolveAssetDirUrl();

  const runPromise = Promise.allSettled([
    runWorker("W1", VARIANT, parsed.baseSfenArg, group1, onPosition, assetDirUrl),
    runWorker("W2", VARIANT, parsed.baseSfenArg, group2, onPosition, assetDirUrl),
  ]).then((settled) => ({ cancelled: false, settled }));

  const outcome = await Promise.race([runPromise, cancelPromise]);
  clearInterval(tickTimer);
  activeWorkers = [];
  setRunningUI(false);

  if (outcome.cancelled) {
    setStatus(`キャンセルしました（${doneCount}/${total}局面まで解析済み・経過${fmtElapsed(startTime)}秒）`);
    return;
  }

  const failed = outcome.settled.filter((s) => s.status === "rejected");
  if (failed.length) {
    setStatus("エラーが発生しました。ページを再読み込みしてからもう一度お試しください。");
    return;
  }
  setStatus(`解析完了: ${total}局面・経過${fmtElapsed(startTime)}秒`);
}

function runWorker(workerLabel, variant, baseSfenArg, jobs, onPosition, assetDirUrl) {
  return new Promise((resolve, reject) => {
    if (!jobs.length) {
      resolve({ workerLabel, results: [] });
      return;
    }
    const worker = new Worker(new URL("analysis-worker.js", import.meta.url));
    activeWorkers.push(worker);
    worker.onmessage = (ev) => {
      const msg = ev.data;
      if (msg.type === "position") {
        onPosition(msg.result);
      } else if (msg.type === "done") {
        resolve(msg);
      } else if (msg.type === "error") {
        reject(new Error(`[${workerLabel}] ${msg.message}`));
      }
      // "stage"メッセージ(ロード進捗)はこのページでは表示に使わない(進捗表示は
      // 局面単位のprogressで十分なため)。
    };
    worker.onerror = (err) => {
      reject(new Error(`[${workerLabel}] Workerエラー: ${err.message || err}`));
    };
    // 資産の場所(assetDirUrl)は解決済みの絶対URLをそのまま渡す。Worker側は自分で相対パスを
    // 組み立てない(資産参照の設定箇所を一箇所に集約するため)。
    worker.postMessage({ workerLabel, variant, baseSfenArg, jobs, assetDirUrl });
  });
}

function cancelAnalysis() {
  for (const w of activeWorkers) w.terminate();
  activeWorkers = [];
  if (cancelResolve) cancelResolve({ cancelled: true });
}

startBtn.addEventListener("click", () => {
  startAnalysis().catch((err) => {
    setStatus(`予期しないエラー: ${err.message}`);
    setRunningUI(false);
  });
});
cancelBtn.addEventListener("click", cancelAnalysis);
