#!/usr/bin/env python3
import pathlib
import shutil
import sys


def prepare(source: pathlib.Path, destination: pathlib.Path) -> None:
    # 上流checkoutを直接書き換えると固定コミットの検証ができなくなる。
    original = '''                sync_cout << "info string shared memory: fallback to local memory";
                if (error_message)
                    sync_cout << " (" << *error_message << ")";
                sync_cout << sync_endl;'''
    # sync_coutは非再帰mutexを取るため、sync_endlまではstd::coutで出力を続ける。
    replacement = '''                sync_cout << "info string shared memory: fallback to local memory";
                if (error_message)
                    std::cout << " (" << *error_message << ")";
                std::cout << sync_endl;'''
    header = (source / "shm.h").read_text()
    if header.count(original) != 1:
        raise RuntimeError("Unexpected shm.h: cannot apply the logging deadlock fix")
    patched = header.replace(original, replacement).encode()
    changed = False
    for path in source.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(source)
        target = destination / relative
        content = patched if relative.as_posix() == "shm.h" else path.read_bytes()
        if not target.exists() or target.read_bytes() != content:
            target.parent.mkdir(parents=True, exist_ok=True)
            if relative.as_posix() == "shm.h":
                target.write_bytes(content)
            else:
                shutil.copy2(path, target)
            changed = True
    # ヘッダだけの変更でも、コンパイル済みオブジェクトを再利用しない。
    if changed:
        (destination / ".source-stamp").touch()


if __name__ == "__main__":
    prepare(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]))
