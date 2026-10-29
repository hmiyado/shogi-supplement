import os
import unittest
from probe_shortest_mate import Answer, parse_answer, shortest_mate, UsiMate


class ShortestMateTest(unittest.TestCase):
    def run_search(self, answers, limit=9):
        seen = []
        def search(depth):
            seen.append(depth)
            return answers[depth]
        return shortest_mate(search, limit), seen

    def test_binary_search_with_proven_upper_bound(self):
        result, seen = self.run_search({0: Answer('mate', ('a',) * 7),
                                       7: Answer('mate', ('a',) * 5),
                                       3: Answer('no_mate'), 5: Answer('mate', ('a',) * 5)})
        self.assertEqual(result['plies'], 5)
        self.assertEqual(seen, [0, 7, 3, 5])
        self.assertEqual(result['pv'], ('a',) * 5)

    def test_pv_is_not_a_bound_on_longest_defense(self):
        result, seen = self.run_search({0: Answer('mate', ('a',) * 3),
                                       3: Answer('no_mate'), 7: Answer('mate', ('b',) * 7),
                                       5: Answer('mate', ('c',) * 5)})
        self.assertEqual(result['plies'], 5)
        self.assertEqual(seen, [0, 3, 7, 5])
        self.assertEqual(result['pv'], ('c',) * 5)

    def test_unknown_midpoint_prevents_shortest_claim(self):
        result, seen = self.run_search({0: Answer('mate', ('a',) * 7),
                                       7: Answer('mate', ('a',) * 7), 3: Answer('unknown')})
        self.assertIsNone(result['plies'])
        self.assertEqual(result['status'], 'mate_length_unknown')
        self.assertEqual(seen, [0, 7, 3])

    def test_unknown_upper_bound_is_not_used_as_proof(self):
        result, seen = self.run_search({0: Answer('mate', ('a',) * 7), 7: Answer('unknown')})
        self.assertEqual(result['status'], 'mate_length_unknown')
        self.assertEqual(seen, [0, 7])

    def test_depth_cap_does_not_disprove_existing_mate(self):
        result, seen = self.run_search({0: Answer('mate', ('a',) * 7),
                                       3: Answer('no_mate')}, 4)
        self.assertEqual(result['status'], 'mate_length_unknown')
        self.assertEqual(seen, [0, 3])

    def test_single_ply_needs_no_additional_lower_query(self):
        result, seen = self.run_search({0: Answer('mate', ('a',)),
                                       1: Answer('mate', ('a',))}, 1)
        self.assertEqual(result['plies'], 1)
        self.assertEqual(seen, [0, 1])

    def test_all_thresholds_and_pv_hints(self):
        for threshold in range(1, 32, 2):
            for hint in range(1, 34):
                with self.subTest(threshold=threshold, hint=hint):
                    seen = []
                    def search(depth):
                        seen.append(depth)
                        return (Answer('mate', ('a',) * hint) if depth == 0 else
                                Answer('mate', ('b',) * threshold) if depth >= threshold else
                                Answer('no_mate'))
                    result = shortest_mate(search, 32)
                    self.assertEqual(result['plies'], threshold)
                    self.assertEqual(len(seen), len(set(seen)))
                    self.assertTrue(all(d == 0 or d % 2 == 1 and d <= 31 for d in seen))

    def test_long_mate_reduces_number_of_queries(self):
        seen = []
        def search(depth):
            seen.append(depth)
            return Answer('mate', ('a',) * 31) if depth in (0, 31) else Answer('no_mate')
        self.assertEqual(shortest_mate(search, 31)['plies'], 31)
        self.assertEqual(seen, [0, 31, 15, 23, 27, 29])

    def test_discovery_unknown_is_not_no_mate(self):
        result, seen = self.run_search({0: Answer('unknown')})
        self.assertEqual(result['status'], 'unknown')
        self.assertEqual(seen, [0])

    def test_discovery_no_mate(self):
        result, seen = self.run_search({0: Answer('no_mate')})
        self.assertEqual(result['status'], 'no_mate')
        self.assertEqual(seen, [0])

    def test_protocol_distinguishes_exhaustion(self):
        self.assertEqual(parse_answer('checkmate none').status, 'unknown')
        self.assertEqual(parse_answer('checkmate timeout').status, 'unknown')
        self.assertEqual(parse_answer('checkmate nomate').status, 'no_mate')
        self.assertEqual(parse_answer('checkmate G*5b').pv, ('G*5b',))


@unittest.skipUnless(os.environ.get('YANEURA_MATE_ENGINE'), 'dedicated engine not configured')
class RealEngineTest(unittest.TestCase):
    SFEN = '3sks3/9/4+P4/9/9/+B8/9/9/K8 b S2rb4gs4n4l17p 1'

    def solve(self, sfen, nodes=100000):
        engine = UsiMate(os.environ['YANEURA_MATE_ENGINE'], sfen, nodes, 1000, 16)
        try:
            return shortest_mate(engine.search, 9)
        finally:
            engine.close()

    def test_three_plies_against_independent_all_defenses(self):
        import shogi
        board = shogi.Board(self.SFEN)
        attacker = board.turn
        def forced(depth):
            if board.is_checkmate():
                return board.turn != attacker
            if depth == 0:
                return False
            attack = board.turn == attacker
            moves = list(board.legal_moves)
            for move in moves:
                board.push(move)
                result = (not attack or board.is_check()) and forced(depth - 1)
                board.pop()
                if result == attack:
                    return attack
            return not attack and bool(moves)
        self.assertFalse(forced(1))
        self.assertTrue(forced(3))
        self.assertEqual(self.solve(self.SFEN)['plies'], 3)

    def test_absolute_game_ply_does_not_change_length(self):
        self.assertEqual(self.solve(self.SFEN[:-1] + '41')['plies'], 3)

    def test_white_attacker(self):
        import shogi
        source = shogi.Board(self.SFEN)
        board = shogi.Board()
        board.clear()
        for square in range(81):
            piece = source.piece_at(square)
            if piece:
                board.set_piece_at(80 - square, shogi.Piece(piece.piece_type, 1 - piece.color))
        for color in (0, 1):
            for piece, count in source.pieces_in_hand[color].items():
                for _ in range(count):
                    board.add_piece_into_hand(piece, 1 - color)
        board.turn = shogi.WHITE
        self.assertEqual(self.solve(board.sfen())['plies'], 3)

    def test_node_exhaustion_is_unknown(self):
        self.assertEqual(self.solve(self.SFEN, nodes=1)['status'], 'unknown')


if __name__ == '__main__':
    unittest.main()
