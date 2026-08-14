#!/usr/bin/env python3
"""VRTのshard定義が、存在するスクリーンショットテストを漏れなく覆っているか検査する。

画面を追加したのにshardへ足し忘れると、その画面だけ検証されないまま緑になる。
"""
import re
import sys
from pathlib import Path

WORKFLOW = Path(".github/workflows/vrt.yml")
TEST_DIR = Path("app/androidApp/src/test")


def shard_classes():
    text = WORKFLOW.read_text(encoding="utf-8")
    matrix = text.split("shard:", 1)[1].split("steps:", 1)[0]
    return set(re.findall(r'--tests "\*([A-Za-z0-9_]+)"', matrix))


def existing_classes():
    return {p.stem for p in TEST_DIR.rglob("*ScreenshotTest.kt")}


def main():
    declared = shard_classes()
    existing = existing_classes()
    missing = sorted(existing - declared)
    stale = sorted(declared - existing)
    for name in missing:
        print(f"shardに含まれていない: {name}", file=sys.stderr)
    for name in stale:
        print(f"存在しないクラスがshardにある: {name}", file=sys.stderr)
    if missing or stale:
        print(f"{WORKFLOW} のshard定義を直してください", file=sys.stderr)
        return 1
    print(f"shard定義OK（{len(existing)}クラス）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
