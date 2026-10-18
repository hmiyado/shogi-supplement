"""Add the shared df-pn verification hook to a prepared pinned source tree."""
from pathlib import Path
import sys


def prepare(source):
    target = source / 'verified_mate.h'
    changed = False
    header = (Path(__file__).parent / 'mate/verified_mate.h').read_bytes()
    if not target.exists() or target.read_bytes() != header:
        target.write_bytes(header)
        changed = True
    mate = source / 'mate/mate.h'
    text = mate.read_text(encoding='utf-8-sig')
    if 'verification_deadline' not in text:
        text = text.replace('#include <vector>', '#include <vector>\n#include <chrono>')
        text = text.replace('namespace Mate::Dfpn\n{', 'namespace Mate::Dfpn\n{\n    inline thread_local auto verification_deadline = std::chrono::steady_clock::time_point::max();')
        mate.write_text(text)
        changed = True
    if 'verification_repetition_used' not in mate.read_text():
        text = mate.read_text().replace('namespace Mate::Dfpn\n{', 'namespace Mate::Dfpn\n{\n    inline thread_local bool verification_repetition_used = false;')
        mate.write_text(text)
        changed = True
    dfpn = source / 'mate/mate_dfpn.hpp'
    text = dfpn.read_text(encoding='utf-8-sig')
    if 'verification_deadline' not in text:
        anchor = '&& !stop //'
        if text.count(anchor) != 1:
            raise RuntimeError('Unexpected df-pn stop condition')
        text = text.replace(anchor, '&& std::chrono::steady_clock::now() < Mate::Dfpn::verification_deadline\n                 ' + anchor)
        dfpn.write_text(text)
        changed = True
    if 'verification_repetition_used' not in dfpn.read_text():
        text = dfpn.read_text(encoding='utf-8-sig')
        anchor = 'auto rep = mate_repetition(pos, plies_from_root, MAX_REPETITION_PLY, or_node);'
        if text.count(anchor) != 1:
            raise RuntimeError('Unexpected df-pn repetition condition')
        text = text.replace(anchor, anchor + '\n            if (rep != MateRepetitionState::Unknown && Mate::Dfpn::verification_deadline != std::chrono::steady_clock::time_point::max())\n                Mate::Dfpn::verification_repetition_used = true;')
        dfpn.write_text(text)
        changed = True
    usi = source / 'usi.cpp'
    text = usi.read_text(encoding='utf-8-sig')
    if '#include "verified_mate.h"' in text:
        updated = text.replace('<< " score cp " << (n > 0 ? 9999 : -9999)', '<< " score mate " << n')
        if updated != text:
            usi.write_text(updated)
            changed = True
        return changed
    text = text.replace('#include "movegen.h"', '#include "movegen.h"\n#include <memory>\n#include "verified_mate.h"')
    original = '''    engine.set_on_update_full(
      [this](const auto& i) { on_update_full(i /*, engine.get_options()["UCI_ShowWDL"] */); });
    engine.set_on_bestmove([](const auto& bm, const auto& p) { on_bestmove(bm, p); });'''
    replacement = '''    auto mateSession = std::make_shared<SupplementMate::Session>();
    engine.set_on_update_full([mateSession](const auto& i) {
        mateSession->update(i);
        if (i.score.template is<Score::Mate>()) {
            int n = i.score.template get<Score::Mate>().plies;
            sync_cout << "info depth " << i.depth << " multipv " << i.multiPV
                      << " score mate " << n
                      << " nodes " << i.nodes << " pv " << i.pv << sync_endl;
        } else on_update_full(i);
    });
    engine.set_on_bestmove([this, mateSession](const auto& bm, const auto& p) {
        auto verified = mateSession->finish(engine.get_position());
        if (verified.empty()) on_bestmove(bm, p);
        else on_bestmove(verified, "");
    });'''
    if text.count(original) != 1:
        raise RuntimeError('Unexpected USI listeners: cannot install mate verification')
    usi.write_text(text.replace(original, replacement))
    return True


if __name__ == '__main__':
    prepare(Path(sys.argv[1]))
