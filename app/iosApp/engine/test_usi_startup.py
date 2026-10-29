#!/usr/bin/env python3
import argparse
import pathlib
import queue
import subprocess
import threading
import time


def verify(executable: str, eval_dir: str, timeout: float) -> None:
    process = subprocess.Popen(
        [executable], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1,
    )
    lines: queue.Queue = queue.Queue()

    def collect():
        for line in process.stdout:
            lines.put(line.rstrip())
        lines.put(None)

    threading.Thread(target=collect, daemon=True).start()

    def send(command):
        process.stdin.write(command + "\n")
        process.stdin.flush()

    def wait_for(token):
        deadline = time.monotonic() + timeout
        received = []
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise AssertionError(f"Timed out waiting for {token}: {received[-4:]}")
            try:
                line = lines.get(timeout=remaining)
            except queue.Empty:
                raise AssertionError(f"Timed out waiting for {token}: {received[-4:]}")
            if line is None:
                raise AssertionError(f"Engine exited before {token}: {received[-4:]}")
            received.append(line)
            if line == token or line.startswith(token + " "):
                return received

    try:
        send("usi")
        wait_for("usiok")
        for option, value in [("Threads", 1), ("USI_Hash", 128), ("USI_OwnBook", "false"),
                              ("NetworkDelay", 0), ("NetworkDelay2", 0), ("FV_SCALE", 20),
                              ("EvalDir", str(pathlib.Path(eval_dir).resolve()))]:
            send(f"setoption name {option} value {value}")
        for count, position in [(3, "startpos"), (2, "startpos moves 7g7f 3c3d")]:
            send("isready")
            wait_for("readyok")
            send("usinewgame")
            send(f"setoption name MultiPV value {count}")
            send(f"position {position}")
            send("go nodes 400000")
            result = wait_for("bestmove")
            for index in range(1, count + 1):
                assert any(f" multipv {index} " in line and " pv " in line for line in result), result[-4:]
            assert result[-1].split()[1] not in ("resign", "win", "(none)"), result[-1]
            print(f"PASS MultiPV={count}: {result[-1]}", flush=True)
        send("quit")
        assert process.wait(timeout=timeout) == 0
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("executable")
    parser.add_argument("eval_dir")
    parser.add_argument("--timeout", type=float, default=30)
    arguments = parser.parse_args()
    verify(arguments.executable, arguments.eval_dir, arguments.timeout)
