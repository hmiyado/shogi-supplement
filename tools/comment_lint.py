#!/usr/bin/env python3
"""Kotlinソースのコメント量と匂いワードを検査する。

規約の正は docs/comment-policy.md。閾値は同ドキュメントの根拠（実測p90）に対応する。

  python3 tools/comment_lint.py --staged     # ステージ済みKotlinのみ（pre-commit用）
  python3 tools/comment_lint.py --diff main  # mainとの差分ファイルのみ（CI用）
  python3 tools/comment_lint.py app          # 全走査（ゲートせず統計を出す）
  python3 tools/comment_lint.py --stats app  # 分布のみ（閾値見直し用）
"""
import argparse
import re
import statistics
import subprocess
import sys
from pathlib import Path

# 閾値（docs/comment-policy.md と対応。変更時は同ドキュメントの表も直すこと）
FILE_MIN_LINES = 60
# 総量規制。Why not コメント行/総行の比率: 宣言1つに1行ずつ説明を付ける健全な書き方
# （文言定数の一覧など）でも比率が上がり、良い書き方を罰してしまう。
COMMENT_PER_DECL_MAX = 2.5
INLINE_RATIO_MAX = 0.08
BLOCK_MAX = {"class": 10, "decl": 5, "inline": 3}

# 規約違反を機械的に見つけられる語（docs/comment-policy.md「禁止されるコメント」に対応）
SMELL_WORDS = [
    "に使う", "が使う", "呼び出し側", "利用側", "画面側", "から呼ばれる",
    "旧実装", "以前は", "指摘", "予定", "ベータでは", "承認済み", "検証済み",
    "モック通り", "両方直す", "揃えること", "同じ値にする", "同期させ",
]

ANNOTATION_ONLY_RE = re.compile(r"^\s*@\w+(?:\([^)]*\))?\s*$")

DECL_RE = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*"
    r"(?:public |private |internal |protected |override |open |abstract |final |suspend |"
    r"inline |operator |infix |external |expect |actual |const |lateinit |data |sealed |"
    r"enum |annotation |value |companion |tailrec |vararg )*"
    r"(?P<kw>class|interface|object|fun|val|var)\b"
)


def classify_lines(lines):
    """各行を code / comment / blank に分類する。文字列リテラル中の // は無視する。"""
    kinds = []
    in_block = False
    for raw in lines:
        line = raw.rstrip("\n")
        stripped = line.strip()
        if in_block:
            kinds.append("comment")
            if "*/" in line:
                in_block = False
            continue
        if not stripped:
            kinds.append("blank")
            continue
        cleaned = re.sub(r'"""(?:.|\n)*?"""', '""', line)
        cleaned = re.sub(r'"(?:\\.|[^"\\])*"', '""', cleaned)
        cleaned = re.sub(r"'(?:\\.|[^'\\])*'", "''", cleaned)
        line_idx = cleaned.find("//")
        block_idx = cleaned.find("/*")
        idx = min([i for i in (line_idx, block_idx) if i >= 0], default=-1)
        if idx < 0:
            kinds.append("code")
            continue
        if block_idx >= 0 and (line_idx < 0 or block_idx < line_idx) and "*/" not in cleaned[block_idx:]:
            in_block = True
        kinds.append("comment" if not cleaned[:idx].strip() else "code")
    return kinds


def count_declarations(lines, kinds):
    """名前を持つ宣言（トップレベル・クラスメンバ）の数。

    関数本体の中の変数は数えない。作業用の変数まで数えると、処理が長い関数を持つ
    ファイルほど分母が膨らみ、総量規制が緩くなってしまうため。
    """
    count = 0
    depth = 0
    fun_body_depth = None
    for i, line in enumerate(lines):
        if kinds[i] != "code":
            continue
        stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', line)
        matched = DECL_RE.match(line) if fun_body_depth is None else None
        if matched:
            count += 1
        opened = stripped.count("{") - stripped.count("}")
        if matched and matched.group("kw") == "fun" and opened > 0:
            fun_body_depth = depth
        depth += opened
        if fun_body_depth is not None and depth <= fun_body_depth:
            fun_body_depth = None
    return count


def analyze(path):
    lines = Path(path).read_text(encoding="utf-8", errors="replace").splitlines(keepends=True)
    kinds = classify_lines(lines)
    blocks = []
    i = 0
    while i < len(lines):
        if kinds[i] != "comment":
            i += 1
            continue
        start = i
        while i < len(lines) and kinds[i] == "comment":
            i += 1
        after = i
        # 空行と単独行のアノテーション（@Composable等）を読み飛ばして宣言本体を探す
        while after < len(lines) and (kinds[after] == "blank" or ANNOTATION_ONLY_RE.match(lines[after])):
            after += 1
        target = "inline"
        if after < len(lines):
            matched = DECL_RE.match(lines[after])
            if matched:
                target = "class" if matched.group("kw") in ("class", "interface", "object") else "decl"
        blocks.append({"line": start + 1, "length": i - start, "target": target,
                       "text": "".join(lines[start:i])})
    declarations = count_declarations(lines, kinds)
    return {"path": str(path), "total": len(lines), "comment": kinds.count("comment"),
            "code": kinds.count("code"), "declarations": declarations, "blocks": blocks}


