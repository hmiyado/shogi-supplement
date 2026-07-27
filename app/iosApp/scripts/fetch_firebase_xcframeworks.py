#!/usr/bin/env python3
"""fetch-firebase.sh のヘルパー。

firebase-ios-sdk の GitHub Release から「Firebase.zip」（全プロダクト同梱・350MB超）を
HTTP Range リクエストで部分読みし、FirebaseCore・FirebaseAppCheck とその依存
XCFrameworkだけを取り出して書き出す。ZIP全体をダウンロードしない理由・
プラットフォームスライスを間引く理由は fetch-firebase.sh 側のコメント参照。

標準ライブラリのみを使う（この環境のPython3にzipfile/urllib.requestは標準で入っている）。
"""
import io
import plistlib
import sys
import urllib.request
import zipfile

RELEASE_URL_TMPL = (
    "https://github.com/firebase/firebase-ios-sdk/releases/download/{version}/Firebase.zip"
)

# Firebase.zip 内の取得元パス→出力先ディレクトリ名。
# FirebaseAppCheck配下はAppCheck自体の自己完結セット（README記載の統合手順どおり）。
# FirebaseCore/FirebaseCoreInternal/GoogleUtilities/FBLPromisesは、zip内では
# FirebaseAnalyticsフォルダにしか存在しない（各プロダクトフォルダはCarthage向けに
# 重複コピーされているが、Coreそのものはこのフォルダにしか置かれていない。
# 同一リリースzip内なので他プロダクトフォルダのAppCheckと組み合わせても
# バージョンは一致する）。
SOURCE_TO_DEST = {
    "Firebase/FirebaseAppCheck/AppCheckCore.xcframework": "AppCheckCore.xcframework",
    "Firebase/FirebaseAppCheck/FirebaseAppCheck.xcframework": "FirebaseAppCheck.xcframework",
    "Firebase/FirebaseAppCheck/FirebaseAppCheckInterop.xcframework": "FirebaseAppCheckInterop.xcframework",
    "Firebase/FirebaseAppCheck/Promises.xcframework": "Promises.xcframework",
    "Firebase/FirebaseAppCheck/RecaptchaInterop.xcframework": "RecaptchaInterop.xcframework",
    "Firebase/FirebaseAnalytics/FirebaseCore.xcframework": "FirebaseCore.xcframework",
    "Firebase/FirebaseAnalytics/FirebaseCoreInternal.xcframework": "FirebaseCoreInternal.xcframework",
    "Firebase/FirebaseAnalytics/GoogleUtilities.xcframework": "GoogleUtilities.xcframework",
    "Firebase/FirebaseAnalytics/FBLPromises.xcframework": "FBLPromises.xcframework",
}

# iOS実機・シミュレータのみ残す（tvos/watchos/macos/maccatalystは間引く）。
KEEP_LIBRARY_IDENTIFIERS = {"ios-arm64", "ios-arm64_x86_64-simulator"}


class HttpRangeFile(io.RawIOBase):
    """zipfile.ZipFile にそのまま渡せる、HTTP Rangeで都度取得するシーク可能ファイル。"""

    def __init__(self, url):
        self.url = url
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req) as resp:
            self.size = int(resp.headers.get("Content-Length"))
        self.pos = 0

    def readable(self):
        return True

    def seekable(self):
        return True

    def seek(self, offset, whence=io.SEEK_SET):
        if whence == io.SEEK_SET:
            self.pos = offset
        elif whence == io.SEEK_CUR:
            self.pos += offset
        elif whence == io.SEEK_END:
            self.pos = self.size + offset
        return self.pos

    def tell(self):
        return self.pos

    def readinto(self, b):
        n = len(b)
        if n == 0:
            return 0
        end = min(self.pos + n - 1, self.size - 1)
        if self.pos > end:
            return 0
        req = urllib.request.Request(self.url, headers={"Range": f"bytes={self.pos}-{end}"})
        with urllib.request.urlopen(req) as resp:
            data = resp.read()
        b[: len(data)] = data
        self.pos += len(data)
        return len(data)


def wanted(name: str, prefix: str) -> bool:
    if not name.startswith(prefix):
        return False
    rest = name[len(prefix):]
    # 先頭の1階層目（<Platform-Arch>/）だけを見て、残すスライスかどうか判定する。
    top = rest.split("/", 1)[0]
    return top in KEEP_LIBRARY_IDENTIFIERS


def rewrite_info_plist(data: bytes) -> bytes:
    plist = plistlib.loads(data)
    plist["AvailableLibraries"] = [
        lib for lib in plist["AvailableLibraries"] if lib["LibraryIdentifier"] in KEEP_LIBRARY_IDENTIFIERS
    ]
    return plistlib.dumps(plist)


def main():
    version = sys.argv[1]
    out_dir = sys.argv[2]
    url = RELEASE_URL_TMPL.format(version=version)

    print(f"opening {url} (via HTTP Range, no full download)")
    zf = zipfile.ZipFile(HttpRangeFile(url))

    for src_prefix, dest_name in SOURCE_TO_DEST.items():
        src_prefix_slash = src_prefix + "/"
        entries = [n for n in zf.namelist() if wanted(n, src_prefix_slash) and not n.endswith("/")]
        info_plist_name = src_prefix + "/Info.plist"
        if info_plist_name in zf.namelist():
            entries.append(info_plist_name)
        if not entries:
            raise SystemExit(f"no entries found for {src_prefix} (check version/paths)")

        print(f"{dest_name}: {len(entries)} files")
        for name in entries:
            rel = name[len(src_prefix) + 1:]
            if name == info_plist_name:
                data = rewrite_info_plist(zf.read(name))
            else:
                data = zf.read(name)
            out_path = f"{out_dir}/{dest_name}/{rel}"
            import os

            os.makedirs(os.path.dirname(out_path), exist_ok=True)
            with open(out_path, "wb") as f:
                f.write(data)

    print("done")


if __name__ == "__main__":
    main()
