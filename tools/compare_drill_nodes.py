#!/usr/bin/env python3
"""Run paired node-budget searches on exported drill candidates (JSON only)."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import queue
import signal
import subprocess
import threading
import time

STOP = threading.Event()
PROCESSES = set()
PROCESS_LOCK = threading.Lock()


def request_stop(*_):
    STOP.set()
    with PROCESS_LOCK:
        for process in list(PROCESSES):
            if process.poll() is None:
                process.terminate()


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class Engine:
    def __init__(self, binary, evaluation):
        with PROCESS_LOCK:
            if STOP.is_set():
                raise InterruptedError('Comparison stopped')
            self.process = subprocess.Popen([str(binary)], stdin=subprocess.PIPE,
                                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                            text=True, bufsize=1)
            PROCESSES.add(self.process)
        self.lines = queue.Queue()
        def read():
            for line in self.process.stdout:
                self.lines.put(line.strip())
            self.lines.put(None)
        threading.Thread(target=read, daemon=True).start()
        try:
            self.send('usi')
            self.until('usiok')
            options = {'Threads': 1, 'USI_Hash': 128, 'MultiPV': 2,
                       'USI_OwnBook': 'false', 'FV_SCALE': 20, 'EvalDir': evaluation,
                       'NetworkDelay': 0, 'NetworkDelay2': 0, 'MinimumThinkingTime': 0}
            for name, value in options.items():
                self.send(f'setoption name {name} value {value}')
        except BaseException:
            self.close()
            raise

    def send(self, text):
        if STOP.is_set():
            raise InterruptedError('Comparison stopped')
        self.process.stdin.write(text + '\n')
        self.process.stdin.flush()

    def until(self, token):
        deadline = time.monotonic() + 180
        result = []
        while True:
            if STOP.is_set():
                raise InterruptedError('Comparison stopped')
            line = self.lines.get(timeout=max(0.01, deadline - time.monotonic()))
            if line is None:
                raise RuntimeError(f'Engine exited: {self.process.poll()}')
            result.append(line)
            if line.startswith(token):
                return result

    def search(self, moves, nodes):
        self.send('isready')
        self.until('readyok')
        self.send('usinewgame')
        self.send('position startpos' + (' moves ' + ' '.join(moves) if moves else ''))
        start = time.monotonic()
        self.send(f'go nodes {nodes}')
        lines = self.until('bestmove')
        pvs = {}
        exact_pvs = {}
        for line in lines:
            words = line.split()
            if not line.startswith('info ') or 'score' not in words or 'pv' not in words:
                continue
            score_index = words.index('score')
            rank = int(words[words.index('multipv') + 1]) if 'multipv' in words else 1
            pvs[str(rank)] = {
                'score': {words[score_index + 1]: int(words[score_index + 2])},
                'pv': words[words.index('pv') + 1:],
                'nodes': int(words[words.index('nodes') + 1]),
                'bounded': 'lowerbound' in words or 'upperbound' in words,
                'bound': 'lower' if 'lowerbound' in words else 'upper' if 'upperbound' in words else None,
            }
            if not pvs[str(rank)]['bounded']:
                exact_pvs[str(rank)] = pvs[str(rank)]
        if '1' not in pvs:
            raise RuntimeError(f'No PV1: {lines[-5:]}')
        return {'bestmove': lines[-1].split()[1], 'pvs': pvs, 'last_exact_pvs': exact_pvs,
                'seconds': time.monotonic() - start, 'raw': lines}

    def close(self):
        try:
            if self.process.poll() is None:
                self.send('quit')
                self.process.wait(timeout=5)
        except (OSError, subprocess.TimeoutExpired, InterruptedError):
            self.process.kill()
            self.process.wait()
        finally:
            with PROCESS_LOCK:
                PROCESSES.discard(self.process)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--positions', type=Path, required=True)
    parser.add_argument('--engine', type=Path, required=True)
    parser.add_argument('--eval-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--nodes', type=int, nargs='+', default=[400000, 4000000])
    parser.add_argument('--workers', type=int, default=4)
    parser.add_argument('--limit', type=int)
    args = parser.parse_args()
    if args.workers < 1 or any(n < 1 for n in args.nodes):
        parser.error('workers and nodes must be positive')
    positions = json.loads(args.positions.read_text())
    positions.sort(key=lambda p: hashlib.sha256(f"{p['game_id']}:{p['ply']}".encode()).hexdigest())
    if args.limit is not None:
        positions = positions[:args.limit]
    manifest = {'positions_sha256': sha(args.positions), 'runner_sha256': sha(__file__),
                'engine_sha256': sha(args.engine),
                'evaluation_sha256': sha(args.eval_dir / 'nn.bin'), 'nodes': args.nodes,
                'threads': 1, 'hash_mb': 128, 'multipv': 2, 'fv_scale': 20,
                'reset': 'isready/readyok/usinewgame per search',
                'position': 'startpos with full move history', 'count': len(positions)}
    args.output.mkdir(parents=True, exist_ok=True)
    manifest_path = args.output / 'manifest.json'
    if manifest_path.exists():
        if json.loads(manifest_path.read_text()) != manifest:
            raise ValueError('Output directory belongs to a different experiment')
    else:
        manifest_path.write_text(json.dumps(manifest, indent=2))

    def run(position):
        if STOP.is_set():
            raise InterruptedError('Comparison stopped')
        target = args.output / f"{position['game_id']}-{position['ply']}.json"
        if target.exists():
            saved = json.loads(target.read_text())
            assert saved['position'] == position and set(saved['searches']) == set(map(str, args.nodes))
            return
        engine = Engine(args.engine.resolve(), args.eval_dir.resolve())
        try:
            searches = {}
            for nodes in args.nodes:
                moves = position['moves_before']
                searches[str(nodes)] = {'before': engine.search(moves, nodes),
                                        'after': engine.search(moves + [position['move']], nodes)}
        finally:
            engine.close()
        temporary = target.with_suffix('.partial')
        temporary.write_text(json.dumps({'position': position, 'searches': searches}))
        temporary.replace(target)

    started = time.monotonic()
    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)
    pool = concurrent.futures.ThreadPoolExecutor(max_workers=args.workers)
    try:
        pending = [pool.submit(run, p) for p in positions]
        for count, future in enumerate(concurrent.futures.as_completed(pending), 1):
            future.result()
            print(f'{count}/{len(positions)} elapsed={time.monotonic() - started:.1f}s', flush=True)
    except BaseException:
        request_stop()
        raise
    finally:
        pool.shutdown(wait=True, cancel_futures=True)


if __name__ == '__main__':
    main()
