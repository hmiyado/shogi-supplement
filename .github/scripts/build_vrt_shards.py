#!/usr/bin/env python3
"""VRTのshard分割をテストクラスの実体から組み立て、matrixとして出力する。

Why not workflowにクラス名を列挙する: 画面を追加するたびに人が足す必要があり、
足し忘れるとその画面だけ検証されない。検査で弾く形にしていたが、
今度は検査の失敗でVRT全体が止まるようになっただけだった。
"""
import json
import os
import re
import sys
from pathlib import Path

TEST_DIR = Path("app/androidApp/src/test")
SHARD_COUNT = 3

# Why not 撮影枚数そのもの: 画像名はクラス名と対応しておらず、実行前に数えられない。
TEST_PATTERN = re.compile(r"^\s*@Test\b", re.MULTILINE)


def weighted_classes():
    found = []
    for path in sorted(TEST_DIR.rglob("*ScreenshotTest.kt")):
        weight = len(TEST_PATTERN.findall(path.read_text(encoding="utf-8")))
        found.append((path.stem, max(weight, 1)))
    return sorted(found, key=lambda item: (-item[1], item[0]))


def pack(classes):
    shards = [[] for _ in range(SHARD_COUNT)]
    loads = [0] * SHARD_COUNT
    for name, weight in classes:
        target = loads.index(min(loads))
        shards[target].append(name)
        loads[target] += weight
    return shards, loads


def main():
    classes = weighted_classes()
    if not classes:
        print(f"{TEST_DIR} にスクリーンショットテストが1件も無い", file=sys.stderr)
        return 1

    shards, loads = pack(classes)
    matrix = [
        {
            "name": f"shard-{index + 1}",
            "tests": " ".join(f'--tests "*{name}"' for name in names),
        }
        for index, names in enumerate(shards)
        if names
    ]

    for entry, load in zip(matrix, loads):
        print(f"{entry['name']}: {load}件 {entry['tests']}")

    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as handle:
            handle.write(f"matrix={json.dumps(matrix, ensure_ascii=False)}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
