#!/usr/bin/env python3
"""Linux engine smoke with peak resident memory and raw USI evidence."""
import argparse
import json
import platform
from pathlib import Path
import time

import shogi
from compare_drill_nodes import Engine, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', type=Path, required=True)
    parser.add_argument('--evaluation', type=Path, required=True)
    parser.add_argument('--binary-sha256', required=True)
    parser.add_argument('--evaluation-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not __debug__:
        parser.error('Assertions must be enabled')
    assert platform.system() == 'Linux' and platform.machine() == 'x86_64'
    assert sha(args.binary) == args.binary_sha256
    assert sha(args.evaluation/'nn.bin') == args.evaluation_sha256
    started = time.monotonic()
    engine = Engine(args.binary.resolve(), str(args.evaluation.resolve()))
    try:
        engine.send('setoption name FV_SCALE value 40')
        engine.send('setoption name MultiPV value 3')
        engine.send('isready')
        engine.until('readyok')
        ready_seconds = time.monotonic() - started
        rows = []
        for moves in ([], ['7g7f', '3c3d'], ['2g2f', '8c8d', '2f2e', '8d8e']):
            for _ in range(2):
                row = engine.search(moves, 400000)
                board = shogi.Board()
                for usi in moves:
                    board.push_usi(usi)
                assert board.is_legal(shogi.Move.from_usi(row['bestmove']))
                assert set(row['pvs']) == {'1', '2', '3'}
                for pv in row['pvs'].values():
                    branch = shogi.Board(board.sfen())
                    for usi in pv['pv']:
                        move = shogi.Move.from_usi(usi)
                        assert branch.is_legal(move)
                        branch.push(move)
                rows.append(row)
        engine.send('position startpos')
        engine.send('go infinite')
        engine.until('info depth')
        stop_started = time.monotonic()
        engine.send('stop')
        stopped = engine.until('bestmove')
        stop_seconds = time.monotonic() - stop_started
        assert stop_seconds < 10
        assert shogi.Board().is_legal(shogi.Move.from_usi(stopped[-1].split()[1]))
        resumed = engine.search([], 400000)
        assert shogi.Board().is_legal(shogi.Move.from_usi(resumed['bestmove']))
        status = Path(f'/proc/{engine.process.pid}/status').read_text().splitlines()
        memory = {line.split(':')[0]: line.split(':', 1)[1].strip()
                  for line in status if line.startswith(('VmHWM:', 'VmRSS:'))}
        result = {'os': platform.system(), 'architecture': platform.machine(),
                  'kernel': platform.release(), 'binary_sha256': sha(args.binary),
                  'evaluation_sha256': sha(args.evaluation/'nn.bin'),
                  'ready_seconds': ready_seconds, 'stop_seconds': stop_seconds,
                  'memory': memory, 'nodes': 400000, 'threads': 1, 'hash_mb': 128,
                  'multipv': 3, 'fv_scale': 40, 'searches': rows,
                  'resumed_bestmove': resumed['bestmove']}
    finally:
        engine.close()
    result['exit_code'] = engine.process.returncode
    assert result['exit_code'] == 0
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print('ENGINE_PROBE_RESULT=' + json.dumps({k:v for k,v in result.items() if k != 'searches'}), flush=True)


if __name__ == '__main__':
    main()
