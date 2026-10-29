"""Verify shortest continuous-check mate using a dedicated YaneuraOu USI engine."""
import argparse
import json
import queue
import subprocess
import threading
import time
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class Answer:
    status: str
    pv: tuple[str, ...] = ()


def parse_answer(line):
    words = line.split()
    if not words or words[0] != 'checkmate' or len(words) < 2:
        raise ValueError(line)
    if words[1] == 'nomate':
        return Answer('no_mate')
    if words[1] in ('none', 'timeout'):
        return Answer('unknown')
    return Answer('mate', tuple(words[1:]))


def shortest_mate(search, max_depth):
    """search(0) discovers mate; search(N) proves mate within N plies.

    PV length is only a suggested bound. A bounded no_mate result never
    invalidates an unbounded mate proof. Establish a proven upper bound, then
    binary-search odd depths. Unknown at any queried bound prevents
    claiming a shortest length. Every call must use a fresh proof tree and no
    depth-independent shared transposition table.
    """
    found = search(0)
    evidence = [{'depth': 0, **asdict(found)}]
    if found.status != 'mate':
        return {'status': found.status, 'plies': None, 'evidence': evidence}
    hint = len(found.pv)
    cap = (max_depth - 1) // 2
    lower = -1
    upper = min(hint // 2, cap)

    def bounded(index):
        depth = 2 * index + 1
        answer = search(depth)
        evidence.append({'depth': depth, **asdict(answer)})
        return answer

    def unknown_length():
        return {'status': 'mate_length_unknown', 'plies': None,
                'pv_length_hint': hint, 'evidence': evidence}

    if cap < 0:
        return unknown_length()
    answer = bounded(upper)
    while answer.status != 'mate':
        if answer.status == 'unknown' or upper == cap:
            return unknown_length()
        lower = upper
        upper = min(2 * upper + 1, cap)
        answer = bounded(upper)

    proof = answer
    while upper - lower > 1:
        middle = (lower + upper) // 2
        answer = bounded(middle)
        if answer.status == 'unknown':
            return unknown_length()
        if answer.status == 'mate':
            upper, proof = middle, answer
        else:
            lower = middle
    return {'status': 'shortest_mate', 'plies': 2 * upper + 1,
            'pv': proof.pv, 'pv_length_hint': hint, 'evidence': evidence}



class UsiMate:
    def __init__(self, executable, sfen, nodes, millis, hash_mb):
        self.sfen, self.nodes, self.millis = sfen, nodes, millis
        self.process = subprocess.Popen([executable], stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                        text=True, bufsize=1)
        self.lines = queue.Queue()
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()
        self.send('usi')
        options = self.until('usiok')
        if not any(line.startswith('option name DepthLimit ') for line in options):
            self.close()
            raise ValueError('engine must support DepthLimit')
        for name, value in [('Threads', 1), ('USI_Hash', hash_mb),
                            ('PvInterval', 0), ('SolverType', '32bitNodeSolver')]:
            self.send(f'setoption name {name} value {value}')

    def _read(self):
        for line in self.process.stdout:
            self.lines.put(line.strip())
        self.lines.put(None)

    def send(self, line):
        self.process.stdin.write(line + '\n')
        self.process.stdin.flush()

    def until(self, prefix):
        deadline = time.monotonic() + self.millis / 1000 + 10
        lines = []
        while True:
            line = self.lines.get(timeout=max(.01, deadline - time.monotonic()))
            if line is None:
                raise RuntimeError('engine exited')
            lines.append(line)
            if line.startswith(prefix):
                return lines

    def search(self, depth):
        self.send(f'setoption name DepthLimit value {depth}')
        self.send(f'setoption name NodesLimit value {self.nodes}')
        self.send('isready')
        self.until('readyok')
        self.send('usinewgame')
        self.send('position sfen ' + self.sfen)
        self.send(f'go mate {self.millis}')
        answer = parse_answer(self.until('checkmate')[-1])
        if answer.status == 'mate':
            import shogi
            board = shogi.Board(self.sfen)
            for move in answer.pv:
                if not board.is_legal(shogi.Move.from_usi(move)):
                    raise ValueError('illegal proof PV: ' + move)
                board.push_usi(move)
            if not board.is_checkmate():
                raise ValueError('proof PV does not end in checkmate')
        return answer

    def close(self):
        if self.process.poll() is None:
            try:
                self.send('quit')
                self.process.wait(timeout=2)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait()
        self.reader.join(timeout=2)
        self.process.stdin.close()
        self.process.stdout.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine', required=True)
    parser.add_argument('--sfen', required=True)
    parser.add_argument('--max-depth', type=int, required=True)
    parser.add_argument('--nodes', type=int, default=100000)
    parser.add_argument('--millis', type=int, default=1000)
    parser.add_argument('--hash-mb', type=int, default=16)
    args = parser.parse_args()
    if min(args.max_depth, args.nodes, args.millis, args.hash_mb) < 1:
        parser.error('budgets must be positive')
    engine = UsiMate(args.engine, args.sfen, args.nodes, args.millis, args.hash_mb)
    try:
        print(json.dumps(shortest_mate(engine.search, args.max_depth), indent=2))
    finally:
        engine.close()


if __name__ == '__main__':
    main()
