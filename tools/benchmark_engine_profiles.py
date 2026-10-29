#!/usr/bin/env python3
"""Compare fixed engine profiles serially on a deterministic sample of drill candidates."""
import argparse
import hashlib
import json
from pathlib import Path
import signal
import statistics
import time

from compare_drill_nodes import Engine, request_stop, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profiles', type=Path, required=True)
    parser.add_argument('--positions', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--count', type=int, default=108)
    args = parser.parse_args()
    if args.count < 1:
        parser.error('--count must be positive')
    if args.output.exists():
        parser.error('Use a new output directory; existing measurements are never overwritten')
    profiles = json.loads(args.profiles.read_text())
    positions = json.loads(args.positions.read_text())
    if len({p['name'] for p in profiles}) != len(profiles) or len(profiles) < 2:
        parser.error('Provide at least two uniquely named profiles')
    selected = sorted(positions, key=lambda p: hashlib.sha256(
        json.dumps(p, sort_keys=True, separators=(',', ':')).encode()).hexdigest())[:args.count]
    if len(selected) != args.count:
        parser.error('Insufficient positions')
    for profile in profiles:
        profile['binary'] = str(Path(profile['binary']).resolve())
        profile['evaluation'] = str(Path(profile['evaluation']).resolve())
        profile['binary_sha256'] = sha(profile['binary'])
        profile['evaluation_sha256'] = sha(Path(profile['evaluation']) / 'nn.bin')
    args.output.mkdir(parents=True)
    manifest = {'profiles': profiles, 'positions_sha256': sha(args.positions),
                'runner_sha256': sha(__file__), 'nodes': 400000, 'multipv': 2, 'threads': 1,
                'hash_mb': 128, 'count': len(selected), 'selection': 'canonical JSON SHA256 ascending',
                'order': 'rotate profiles by position index; one search at a time',
                'reset': 'isready/readyok/usinewgame before every search', 'positions': selected}
    (args.output / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)
    engines = []
    rows = []
    try:
        for profile in profiles:
            engine = Engine(Path(profile['binary']), profile['evaluation'])
            engines.append(engine)
            engine.send(f"setoption name FV_SCALE value {int(profile['fv_scale'])}")
        for index, position in enumerate(selected):
            for offset in range(len(profiles)):
                which = (index + offset) % len(profiles)
                profile, engine = profiles[which], engines[which]
                result = engine.search(position['moves_before'], 400000)
                row = {'profile': profile['name'], 'index': index, 'result': result}
                target = args.output / f"{index:03d}-{which}.json"
                temporary = target.with_suffix('.tmp')
                temporary.write_text(json.dumps(row, indent=2))
                temporary.replace(target)
                rows.append(row)
            print(f'{index + 1}/{len(selected)}', flush=True)
        def distribution(values):
            ordered = sorted(values)
            index = .95 * (len(ordered) - 1)
            lo = int(index)
            p95 = ordered[lo] + (ordered[min(lo+1, len(ordered)-1)] - ordered[lo]) * (index-lo)
            return {'count':len(values), 'median_seconds':statistics.median(values), 'p95_seconds':p95}
        summary = {p['name']:distribution([r['result']['seconds'] for r in rows if r['profile']==p['name']]) for p in profiles}
        (args.output/'summary.json').write_text(json.dumps(summary, indent=2))
        print(json.dumps(summary), flush=True)
    finally:
        for engine in engines:
            engine.close()


if __name__ == '__main__':
    main()
