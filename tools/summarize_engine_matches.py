#!/usr/bin/env python3
"""Verify paired matches and estimate uncertainty by resampling whole opening pairs."""
import argparse
from collections import Counter
import json
from pathlib import Path
import random
import statistics

import shogi

from compare_drill_nodes import sha


def percentile(values, probability):
    ordered = sorted(values)
    index = probability * (len(ordered) - 1)
    lo = int(index)
    return ordered[lo] + (ordered[min(lo + 1, len(ordered) - 1)] - ordered[lo]) * (index - lo)


def verify(game, opening, max_plies):
    board = shogi.Board()
    full_moves = list(opening['moves'])
    history, checks = {}, []
    def remember():
        history.setdefault(' '.join(board.sfen().split()[:3]), []).append(len(checks))
    remember()
    for usi in full_moves:
        move = shogi.Move.from_usi(usi)
        assert board.is_legal(move)
        side = board.turn
        board.push(move)
        checks.append((side, board.is_check()))
        remember()
    pv_count = 0
    seats = [1, 0] if game['swap'] else [0, 1]
    for index, turn in enumerate(game['turns']):
        assert seats[board.turn] == turn['engine']
        assert turn['seconds'] > 0
        assert turn['raw'][-1].split()[:2] == ['bestmove', turn['bestmove']]
        for line in turn['raw']:
            words = line.split()
            if not line.startswith('info ') or 'score' not in words or 'pv' not in words:
                continue
            branch = shogi.Board(board.sfen())
            pv = words[words.index('pv') + 1:]
            for ply, usi in enumerate(pv):
                if usi == 'resign':
                    assert branch.is_checkmate() and ply == len(pv) - 1
                    break
                move = shogi.Move.from_usi(usi)
                assert branch.is_legal(move), (index, line, usi)
                branch.push(move)
                pv_count += 1
        if turn['bestmove'] in ('win', 'resign'):
            assert index == len(game['turns']) - 1
        else:
            move = shogi.Move.from_usi(turn['bestmove'])
            assert board.is_legal(move)
            side = board.turn
            board.push(move)
            checks.append((side, board.is_check()))
            full_moves.append(turn['bestmove'])
            remember()
    assert full_moves == game['moves'] and board.sfen() == game['final_sfen']
    reason, winner = game['reason'], game['winner']
    last = game['turns'][-1]
    if reason == 'checkmate':
        assert board.is_checkmate() and winner == 1 - seats[board.turn]
    elif reason == 'no-legal-moves':
        assert not any(board.legal_moves) and winner == 1 - seats[board.turn]
    elif reason == 'resign':
        assert last['bestmove'] == 'resign' and winner == 1 - last['engine']
    elif reason in ('repetition', 'perpetual-check', 'repetition-needs-review'):
        repeats = history[' '.join(board.sfen().split()[:3])]
        assert len(repeats) >= 4
        checking = []
        for side in (0, 1):
            values = [c for s, c in checks[repeats[-4]:repeats[-1]] if s == side]
            if values and all(values):
                checking.append(side)
        if reason == 'perpetual-check':
            assert len(checking) == 1 and winner == 1 - seats[checking[0]]
        elif reason == 'repetition':
            assert not checking and winner is None
        else:
            assert len(checking) == 2 and winner is None
    elif reason == 'ply-limit':
        assert len(game['turns']) == max_plies and winner is None
    elif reason == 'declaration-needs-review':
        assert last['bestmove'] == 'win' and winner is None
    else:
        raise AssertionError(f'Unknown ending: {reason}')
    return pv_count


