#!/usr/bin/env python3
"""Paired native-engine matches; python-shogi is required for rule validation."""
import argparse
import json
from pathlib import Path
import signal
import time

import shogi

from compare_drill_nodes import Engine, request_stop, sha


def position_key(board):
    return ' '.join(board.sfen().split()[:3])


def play(engines, opening, swap, milliseconds, max_plies):
    seats = [1, 0] if swap else [0, 1]
    board = shogi.Board()
    moves = list(opening['moves'])
    occurrences = {position_key(board): [0]}
    checks = []
    for move in moves:
        parsed = shogi.Move.from_usi(move)
        if not board.is_legal(parsed):
            raise ValueError(f'Illegal opening move: {move}')
        mover = board.turn
        board.push(parsed)
        checks.append((mover, board.is_check()))
        occurrences.setdefault(position_key(board), []).append(len(checks))
    if board.is_game_over() or any(len(v) >= 4 for v in occurrences.values()):
        raise ValueError('Opening is already terminal')
    for engine in engines:
        engine.send('isready')
        engine.until('readyok')
        engine.send('usinewgame')
    turns = []
    winner, reason = None, 'ply-limit'
    for _ in range(max_plies):
        who = seats[board.turn]
        engine = engines[who]
        engine.send('position startpos moves ' + ' '.join(moves))
        started = time.monotonic()
        engine.send(f'go movetime {milliseconds}')
        lines = []
        deadline = started + max(10, milliseconds / 1000 * 5)
        while True:
            line = engine.lines.get(timeout=max(.001, deadline - time.monotonic()))
            if line is None:
                raise RuntimeError('Engine exited during search')
            lines.append(line)
            if line.startswith('bestmove '):
                break
            if time.monotonic() > deadline:
                raise TimeoutError('Engine exceeded search watchdog')
        seconds = time.monotonic() - started
        bestmove = lines[-1].split()[1]
        turns.append({'engine': who, 'seconds': seconds, 'bestmove': bestmove, 'raw': lines})
        if bestmove == 'resign':
            winner, reason = 1 - who, 'resign'
            break
        if bestmove == 'win':
            reason = 'declaration-needs-review'
            break
        move = shogi.Move.from_usi(bestmove)
        if not board.is_legal(move):
            raise ValueError(f'Illegal engine move: {bestmove}, {board.sfen()}')
        mover = board.turn
        board.push(move)
        moves.append(bestmove)
        checks.append((mover, board.is_check()))
        if board.is_checkmate():
            winner, reason = who, 'checkmate'
            break
        if not any(board.legal_moves):
            winner, reason = who, 'no-legal-moves'
            break
        repeats = occurrences.setdefault(position_key(board), [])
        repeats.append(len(checks))
        if len(repeats) >= 4:
            interval = checks[repeats[-4]:repeats[-1]]
            checking = [side for side in (0, 1)
                        if any(s == side for s, _ in interval)
                        and all(check for s, check in interval if s == side)]
            if len(checking) == 1:
                winner, reason = 1 - seats[checking[0]], 'perpetual-check'
            else:
                reason = 'repetition' if not checking else 'repetition-needs-review'
            break
    return {'swap': swap, 'winner': winner, 'reason': reason,
            'moves': moves, 'turns': turns, 'final_sfen': board.sfen()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profiles', type=Path, required=True)
    parser.add_argument('--openings', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--milliseconds', type=int, default=200)
    parser.add_argument('--max-plies', type=int, default=320)
    args = parser.parse_args()
    if args.output.exists() or args.milliseconds < 1 or args.max_plies < 1:
        parser.error('Use a new output directory and positive limits')
    profiles = json.loads(args.profiles.read_text())
    openings = json.loads(args.openings.read_text())
    if len(profiles) != 2 or profiles[0]['name'] == profiles[1]['name']:
        parser.error('Exactly two distinct profiles are required')
    if not openings or len({o['game_id'] for o in openings}) != len(openings):
        parser.error('Opening game IDs must be nonempty and unique')
    for profile in profiles:
        profile['binary'] = str(Path(profile['binary']).resolve())
        profile['evaluation'] = str(Path(profile['evaluation']).resolve())
        profile['binary_sha256'] = sha(profile['binary'])
        profile['evaluation_sha256'] = sha(Path(profile['evaluation'])/'nn.bin')
    args.output.mkdir(parents=True)
    manifest = {'profiles': profiles, 'openings': openings, 'milliseconds': args.milliseconds,
                'max_plies': args.max_plies, 'threads': 1, 'hash_mb': 128, 'multipv': 1,
                'runner_sha256': sha(__file__), 'engine_client_sha256': sha(Path(__file__).with_name('compare_drill_nodes.py')),
                'openings_sha256': sha(args.openings)}
    (args.output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)
    engines = []
    try:
        for profile in profiles:
            engine = Engine(Path(profile['binary']), profile['evaluation'])
            engines.append(engine)
            engine.send(f"setoption name FV_SCALE value {int(profile['fv_scale'])}")
            engine.send('setoption name MultiPV value 1')
        for index, opening in enumerate(openings):
            for swap in (bool(index % 2), not bool(index % 2)):
                result = play(engines, opening, swap, args.milliseconds, args.max_plies)
                result['pair'] = index
                path = args.output/f'{index:03d}-{int(swap)}.json'
                temporary = path.with_suffix('.tmp')
                temporary.write_text(json.dumps(result, indent=2)+'\n')
                temporary.replace(path)
                print(json.dumps({'pair': index, 'swap': swap, 'winner': result['winner'],
                                  'reason': result['reason'], 'plies': len(result['turns'])}), flush=True)
    finally:
        for engine in engines:
            engine.close()


if __name__ == '__main__':
    main()