def is_test(path):
    name = Path(path).name
    return name.endswith("Test.kt") or "/test/" in str(path) or "Test/" in str(path) or "/fakes/" in str(path)


def check(path):
    """1ファイルを検査して違反メッセージのリストを返す。"""
    result = analyze(path)
    problems = []
    if result["total"] >= FILE_MIN_LINES:
        if result["declarations"]:
            per_decl = result["comment"] / result["declarations"]
            if per_decl > COMMENT_PER_DECL_MAX:
                problems.append(
                    f"{path}: 宣言1つあたりのコメントが {per_decl:.1f}行"
                    f"（{result['comment']}行 / 宣言{result['declarations']}個・上限 {COMMENT_PER_DECL_MAX}行）"
                )
        inline_lines = sum(b["length"] for b in result["blocks"] if b["target"] == "inline")
        if result["code"]:
            inline_ratio = inline_lines / result["code"]
            if inline_ratio > INLINE_RATIO_MAX:
                problems.append(
                    f"{path}: インラインコメントが {inline_ratio:.2f}"
                    f"（{inline_lines}行 / コード{result['code']}行・上限 {INLINE_RATIO_MAX}）"
                )
    for block in result["blocks"]:
        limit = BLOCK_MAX[block["target"]]
        if block["length"] > limit:
            problems.append(
                f"{path}:{block['line']}: {block['target']}コメントが {block['length']}行（上限 {limit}行）"
            )
        for word in SMELL_WORDS:
            if word in block["text"]:
                problems.append(f"{path}:{block['line']}: 禁止語「{word}」を含むコメント")
                break
    return problems


def collect(args):
    if args.staged:
        out = subprocess.run(["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
                             capture_output=True, text=True).stdout
        return [p for p in out.split() if p.endswith(".kt") and Path(p).exists()]
    if args.diff:
        out = subprocess.run(["git", "diff", "--name-only", f"{args.diff}...HEAD"],
                             capture_output=True, text=True).stdout
        return [p for p in out.split() if p.endswith(".kt") and Path(p).exists()]
    paths = []
    for root in args.paths or ["app"]:
        target = Path(root)
        if target.is_file():
            paths.append(target)
            continue
        paths.extend(p for p in target.rglob("*.kt") if "/build/" not in str(p))
    return [str(p) for p in paths]


def print_stats(files):
    results = [analyze(f) for f in files]
    groups = {"プロダクション": [r for r in results if not is_test(r["path"])],
              "テスト": [r for r in results if is_test(r["path"])]}
    for name, rows in groups.items():
        if not rows:
            continue
        print(f"[{name}] {len(rows)}ファイル コメント{sum(r['comment'] for r in rows)}行/"
              f"総{sum(r['total'] for r in rows)}行")
        per_decl = sorted(r["comment"] / r["declarations"] for r in rows if r["declarations"])
        print(f"  コメント/宣言 中央{statistics.median(per_decl):.2f} "
              f"p90 {per_decl[int(len(per_decl) * 0.9)]:.2f} 最大{max(per_decl):.2f}")
        inline = sorted(sum(b["length"] for b in r["blocks"] if b["target"] == "inline") / r["code"]
                        for r in rows if r["code"])
        print(f"  インライン/コード 中央{statistics.median(inline):.3f} "
              f"p90 {inline[int(len(inline) * 0.9)]:.3f} 最大{max(inline):.3f}")
        for target in ("class", "decl", "inline"):
            lengths = sorted(b["length"] for r in rows for b in r["blocks"] if b["target"] == target)
            if lengths:
                print(f"  {target:6s} {len(lengths):4d}個 中央{statistics.median(lengths):.0f} "
                      f"p90 {lengths[int(len(lengths) * 0.9)]} 最大{max(lengths)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("paths", nargs="*")
    parser.add_argument("--staged", action="store_true", help="ステージ済みファイルのみ検査する")
    parser.add_argument("--diff", metavar="BASE", help="BASEとの差分ファイルのみ検査する")
    parser.add_argument("--stats", action="store_true", help="違反判定をせず分布だけ出す")
    args = parser.parse_args()

    files = collect(args)
    if not files:
        return 0
    if args.stats:
        print_stats(files)
        return 0

    problems = [p for f in files for p in check(f)]
    for problem in problems:
        print(f"comment-lint: {problem}", file=sys.stderr)
    if problems:
        print(f"comment-lint: {len(problems)}件（規約は docs/comment-policy.md）", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
