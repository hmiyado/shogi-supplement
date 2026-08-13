#!/usr/bin/env python3
"""comment_lint.py の検査ロジックのテスト。

  python3 tools/test_comment_lint.py
"""
import argparse
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import comment_lint  # noqa: E402


def write(tmpdir, name, body):
    path = Path(tmpdir) / name
    path.write_text(body, encoding="utf-8")
    return str(path)


class ClassifyTest(unittest.TestCase):
    def test_文字列リテラル中のスラッシュ2つはコメントとして数えない(self):
        kinds = comment_lint.classify_lines(['val url = "http://example.com"\n'])
        self.assertEqual(["code"], kinds)

    def test_ブロックコメントは終端まで数える(self):
        kinds = comment_lint.classify_lines(["/**\n", " * doc\n", " */\n", "val a = 1\n"])
        self.assertEqual(["comment", "comment", "comment", "code"], kinds)

    def test_コード行末尾のコメントはコード行として数える(self):
        kinds = comment_lint.classify_lines(["val a = 1 // 補足\n"])
        self.assertEqual(["code"], kinds)


class BlockTargetTest(unittest.TestCase):
    def test_直後の宣言の種類でコメントの帰属先が決まる(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "A.kt", "// c\nclass A\n// d\nfun f() {}\n// i\nval x = run { 1 }\n")
            blocks = comment_lint.analyze(path)["blocks"]
            self.assertEqual(["class", "decl", "decl"], [b["target"] for b in blocks])

    def test_単独行のアノテーションを挟んでも宣言として扱う(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "A.kt", "// c\n@Composable\nfun F() {}\n")
            self.assertEqual(["decl"], [b["target"] for b in comment_lint.analyze(path)["blocks"]])

    def test_宣言以外の直前のコメントはインライン扱いになる(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "A.kt", "fun f() {\n    // 補足\n    doIt()\n}\n")
            blocks = comment_lint.analyze(path)["blocks"]
            self.assertEqual(["inline"], [b["target"] for b in blocks])


class CheckTest(unittest.TestCase):
    def test_上限を超えたブロックを違反として報告する(self):
        with tempfile.TemporaryDirectory() as tmp:
            body = "".join(f"// line{i}\n" for i in range(comment_lint.BLOCK_MAX["inline"] + 1))
            path = write(tmp, "A.kt", f"fun f() {{\n{body}    doIt()\n}}\n")
            self.assertTrue(any("inlineコメント" in p for p in comment_lint.check(path)))

    def test_上限内のブロックは違反にしない(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "A.kt", "fun f() {\n    // 補足\n    doIt()\n}\n")
            self.assertEqual([], comment_lint.check(path))

    def test_短いファイルは比率を判定しない(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "A.kt", "// a\n// b\nval x = 1\n")
            self.assertEqual([], comment_lint.check(path))

    def test_宣言あたりのコメント量が多いファイルを違反として報告する(self):
        with tempfile.TemporaryDirectory() as tmp:
            # 宣言1つに3行ずつ説明を付ける（上限2.5行/宣言を超える）
            body = "".join(f"// a{i}\n// b{i}\n// c{i}\nval x{i} = {i}\n" for i in range(20))
            path = write(tmp, "A.kt", body)
            self.assertTrue(any("宣言1つあたり" in p for p in comment_lint.check(path)))

    def test_宣言1つに1行ずつの説明は違反にしない(self):
        with tempfile.TemporaryDirectory() as tmp:
            body = "".join(f"/** 説明{i} */\nval x{i} = {i}\n" for i in range(40))
            path = write(tmp, "A.kt", body)
            self.assertEqual([], comment_lint.check(path))

    def test_インラインコメントが多いファイルを違反として報告する(self):
        with tempfile.TemporaryDirectory() as tmp:
            body = "fun f() {\n" + "".join(f"    // note{i}\n    step{i}()\n" for i in range(40)) + "}\n"
            path = write(tmp, "A.kt", body)
            self.assertTrue(any("インラインコメント" in p for p in comment_lint.check(path)))

    def test_禁止語を含むコメントを違反として報告する(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "A.kt", "// ReportScreen から呼ばれる\nfun f() {}\n")
            self.assertTrue(any("禁止語" in p for p in comment_lint.check(path)))

    def test_テストファイルには緩い比率上限を使わない(self):
        self.assertTrue(comment_lint.is_test("app/x/src/test/kotlin/FooTest.kt"))
        self.assertFalse(comment_lint.is_test("app/x/src/commonMain/kotlin/Foo.kt"))


class CollectTest(unittest.TestCase):
    def test_ファイルを直接指定しても検査対象になる(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "A.kt", "// c\nfun f() {}\n")
            args = argparse.Namespace(paths=[path], staged=False, diff=None, stats=False)
            self.assertEqual([path], comment_lint.collect(args))


class CliTest(unittest.TestCase):
    def test_違反があれば終了コード1を返す(self):
        with tempfile.TemporaryDirectory() as tmp:
            write(tmp, "A.kt", "// ReportScreen から呼ばれる\nfun f() {}\n")
            proc = subprocess.run(
                [sys.executable, str(Path(__file__).parent / "comment_lint.py"), tmp],
                capture_output=True, text=True)
            self.assertEqual(1, proc.returncode)
            self.assertIn("禁止語", proc.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
