#!/usr/bin/env python3
"""Check timed MultiPV study output on the openings fixed by a match manifest."""
import argparse
import json
from pathlib import Path
import statistics
import time

import shogi

from compare_drill_nodes import Engine, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--milliseconds', type=int, default=1000)
    args = parser.parse_args()
    if args.output.exists() or args.milliseconds < 1:
        parser.error('Use a new output directory and a positive time limit')
    if not __debug__:
        parser.error('Verification requires assertions; do not use Python -O')
    manifest = json.loads(args.manifest.read_text())
    for profile in manifest['profiles']:
        assert sha(profile['binary']) == profile['binary_sha256']
        assert sha(Path(profile['evaluation'])/'nn.bin') == profile['evaluation_sha256']
    args.output.mkdir(parents=True)
    engines, rows = [], []
    try:
        for profile in manifest['profiles']:
            engine = Engine(Path(profile['binary']), profile['evaluation'])
            engines.append(engine)
            engine.send(f"setoption name FV_SCALE value {int(profile['fv_scale'])}")
            engine.send('setoption name MultiPV value 3')
        for index, opening in enumerate(manifest['openings']):
            for offset in range(len(engines)):
                which = (index + offset) % len(engines)
                engine = engines[which]
                engine.send('isready')
                engine.until('readyok')
                engine.send('usinewgame')
                engine.send('position startpos moves ' + ' '.join(opening['moves']))
                started = time.monotonic()
                engine.send(f'go movetime {args.milliseconds}')
                lines = engine.until('bestmove')
                seconds = time.monotonic() - started
                board = shogi.Board()
                for usi in opening['moves']:
                    move = shogi.Move.from_usi(usi)
                    assert board.is_legal(move)
                    board.push(move)
                bestmove = lines[-1].split()[1]
                assert board.is_legal(shogi.Move.from_usi(bestmove))
                pvs, checked = {}, 0
                for line in lines:
                    words = line.split()
                    if not line.startswith('info ') or 'score' not in words or 'pv' not in words:
                        continue
                    rank = int(words[words.index('multipv') + 1]) if 'multipv' in words else 1
                    pv = words[words.index('pv') + 1:]
                    branch = shogi.Board(board.sfen())
                    for ply, usi in enumerate(pv):
                        if usi == 'resign':
                            assert branch.is_checkmate() and ply == len(pv) - 1
                            break
                        move = shogi.Move.from_usi(usi)
                        assert branch.is_legal(move)
                        branch.push(move)
                        checked += 1
                    pvs[rank] = pv
                assert set(pvs) == {1, 2, 3}
                row = {'index': index, 'profile': manifest['profiles'][which]['name'],
                       'seconds': seconds, 'bestmove': bestmove, 'pvs': pvs,
                       'distinct_final_pv_roots': len({pv[0] for pv in pvs.values()}),
                       'legal_pv_moves': checked, 'raw': lines}
                (args.output/f'{index:03d}-{which}.json').write_text(json.dumps(row, indent=2)+'\n')
                rows.append(row)
            print(f'{index+1}/{len(manifest["openings"])}', flush=True)
    finally:
        for engine in engines:
            engine.close()
    summary = {'milliseconds': args.milliseconds, 'multipv': 3, 'searches': len(rows),
               'runner_sha256': sha(__file__), 'match_manifest_sha256': sha(args.manifest),
               'legal_pv_moves': sum(r['legal_pv_moves'] for r in rows), 'profiles': {}}
    summary['final_pv_root_duplicates'] = sum(r['distinct_final_pv_roots'] < 3 for r in rows)
    for profile in manifest['profiles']:
        times = sorted(r['seconds'] for r in rows if r['profile'] == profile['name'])
        at = .95 * (len(times) - 1)
        lo = int(at)
        summary['profiles'][profile['name']] = {'searches': len(times),
            'median_seconds': statistics.median(times),
            'p95_seconds': times[lo] + (times[min(lo+1, len(times)-1)] - times[lo]) * (at-lo)}
    (args.output/'summary.json').write_text(json.dumps(summary, indent=2)+'\n')


if __name__ == '__main__':
    main()