def summarize(directory):
    manifest = json.loads((directory/'manifest.json').read_text())
    pair_scores, pair_bounds, results, timings, files = [], [], [], [[], []], {}
    legal_pv_moves = 0
    for pair, opening in enumerate(manifest['openings']):
        scores = []
        for swap in (False, True):
            path = directory/f'{pair:03d}-{int(swap)}.json'
            game = json.loads(path.read_text())
            assert game['pair'] == pair and game['swap'] == swap
            legal_pv_moves += verify(game, opening, manifest['max_plies'])
            for turn in game['turns']:
                timings[turn['engine']].append(turn['seconds'])
            score = (int(game['winner'] == 1) if game['winner'] is not None
                     else .5 if game['reason'] == 'repetition' else None)
            scores.append(score)
            results.append({'pair': pair, 'swap': swap, 'winner': game['winner'],
                            'reason': game['reason'], 'candidate_score': score,
                            'searches': len(game['turns'])})
            files[path.name] = sha(path)
            print(f'verified {len(results)}/{len(manifest["openings"])*2}', flush=True)
        pair_scores.append(sum(scores) / 2 if None not in scores else None)
        pair_bounds.append([sum(s if s is not None else replacement for s in scores) / 2
                            for replacement in (0, 1)])
    complete = all(score is not None for score in pair_scores)
    inference = {'complete_adjudication': complete, 'seed': 101, 'resamples': 20000,
                 'unit': 'opening pair', 'candidate_score_rate': None, 'bootstrap95': None}
    if complete:
        rng = random.Random(101)
        samples = [statistics.mean(rng.choices(pair_scores, k=len(pair_scores))) for _ in range(20000)]
        interval = [percentile(samples, p) for p in (.025, .975)]
        inference.update(candidate_score_rate=statistics.mean(pair_scores), bootstrap95=interval,
                         conclusion='pilot_supports_candidate' if interval[0] > .5 else 'improvement_not_demonstrated')
    else:
        inference['conclusion'] = 'unresolved_games_require_review'
    known_points = sum(r['candidate_score'] for r in results if r['candidate_score'] is not None)
    unresolved = sum(r['candidate_score'] is None for r in results)
    inference['unresolved_games'] = unresolved
    inference['observed_score_rate_bounds'] = [known_points / len(results),
                                              (known_points + unresolved) / len(results)]
    if unresolved:
        rng = random.Random(101)
        samples = [[], []]
        for _ in range(20000):
            indices = rng.choices(range(len(pair_bounds)), k=len(pair_bounds))
            for bound in (0, 1):
                samples[bound].append(statistics.mean(pair_bounds[i][bound] for i in indices))
        inference['supplemental_unresolved_sensitivity'] = {
            'all_unresolved_candidate_losses_bootstrap95': [percentile(samples[0], p) for p in (.025, .975)],
            'all_unresolved_candidate_wins_bootstrap95': [percentile(samples[1], p) for p in (.025, .975)],
            'scope': 'Supplement added after a ply-limit game was observed; primary complete-adjudication analysis remains unavailable',
        }
    profiles = [{k:v for k,v in p.items() if k not in ('binary', 'evaluation')} for p in manifest['profiles']]
    return {'profiles': profiles, 'pairs': len(pair_scores), 'games': len(results),
            'milliseconds': manifest['milliseconds'], 'multipv': manifest['multipv'],
            'threads': manifest['threads'], 'hash_mb': manifest['hash_mb'],
            'openings_sha256': manifest['openings_sha256'], 'runner_sha256': manifest['runner_sha256'],
            'engine_client_sha256': manifest['engine_client_sha256'], 'verifier_sha256': sha(__file__),
            'endings': dict(Counter(r['reason'] for r in results)),
            'candidate_wins': sum(r['winner'] == 1 for r in results),
            'baseline_wins': sum(r['winner'] == 0 for r in results),
            'legal_pv_moves': legal_pv_moves, 'inference': inference,
            'timings': [{'profile': profiles[i]['name'], 'searches': len(values),
                         'median_seconds': statistics.median(values), 'p95_seconds': percentile(values, .95),
                         'max_seconds': max(values),
                         'over_budget_10pct': sum(v > manifest['milliseconds'] / 1000 * 1.1 for v in values)}
                        for i, values in enumerate(timings)],
            'results': results, 'artifact_sha256': files}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not __debug__:
        parser.error('Verification requires assertions; do not use Python -O')
    result = summarize(args.directory)
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result['inference'], indent=2))
