#!/usr/bin/env python3
"""Run mate-verification regressions against an integrated native USI engine."""
import argparse
import pathlib
import queue
import subprocess
import threading
import time
from dataclasses import dataclass

import shogi

SCREENSHOT_SFEN = (
    'l7l/1k7/pPn+PS2pp/2p1s4/N2p1N2b/2P1bP3/p1+s1p1P1P/'
    '2G2K3/LNG2G2L w R4Prgsp 112'
)
PRECEDING_SFEN = (
    'l7l/1k7/p1n+PS2pp/1Pp1s4/N2p1N2b/2P1bP3/p1+s1p1P1P/'
    '2G2K3/LNG2G2L b R4Prgsp 111'
)
VALID_SFEN = '3sks3/9/4+P4/9/9/+B8/9/9/K8 b S2rb4gs4n4l17p 1'
HISTORY_MOVES = ('9i8i', '4a3b', '8i9i', '3b4a')


@dataclass(frozen=True)
class Verified:
    multipv: int
    mate: int
    pv: tuple


class Engine:
    def __init__(self, executable, eval_dir, timeout, hash_mb, multipv):
        self.timeout = timeout
        self.process = subprocess.Popen(
            [executable], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1)
        self.lines = queue.Queue()
        threading.Thread(target=self._read, daemon=True).start()
        self.send('usi')
        self.until('usiok')
        for name, value in [('Threads', 1), ('USI_Hash', hash_mb), ('MultiPV', multipv),
                            ('USI_OwnBook', 'false'), ('NetworkDelay', 0),
                            ('NetworkDelay2', 0), ('FV_SCALE', 20),
                            ('EvalDir', eval_dir)]:
            self.send(f'setoption name {name} value {value}')

    def _read(self):
        for line in self.process.stdout:
            self.lines.put(line.strip())
        self.lines.put(None)

    def send(self, line):
        self.process.stdin.write(line + '\n')
        self.process.stdin.flush()

    def until(self, prefix):
        deadline = time.monotonic() + self.timeout
        received = []
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError(f'timed out waiting for {prefix}: {received[-4:]}')
            try:
                line = self.lines.get(timeout=remaining)
            except queue.Empty:
                raise RuntimeError(f'timed out waiting for {prefix}: {received[-4:]}')
            if line is None:
                raise RuntimeError(f'engine exited before {prefix}: {received[-4:]}')
            received.append(line)
            if line == prefix or line.startswith(prefix + ' '):
                return received

    def search(self, position, move):
        self.send('isready')
        self.until('readyok')
        self.send('usinewgame')
        self.send('position ' + position)
        self.send('go ' + move)
        return self.until('bestmove')

    def close(self):
        if self.process.poll() is None:
            try:
                self.send('quit')
                self.process.wait(timeout=2)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait()
        self.process.stdin.close()
        self.process.stdout.close()


def parse_verified(lines):
    found = []
    for line in lines:
        words = line.split()
        if 'mateverified' not in words:
            continue
        if 'score' not in words or 'mate' not in words or 'pv' not in words:
            raise AssertionError(f'malformed verified mate line: {line}')
        score = words.index('score')
        pv = words.index('pv')
        multipv = int(words[words.index('multipv') + 1]) if 'multipv' in words else 1
        found.append(Verified(multipv, int(words[score + 2]), tuple(words[pv + 1:])))
    return found


def validate_pv(sfen, moves, pv, mate):
    board = shogi.Board(sfen)
    for move in moves:
        board.push_usi(move)
    attacker = board.turn if mate > 0 else 1 - board.turn
    for usi in pv:
        move = shogi.Move.from_usi(usi)
        assert board.is_legal(move), f'illegal verified PV move: {usi}'
        attacking = board.turn == attacker
        board.push(move)
        if attacking:
            assert board.is_check(), f'non-check attacker move in verified PV: {usi}'
    assert board.is_checkmate(), 'verified PV does not end in checkmate'
    assert board.turn != attacker, 'verified PV does not mate the defender'


def check_mate(label, sfen, lines, expected):
    verified = parse_verified(lines)
    assert verified, f'{label}: no verified mate'
    for result in verified:
        assert len(result.pv) == abs(result.mate), (
            f'{label}: multipv {result.multipv} PV length {len(result.pv)}')
        validate_pv(sfen, (), result.pv, result.mate)
    first = [v for v in verified if v.multipv == 1]
    assert first, f'{label}: no verified multipv 1 mate'
    result = first[-1]
    assert result.mate == expected, f'{label}: expected mate {expected}, got {result.mate}'
    print(f'PASS {label}: score mate {result.mate} pv {" ".join(result.pv)} '
          f'({len(verified)} verified)')


def check_no_verified(label, lines):
    verified = parse_verified(lines)
    assert not verified, f'{label}: unexpected verified mate {verified}'
    print(f'PASS {label}: no verified mate')


def check_preceding_fixture():
    board = shogi.Board(PRECEDING_SFEN)
    move = shogi.Move.from_usi('8d8c')
    assert board.is_legal(move), 'preceding fixture cannot play 8d8c'
    board.push(move)
    assert board.sfen().split()[:2] == SCREENSHOT_SFEN.split()[:2], (
        'preceding fixture does not reach the screenshot position')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine', required=True)
    parser.add_argument('--eval-dir', required=True)
    parser.add_argument('--go', default='nodes 400000')
    parser.add_argument('--timeout', type=float, default=60)
    parser.add_argument('--hash-mb', type=int, default=128)
    parser.add_argument('--multipv', type=int, default=2)
    args = parser.parse_args()
    if args.multipv < 1:
        parser.error('--multipv must be positive')
    check_preceding_fixture()
    engine = Engine(str(pathlib.Path(args.engine).resolve()),
                    str(pathlib.Path(args.eval_dir).resolve()),
                    args.timeout, args.hash_mb, args.multipv)
    try:
        lines = engine.search('sfen ' + SCREENSHOT_SFEN, args.go)
        check_mate('screenshot', SCREENSHOT_SFEN, lines, -4)
        lines = engine.search('sfen ' + PRECEDING_SFEN, args.go)
        check_mate('preceding fixture', PRECEDING_SFEN, lines, 3)
        lines = engine.search('sfen ' + VALID_SFEN, args.go)
        check_mate('valid', VALID_SFEN, lines, 3)
        lines = engine.search('startpos', args.go)
        check_no_verified('invalid', lines)
        lines = engine.search(
            'sfen ' + VALID_SFEN + ' moves ' + ' '.join(HISTORY_MOVES), args.go)
        check_no_verified('history-cycle', lines)
        lines = engine.search('sfen ' + VALID_SFEN, 'nodes 1')
        check_no_verified('budget (engine nodes 1: search exhaustion, not verifier budget)', lines)
    finally:
        engine.close()


if __name__ == '__main__':
    main()
