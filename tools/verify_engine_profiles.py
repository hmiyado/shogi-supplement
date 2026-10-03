#!/usr/bin/env python3
"""Validate a complete serial benchmark and its PV legality (requires python-shogi)."""
import argparse
import json
from pathlib import Path
import time
import shogi
from compare_drill_nodes import Engine


def verify_stop(profiles):
    results = []
    for profile in profiles:
        engine = Engine(Path(profile['binary']), profile['evaluation'])
        try:
            engine.send(f"setoption name FV_SCALE value {int(profile['fv_scale'])}")
            engine.send('isready')
            engine.until('readyok')
            engine.send('usinewgame')
            engine.send('position startpos')
            engine.send('go infinite')
            engine.until('info depth')
            start = time.monotonic()
            engine.send('stop')
            deadline = start + 10
            while True:
                line = engine.lines.get(timeout=max(.001, deadline - time.monotonic()))
                if line is None:
                    raise RuntimeError('Engine exited during stop')
                if line.startswith('bestmove '):
                    break
                if time.monotonic() > deadline:
                    raise TimeoutError('Engine did not stop within 10 seconds')
            seconds = time.monotonic() - start
            bestmove = line.split()[1]
            assert shogi.Board().is_legal(shogi.Move.from_usi(bestmove))
            resumed = engine.search(['7g7f', '3c3d'], 400000)
            board = shogi.Board()
            board.push_usi('7g7f')
            board.push_usi('3c3d')
            assert board.is_legal(shogi.Move.from_usi(resumed['bestmove']))
            assert set(resumed['pvs']) == {'1', '2'}
            for pv in resumed['pvs'].values():
                branch = shogi.Board(board.sfen())
                for usi in pv['pv']:
                    move = shogi.Move.from_usi(usi)
                    assert branch.is_legal(move)
                    branch.push(move)
            results.append({'profile': profile['name'], 'stop_seconds': seconds,
                            'bestmove_after_stop': bestmove,
                            'resumed_bestmove': resumed['bestmove'], 'legal': True})
        finally:
            engine.close()
    return results


def verify(directory):
    manifest = json.loads((directory/'manifest.json').read_text())
    profiles = manifest['profiles']
    positions = manifest['positions']
    assert len(positions) == manifest['count']
    rows = {}
    checked_moves = 0
    for index, position in enumerate(positions):
        board = shogi.Board()
        for usi in position['moves_before']:
            move = shogi.Move.from_usi(usi)
            assert board.is_legal(move), ('input', index, usi)
            board.push(move)
        assert board.sfen() == position['sfen_before'], index
        for which, profile in enumerate(profiles):
            row = json.loads((directory/f'{index:03d}-{which}.json').read_text())
            assert row['index'] == index and row['profile'] == profile['name']
            result = row['result']
            bestmove = result['bestmove']
            if bestmove == 'resign':
                assert board.is_checkmate(), (index, profile['name'], 'unexpected resign')
            else:
                assert board.is_legal(shogi.Move.from_usi(bestmove)), (index, bestmove)
            assert len(result['pvs']) == min(manifest['multipv'], len(list(board.legal_moves)))
            for info in result['raw']:
                words = info.split()
                if not info.startswith('info ') or 'pv' not in words or 'score' not in words:
                    continue
                pv = words[words.index('pv')+1:]
                branch = shogi.Board(board.sfen())
                for usi in pv:
                    if usi == 'resign':
                        assert branch.is_checkmate() and usi == pv[-1]
                        break
                    move = shogi.Move.from_usi(usi)
                    assert branch.is_legal(move), (index, profile['name'], usi, info)
                    branch.push(move)
                    checked_moves += 1
            rows[index, which] = result
    differences = {}
    for which, profile in enumerate(profiles[1:], 1):
        differences[profile['name']] = sum(rows[i,0]['bestmove'] != rows[i,which]['bestmove'] for i in range(len(positions)))
    return {'positions':len(positions), 'searches':len(rows), 'legal_pv_moves_checked':checked_moves,
            'bestmove_changes_against_first_profile':differences,
            'scope':'Native engine legality and descriptive search differences; not proof of strength or cross-platform parity'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--stop-check', action='store_true',
                        help='Also run native stop/resume checks using manifest binaries')
    args = parser.parse_args()
    if not __debug__:
        parser.error('Do not run verification with Python -O; assertions must be enabled')
    result = verify(args.directory)
    if args.stop_check:
        manifest = json.loads((args.directory/'manifest.json').read_text())
        result['stop_resume'] = verify_stop(manifest['profiles'])
    (args.directory/'verification.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))
