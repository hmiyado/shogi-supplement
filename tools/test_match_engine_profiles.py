import copy
import queue
import unittest

from match_engine_profiles import play
from summarize_engine_matches import verify


class ScriptedEngine:
    def __init__(self, moves):
        self.moves = iter(moves)
        self.lines = queue.Queue()

    def send(self, command):
        if command.startswith('go '):
            self.lines.put('bestmove ' + next(self.moves))

    def until(self, _):
        return ['readyok']


class MatchRulesTest(unittest.TestCase):
    def test_fourfold_repetition_is_draw(self):
        engines = [ScriptedEngine(['5i6h', '6h5i'] * 3),
                   ScriptedEngine(['5a6b', '6b5a'] * 3)]
        opening = {'moves': []}
        result = play(engines, opening, False, 200, 320)
        self.assertEqual(result['reason'], 'repetition')
        self.assertIsNone(result['winner'])
        self.assertEqual(len(result['moves']), 12)
        verify(result, opening, 320)

    def test_swapped_resignation_and_corrupt_winner(self):
        opening = {'moves': []}
        result = play([ScriptedEngine([]), ScriptedEngine(['resign'])],
                      opening, True, 200, 320)
        self.assertEqual(result['winner'], 0)
        verify(result, opening, 320)
        corrupted = copy.deepcopy(result)
        corrupted['winner'] = 1
        with self.assertRaises(AssertionError):
            verify(corrupted, opening, 320)

    def test_illegal_engine_move_fails_run(self):
        with self.assertRaises(ValueError):
            play([ScriptedEngine(['7g7e']), ScriptedEngine([])],
                 {'moves': []}, False, 200, 320)

    def test_ply_limit_is_unresolved(self):
        result = play([ScriptedEngine(['7g7f']), ScriptedEngine([])],
                      {'moves': []}, False, 200, 1)
        self.assertEqual(result['reason'], 'ply-limit')
        self.assertIsNone(result['winner'])
        verify(result, {'moves': []}, 1)


if __name__ == '__main__':
    unittest.main()
