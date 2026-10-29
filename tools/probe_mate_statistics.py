"""Probe threat-mate assessment with a dedicated USI mate engine."""
import argparse
from dataclasses import asdict
import json
import time

import shogi

from probe_shortest_mate import UsiMate


def assessment_sfen(sfen, move=None):
    board = shogi.Board(sfen)
    if board.is_check():
        raise ValueError('a checked position is not a threat-mate opportunity')
    if move is None:
        board.push(shogi.Move.null())
    else:
        parsed = shogi.Move.from_usi(move)
        if not board.is_legal(parsed):
            raise ValueError('illegal response: ' + move)
        board.push(parsed)
    return board.sfen()


def mirror_sfen(sfen):
    original = shogi.Board(sfen)
    board = shogi.Board()
    board.clear()
    for square in range(81):
        piece = original.piece_at(square)
        if piece:
            board.set_piece_at(80 - square, shogi.Piece(piece.piece_type, 1 - piece.color))
    for side in (shogi.BLACK, shogi.WHITE):
        for piece_type, count in original.pieces_in_hand[side].items():
            for _ in range(count):
                board.add_piece_into_hand(piece_type, 1 - side)
    board.turn = 1 - original.turn
    return board.sfen()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine', required=True)
    parser.add_argument('--nodes', type=int, default=100000)
    parser.add_argument('--millis', type=int, default=500)
    args = parser.parse_args()
    if min(args.nodes, args.millis) < 1:
        parser.error('budgets must be positive')
    original = '3lkl3/3p1p3/4G4/9/9/9/9/9/K8 w GPs 1'
    cases = [
        ('threat_before_response', original, None, 'mate'),
        ('threat_removed', original, 'S*5b', 'no_mate'),
        ('threat_ignored', original, '4b4c', 'mate'),
        ('no_threat', shogi.STARTING_SFEN, None, 'no_mate'),
        ('mirrored_threat', mirror_sfen(original), None, 'mate'),
        ('mirrored_removed', mirror_sfen(original), 'S*5h', 'no_mate'),
        ('mirrored_ignored', mirror_sfen(original), '6h6g', 'mate'),
    ]
    results = []
    for name, sfen, response, expected in cases:
        target = assessment_sfen(sfen, response)
        start = time.monotonic()
        engine = UsiMate(args.engine, target, args.nodes, args.millis, 16)
        try:
            answer = engine.search(0)
        finally:
            engine.close()
        if answer.status != expected:
            raise AssertionError((name, expected, answer))
        results.append(dict(name=name, sfen=sfen, response=response, search_sfen=target,
                            answer=asdict(answer), seconds=time.monotonic() - start))
    print(json.dumps(dict(scope='Synthetic local dedicated-engine probe; not product integration',
                          nodes=args.nodes, millis=args.millis, cases=results), indent=2))


if __name__ == '__main__':
    main()
