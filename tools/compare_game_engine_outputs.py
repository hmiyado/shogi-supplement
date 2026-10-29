#!/usr/bin/env python3
"""Compare saved analyses with the frozen blunder v1.1 rule used in this experiment."""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import statistics


# Preserve the experiment's rule so later production changes do not alter its results.
def to_cp(score):
    if not score:
        return None
    if 'cp' in score:
        return max(-30000, min(30000, score['cp']))
    mate = score.get('mate')
    if not isinstance(mate, int):
        return None
    return -30000 if mate == 0 else (1 if mate > 0 else -1) * (30000 - abs(mate))


def winprob(cp):
    return 1.0 / (1.0 + math.exp(-cp / 600))


def is_blunder_v1(cp_before, cp_after, before, after, move_usi=None, best_usi=None):
    if move_usi is not None and best_usi is not None and move_usi == best_usi:
        return False
    mate_before, mate_after = before.get('mate'), after.get('mate')
    if isinstance(mate_before, int) and mate_before > 0 and not (
        isinstance(mate_after, int) and mate_after <= 0
    ):
        return True
    if isinstance(mate_after, int) and mate_after > 0 and not (
        isinstance(mate_before, int) and mate_before < 0
    ) and cp_before > -500:
        return True
    return cp_before + cp_after >= 500 and 0.05 <= winprob(cp_before) <= 0.95 and cp_after > 0


def compare(directory):
    entries = json.loads((directory / 'sample.json').read_text())
    counts = Counter()
    changes = []
    losses = {'hao': [], 'aoba': []}
    sources = {}
    game_changes = []
    for entry in entries:
        gid = entry['game']['id']
        moves = entry['game']['moves'].split()
        records = {}
        for engine in losses:
            path = directory / 'evals' / engine / (gid + '.json')
            raw = path.read_bytes()
            sources[engine + '/' + gid] = hashlib.sha256(raw).hexdigest()
            records[engine] = json.loads(raw)['evals']
            assert len(records[engine]) == len(moves) + 1
        differing = 0
        for index, move in enumerate(moves):
            flags = {}
            pair_loss = {}
            for engine, evs in records.items():
                before, after = evs[index:index + 2]
                a, b = to_cp(before['score']), to_cp(after['score'])
                assert a is not None and b is not None
                flags[engine] = is_blunder_v1(a, b, before['score'], after['score'], move, before['pv'][0])
                pair_loss[engine] = winprob(a) - winprob(-b)
                losses[engine].append(pair_loss[engine])
                counts[engine + '_blunders'] += flags[engine]
            counts['moves'] += 1
            counts[f"hao_{int(flags['hao'])}_aoba_{int(flags['aoba'])}"] += 1
            different = flags['hao'] != flags['aoba']
            differing += different
            counts['bestmove_changed'] += records['hao'][index]['pv'][0] != records['aoba'][index]['pv'][0]
            changes.append(abs(pair_loss['aoba'] - pair_loss['hao']))
        game_changes.append(differing)
    n = counts['moves']
    agreement = (counts['hao_0_aoba_0'] + counts['hao_1_aoba_1']) / n
    h, a = counts['hao_blunders'] / n, counts['aoba_blunders'] / n
    chance = h*a + (1-h)*(1-a)
    return {'games': len(entries), 'counts': dict(counts), 'blunder_agreement': agreement,
            'cohen_kappa': (agreement-chance)/(1-chance),
            'games_with_changed_blunders': sum(x > 0 for x in game_changes),
            'mean_loss_wp': {k: statistics.mean(v) for k, v in losses.items()},
            'mean_absolute_loss_wp_change': statistics.mean(changes),
            'p95_absolute_loss_wp_change': sorted(changes)[int(.95*(len(changes)-1))],
            'input_sha256': sources,
            'scope': 'Reaggregate saved 400k paired analyses; disagreement is not an accuracy label. No new engine search.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = compare(args.directory)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: v for k, v in result.items() if k != 'input_sha256'}, indent=2))
