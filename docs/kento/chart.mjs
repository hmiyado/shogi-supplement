// 手数×評価値の折れ線グラフを素のSVGで描画する(外部ライブラリ不使用)。
// 先手視点・±2000cpクランプ済みの値を受け取るだけで、クランプ自体は呼び出し側で行う
// (このモジュールは描画だけに専念する)。

const SVG_NS = "http://www.w3.org/2000/svg";
const CAP = 2000;

function el(name, attrs = {}) {
  const e = document.createElementNS(SVG_NS, name);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
  return e;
}

/**
 * @param {SVGSVGElement} svg 描画先(空にしてから描く)
 * @param {Array<{ply:number, value:number|null, isLoss:boolean}>} points
 *   value は先手視点で±2000にクランプ済み。nullは未解析(進捗中)を表す。
 */
export function renderEvalChart(svg, points) {
  while (svg.firstChild) svg.removeChild(svg.firstChild);
  if (!points.length) return;

  const padLeft = 44;
  const padRight = 12;
  const padTop = 10;
  const padBottom = 24;
  const width = 900;
  const height = 260;
  const plotW = width - padLeft - padRight;
  const plotH = height - padTop - padBottom;

  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svg.setAttribute("preserveAspectRatio", "xMinYMid meet");
  svg.setAttribute("role", "img");
  svg.setAttribute("aria-label", "手数ごとの評価値推移(先手視点)");

  const maxPly = points.length - 1;
  const xOf = (ply) => padLeft + (maxPly <= 0 ? 0 : (ply / maxPly) * plotW);
  const yOf = (value) => padTop + ((CAP - value) / (2 * CAP)) * plotH;

  const gridValues = [-2000, -1000, 0, 1000, 2000];
  for (const v of gridValues) {
    const y = yOf(v);
    const line = el("line", {
      x1: padLeft,
      x2: width - padRight,
      y1: y,
      y2: y,
      class: v === 0 ? "chart-zero-line" : "chart-grid-line",
    });
    svg.appendChild(line);
    const label = el("text", { x: padLeft - 6, y: y + 3, class: "chart-axis-label", "text-anchor": "end" });
    label.textContent = v === 0 ? "0" : v > 0 ? `+${v}` : String(v);
    svg.appendChild(label);
  }

  const xTicks = maxPly <= 0 ? [0] : [0, Math.round(maxPly / 2), maxPly];
  for (const ply of [...new Set(xTicks)]) {
    const label = el("text", {
      x: xOf(ply),
      y: height - 6,
      class: "chart-axis-label",
      "text-anchor": ply === 0 ? "start" : ply === maxPly ? "end" : "middle",
    });
    label.textContent = `${ply}手`;
    svg.appendChild(label);
  }

  // --- 折れ線(欠測はセグメントを分けて描く) ---
  let segment = [];
  const segments = [];
  for (const p of points) {
    if (p.value === null || p.value === undefined) {
      if (segment.length) segments.push(segment);
      segment = [];
      continue;
    }
    segment.push(p);
  }
  if (segment.length) segments.push(segment);

  for (const seg of segments) {
    const d = seg.map((p, i) => `${i === 0 ? "M" : "L"}${xOf(p.ply).toFixed(2)},${yOf(p.value).toFixed(2)}`).join(" ");
    svg.appendChild(el("path", { d, class: "chart-line" }));
  }

  for (const p of points) {
    if (p.value === null || p.value === undefined) continue;
    const r = p.isLoss ? 3.6 : 2.2;
    svg.appendChild(
      el("circle", {
        cx: xOf(p.ply).toFixed(2),
        cy: yOf(p.value).toFixed(2),
        r,
        class: p.isLoss ? "chart-point-loss" : "chart-point",
      })
    );
  }
}
