import importlib.util
from pathlib import Path
import tempfile
import unittest

script = Path(__file__).resolve().parents[1] / 'app/engine/prepare_mate_verification.py'
spec = importlib.util.spec_from_file_location('prepare_mate', script)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PrepareMateTest(unittest.TestCase):
    def test_idempotent_and_header_changes_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            (source / 'mate').mkdir()
            (source / 'mate/mate.h').write_text('#include <vector>\nnamespace Mate::Dfpn\n{\n}\n')
            (source / 'mate/mate_dfpn.hpp').write_text('while (condition && !stop // stop\n) {}\nauto rep = mate_repetition(pos, plies_from_root, MAX_REPETITION_PLY, or_node);')
            (source / 'usi.cpp').write_text('''#include "movegen.h"
    engine.set_on_update_full(
      [this](const auto& i) { on_update_full(i /*, engine.get_options()["UCI_ShowWDL"] */); });
    engine.set_on_bestmove([](const auto& bm, const auto& p) { on_bestmove(bm, p); });''')
            self.assertTrue(module.prepare(source))
            self.assertFalse(module.prepare(source))
            usi = source / 'usi.cpp'
            self.assertIn('<< " score mate " << n', usi.read_text())
            usi.write_text(usi.read_text().replace('<< " score mate " << n', '<< " score cp " << (n > 0 ? 9999 : -9999)'))
            self.assertTrue(module.prepare(source))
            self.assertIn('<< " score mate " << n', usi.read_text())
            self.assertFalse(module.prepare(source))
            (source / 'verified_mate.h').write_text('old header')
            self.assertTrue(module.prepare(source))
            self.assertFalse(module.prepare(source))


if __name__ == '__main__':
    unittest.main()
