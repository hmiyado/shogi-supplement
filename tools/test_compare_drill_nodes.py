import unittest
from unittest.mock import Mock

from compare_drill_nodes import Engine


class SearchProtocolTest(unittest.TestCase):
    def engine(self, lines):
        engine = Engine.__new__(Engine)
        engine.send = Mock()
        engine.until = Mock(side_effect=[['readyok'], lines])
        return engine

    def test_reset_history_budget_and_last_info_per_rank(self):
        engine = self.engine([
            'info depth 10 multipv 1 score cp 20 nodes 100 pv 2g2f 8c8d',
            'info depth 11 multipv 1 score mate 5 nodes 400020 pv 7g7f',
            'info depth 10 multipv 2 score cp -40 upperbound nodes 400021 pv 2g2f',
            'bestmove 7g7f ponder 3c3d',
        ])
        result = engine.search(['7g7f', '3c3d'], 400000)
        self.assertEqual([c.args[0] for c in engine.send.call_args_list], [
            'isready', 'usinewgame', 'position startpos moves 7g7f 3c3d', 'go nodes 400000',
        ])
        self.assertEqual(result['bestmove'], '7g7f')
        self.assertEqual(result['pvs']['1']['score'], {'mate': 5})
        self.assertEqual(result['pvs']['1']['nodes'], 400020)
        self.assertFalse(result['pvs']['1']['bounded'])
        self.assertTrue(result['pvs']['2']['bounded'])
        self.assertEqual(result['pvs']['2']['bound'], 'upper')
        self.assertNotIn('2', result['last_exact_pvs'])
        self.assertEqual(result['last_exact_pvs']['1']['score'], {'mate': 5})

    def test_no_score_is_not_silently_published(self):
        with self.assertRaises(RuntimeError):
            self.engine(['bestmove resign']).search([], 400000)


if __name__ == '__main__':
    unittest.main()
