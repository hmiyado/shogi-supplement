#!/usr/bin/env python3
"""Build a self-contained HTML catalog from the Roborazzi screenshots."""

from __future__ import annotations

import argparse
import base64
import html
import json
import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Screenshot:
    name: str
    kind_key: str
    kind_label: str
    category_key: str
    category: str
    screen: str
    condition: str
    width: int
    height: int
    test_source: str | None
    data_uri: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshots", type=Path, required=True)
    parser.add_argument("--tests", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def read_test_sources(tests_dir: Path) -> dict[str, str]:
    """Map a screenshot filename to the Kotlin class and test method that creates it."""
    sources: dict[str, str] = {}
    class_pattern = re.compile(r"\bclass\s+(\w+)\b")
    method_pattern = re.compile(r"\bfun\s+(\w+)\s*\(")
    path_pattern = re.compile(r'filePath\s*=\s*"src/test/snapshots/([^\"]+\.png)"')

    for source_path in sorted(tests_dir.rglob("*.kt")):
        source = source_path.read_text(encoding="utf-8")
        class_matches = list(class_pattern.finditer(source))
        for class_index, class_match in enumerate(class_matches):
            class_end = class_matches[class_index + 1].start() if class_index + 1 < len(class_matches) else len(source)
            class_source = source[class_match.end() : class_end]
            methods = list(method_pattern.finditer(class_source))
            for method_index, method_match in enumerate(methods):
                method_end = methods[method_index + 1].start() if method_index + 1 < len(methods) else len(class_source)
                method_source = class_source[method_match.end() : method_end]
                path_match = path_pattern.search(method_source)
                if path_match:
                    sources[path_match.group(1)] = f"{class_match.group(1)}.{method_match.group(1)}"
    return sources


def read_metadata(path: Path) -> dict[str, str]:
    metadata_path = path.with_suffix(".json")
    if not metadata_path.is_file():
        raise ValueError(f"メタデータがありません: {metadata_path}")
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    if not isinstance(metadata, dict):
        raise ValueError(f"メタデータはJSONオブジェクトで指定してください: {metadata_path}")
    required = {
        "kind", "kindLabel", "category", "categoryLabel",
        "screen", "screenLabel", "state", "stateLabel",
    }
    missing = sorted(required - metadata.keys())
    if missing:
        raise ValueError(f"メタデータの必須項目がありません: {metadata_path}: {', '.join(missing)}")
    if any(not isinstance(metadata[key], str) or not metadata[key] for key in required):
        raise ValueError(f"メタデータは空でない文字列で指定してください: {metadata_path}")
    return metadata


def image_dimensions(path: Path) -> tuple[int, int]:
    """Read PNG dimensions from IHDR without an image library."""
    raw = path.read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n" or raw[12:16] != b"IHDR":
        raise ValueError(f"PNGではありません: {path}")
    return int.from_bytes(raw[16:20], "big"), int.from_bytes(raw[20:24], "big")


def load_screenshots(snapshots_dir: Path, test_sources: dict[str, str]) -> list[Screenshot]:
    screenshots: list[Screenshot] = []
    png_paths = sorted(snapshots_dir.glob("*.png"))
    png_stems = {path.stem for path in png_paths}
    orphan_metadata = sorted(path.name for path in snapshots_dir.glob("*.json") if path.stem not in png_stems)
    if orphan_metadata:
        raise ValueError(f"対応するPNGがありません: {', '.join(orphan_metadata)}")
    for path in png_paths:
        metadata = read_metadata(path)
        encoded = base64.b64encode(path.read_bytes()).decode("ascii")
        width, height = image_dimensions(path)
        test_source = test_sources.get(path.name)
        screenshots.append(
            Screenshot(
                name=path.name,
                kind_key=metadata["kind"],
                kind_label=metadata["kindLabel"],
                category_key=metadata["category"],
                category=metadata["categoryLabel"],
                screen=metadata["screenLabel"],
                condition=metadata["stateLabel"],
                width=width,
                height=height,
                test_source=test_source,
                data_uri=f"data:image/png;base64,{encoded}",
            )
        )
    return screenshots


def esc(value: object) -> str:
    return html.escape(str(value), quote=True)


def render_card(item: Screenshot) -> str:
    test_source = item.test_source or "テストコードから直接参照されていません"
    search = " ".join((item.category, item.screen, item.condition, item.name, test_source))
    return f'''\
      <article class="card" data-filter-key="{esc(item.kind_key)}:{esc(item.category_key)}" data-search="{esc(search)}">
        <div class="preview"><img loading="lazy" src="{item.data_uri}" alt="{esc(item.category)} / {esc(item.screen)} / {esc(item.condition)}"></div>
        <div class="card-body">
          <div class="eyebrow"><span>{esc(item.category)}</span><span>{item.width} × {item.height}</span></div>
          <h2>{esc(item.screen)}</h2>
          <p class="condition">{esc(item.condition)}</p>
          <p class="source"><code>{esc(item.name)}</code><br><span>{esc(test_source)}</span></p>
        </div>
      </article>'''


def render_sections(screenshots: list[Screenshot]) -> str:
    grouped: dict[tuple[str, str], list[Screenshot]] = {}
    kind_labels: dict[str, str] = {}
    category_labels: dict[str, str] = {}
    for item in screenshots:
        grouped.setdefault((item.kind_key, item.category_key), []).append(item)
        kind_labels[item.kind_key] = item.kind_label
        category_labels[item.category_key] = item.category

    kind_sections: list[str] = []
    kind_order = {"component": 0, "screen": 1}
    for kind_key in sorted(kind_labels, key=lambda key: kind_order.get(key, 99)):
        category_sections: list[str] = []
        category_keys = sorted(
            (category_key for current_kind, category_key in grouped if current_kind == kind_key),
            key=lambda key: category_labels[key],
        )
        for category_key in category_keys:
            items = grouped[(kind_key, category_key)]
            cards = "\n".join(render_card(item) for item in items)
            category_sections.append(
                f'''<section class="catalog-section" data-category="{esc(category_key)}" aria-labelledby="section-{esc(kind_key)}-{esc(category_key)}">
      <div class="section-heading"><h3 id="section-{esc(kind_key)}-{esc(category_key)}">{esc(category_labels[category_key])}</h3><span>{len(items)} screens</span></div>
      <div class="grid">{cards}</div>
    </section>'''
            )
        category_markup = "\n".join(category_sections)
        kind_sections.append(
            f'''<section class="kind-section" aria-labelledby="kind-{esc(kind_key)}">
    <h2 class="kind-heading" id="kind-{esc(kind_key)}">{esc(kind_labels[kind_key])}</h2>
    {category_markup}
  </section>'''
        )
    return "\n".join(kind_sections)


def render_filter_groups(screenshots: list[Screenshot]) -> str:
    grouped: dict[str, dict[str, str]] = {}
    for item in screenshots:
        grouped.setdefault(item.kind_key, {})[item.category_key] = item.category

    kind_order = {"component": 0, "screen": 1}
    groups: list[str] = []
    for kind_key in sorted(grouped, key=lambda key: kind_order.get(key, 99)):
        buttons = "".join(
            f'<button class="filter" type="button" data-filter="{esc(kind_key)}:{esc(category_key)}">{esc(category_label)}</button>'
            for category_key, category_label in sorted(grouped[kind_key].items(), key=lambda pair: pair[1])
        )
        groups.append(
            f'''<fieldset class="filter-set"><legend>{esc("コンポーネント" if kind_key == "component" else "画面（Screen）")}</legend>{buttons}</fieldset>'''
        )
    return "".join(groups)


def render(screenshots: list[Screenshot]) -> str:
    screen_count = len({(item.category_key, item.screen) for item in screenshots})
    return f'''<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>将棋サプリ UIカタログ</title>
  <style>
    @font-face {{ font-family: "Shippori Mincho"; src: url("../../../../docs/assets/fonts/shippori_mincho_bold.ttf") format("truetype"); font-weight: 700; }}
    @font-face {{ font-family: "IBM Plex Sans JP"; src: url("../../../../docs/assets/fonts/ibm_plex_sans_jp_regular.ttf") format("truetype"); font-weight: 400; }}
    @font-face {{ font-family: "IBM Plex Sans JP"; src: url("../../../../docs/assets/fonts/ibm_plex_sans_jp_bold.ttf") format("truetype"); font-weight: 700; }}
    :root {{ --bg:#F7F3EA; --surface:#FFFDF7; --ink:#211E1A; --ink2:#5C564C; --ink3:#8C857B; --line:#DDD5C4; --primary:#3A4B7C; --sans:"IBM Plex Sans JP",sans-serif; --mincho:"Shippori Mincho",serif; }}
    * {{ box-sizing: border-box; }}
    body {{ margin:0; background:var(--bg); color:var(--ink); font-family:var(--sans); }}
    .shell {{ max-width:1440px; margin:0 auto; padding:48px 32px 72px; }}
    header {{ display:flex; justify-content:space-between; gap:32px; align-items:end; border-bottom:1px solid var(--line); padding-bottom:28px; }}
    h1 {{ margin:0; font-family:var(--mincho); font-size:clamp(34px,5vw,58px); line-height:1.2; }}
    .kicker {{ margin:0 0 10px; color:var(--primary); font-size:13px; font-weight:700; letter-spacing:.14em; }}
    .intro {{ max-width:720px; color:var(--ink2); line-height:1.7; }}
    .stats {{ display:flex; gap:24px; flex:none; }}
    .stat strong {{ display:block; color:var(--primary); font-size:30px; line-height:1; }}
    .stat span {{ color:var(--ink3); font-size:13px; }}
    .toolbar {{ display:flex; flex-wrap:wrap; gap:10px; align-items:center; padding:24px 0; }}
    .search {{ width:min(360px,100%); margin-right:auto; padding:11px 14px; border:1px solid var(--line); border-radius:8px; background:var(--surface); color:var(--ink); font:inherit; }}
    .filter-groups {{ display:flex; flex-wrap:wrap; gap:10px; }}
    .filter-set {{ display:flex; flex-wrap:wrap; gap:7px; margin:0; border:0; padding:0; }}
    .filter-set legend {{ width:100%; margin:0 0 3px; color:var(--ink3); font-size:11px; letter-spacing:.08em; }}
    button {{ border:1px solid var(--line); border-radius:999px; padding:9px 14px; background:var(--surface); color:var(--ink2); font:inherit; cursor:pointer; }}
    button.active {{ border-color:var(--primary); background:var(--primary); color:#fff; }}
    .kind-section {{ margin-top:38px; }}
    .kind-section[hidden] {{ display:none; }}
    .kind-heading {{ margin:0 0 18px; border-bottom:2px solid var(--primary); padding-bottom:10px; font-family:var(--mincho); font-size:34px; line-height:1.3; }}
    .catalog-section {{ margin-top:26px; }}
    .catalog-section[hidden] {{ display:none; }}
    .section-heading {{ display:flex; align-items:baseline; gap:14px; margin-bottom:14px; border-bottom:1px solid var(--line); padding-bottom:10px; }}
    .section-heading h3 {{ margin:0; font-family:var(--mincho); font-size:28px; line-height:1.3; }}
    .section-heading span {{ color:var(--ink3); font-size:13px; }}
    .grid {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:22px; }}
    .card {{ overflow:hidden; background:var(--surface); border:1px solid var(--line); border-radius:12px; box-shadow:0 5px 18px rgba(33,30,26,.06); }}
    .card[hidden] {{ display:none; }}
    .preview {{ display:flex; min-height:220px; max-height:430px; padding:16px; align-items:center; justify-content:center; background:#ebe5d8; }}
    .preview img {{ display:block; max-width:100%; max-height:398px; width:auto; height:auto; object-fit:contain; box-shadow:0 2px 10px rgba(33,30,26,.12); }}
    .card-body {{ padding:17px 18px 19px; }}
    .eyebrow {{ display:flex; justify-content:space-between; gap:8px; color:var(--ink3); font-size:12px; letter-spacing:.04em; }}
    h2 {{ margin:9px 0 5px; font-family:var(--mincho); font-size:22px; line-height:1.35; }}
    .condition {{ margin:0; color:var(--primary); font-size:14px; font-weight:700; }}
    .source {{ margin:15px 0 0; color:var(--ink3); font-size:11px; line-height:1.55; word-break:break-word; }}
    code {{ color:var(--ink2); font-family:ui-monospace,SFMono-Regular,Menlo,monospace; }}
    .empty {{ display:none; padding:48px; color:var(--ink3); text-align:center; }}
    .empty.visible {{ display:block; }}
    @media (max-width:700px) {{ .shell {{ padding:28px 16px 52px; }} header {{ display:block; }} .stats {{ margin-top:24px; }} .toolbar {{ align-items:stretch; }} .search {{ width:100%; margin-right:0; }} .kind-heading {{ font-size:30px; }} .section-heading h3 {{ font-size:24px; }} }}
  </style>
</head>
<body>
  <main class="shell">
    <header>
      <div>
        <p class="kicker">SHOGI SUPPLEMENT / DEVELOPMENT TOOL</p>
        <h1>UIカタログ</h1>
        <p class="intro">Roborazziのスクリーンショットを、画面・表示条件・テスト元と一緒に確認できます。画像はこのページに埋め込まれているため、生成後は単体で開けます。</p>
      </div>
      <div class="stats"><div class="stat"><strong>{len(screenshots)}</strong><span>screenshots</span></div><div class="stat"><strong>{screen_count}</strong><span>screens</span></div></div>
    </header>
    <div class="toolbar">
      <input class="search" type="search" placeholder="画面名・条件・ファイル名を検索" aria-label="UIカタログを検索">
      <button class="filter active" type="button" data-filter="all">すべて</button>
      <div class="filter-groups" aria-label="カテゴリで絞り込み">{render_filter_groups(screenshots)}</div>
    </div>
    {render_sections(screenshots)}
    <p class="empty">条件に一致するスクリーンショットはありません。</p>
  </main>
  <script>
    const kindSections = [...document.querySelectorAll('.kind-section')];
    const sections = [...document.querySelectorAll('.catalog-section')];
    const cards = [...document.querySelectorAll('.card')];
    const filters = [...document.querySelectorAll('.filter')];
    const search = document.querySelector('.search');
    const empty = document.querySelector('.empty');
    let activeFilter = 'all';
    function update() {{
      const query = search.value.trim().toLowerCase();
      let visible = 0;
      cards.forEach(card => {{
        const matchesFilter = activeFilter === 'all' || card.dataset.filterKey === activeFilter;
        const matchesSearch = !query || card.dataset.search.toLowerCase().includes(query);
        card.hidden = !(matchesFilter && matchesSearch);
        if (!card.hidden) visible += 1;
      }});
      sections.forEach(section => {{
        section.hidden = !section.querySelector('.card:not([hidden])');
      }});
      kindSections.forEach(section => {{
        section.hidden = !section.querySelector('.catalog-section:not([hidden])');
      }});
      empty.classList.toggle('visible', visible === 0);
    }}
    filters.forEach(button => button.addEventListener('click', () => {{
      activeFilter = button.dataset.filter;
      filters.forEach(item => item.classList.toggle('active', item === button));
      update();
    }}));
    search.addEventListener('input', update);
    update();
  </script>
</body>
</html>
'''


def main() -> None:
    args = parse_args()
    screenshots = load_screenshots(args.snapshots, read_test_sources(args.tests))
    if not screenshots:
        raise SystemExit(f"スクリーンショットがありません: {args.snapshots}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render(screenshots), encoding="utf-8")
    print(f"UI catalog: {len(screenshots)} screenshots -> {args.output}")


if __name__ == "__main__":
    main()
