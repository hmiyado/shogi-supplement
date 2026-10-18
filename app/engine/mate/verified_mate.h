#pragma once
#include <algorithm>
#include <chrono>
#include <map>
#include <string>
#include <vector>
#include "mate/mate.h"
#include "movegen.h"
#include "usi.h"

namespace YaneuraOu::SupplementMate {
// A single verification shares its node budget across discovery and all bounds.
struct Result {
    enum Status { Unknown, NoMate, Mate } status = Unknown;
    int plies = 0;
    std::vector<Move> pv;
};

class Verifier {
    Mate::Dfpn::MateDfpnSolver solver{Mate::Dfpn::DfpnSolverType::Node32bit};
    u64 remaining = 100000;
    const std::chrono::steady_clock::time_point deadline =
      std::chrono::steady_clock::now() + std::chrono::milliseconds(500);

    Result attack(Position& pos, int bound) {
        if (!remaining || std::chrono::steady_clock::now() >= deadline) return {};
        solver.set_max_game_ply(bound ? pos.game_ply() + bound - 1 : 0);
        auto previousDeadline = Mate::Dfpn::verification_deadline;
        auto previousRepetition = Mate::Dfpn::verification_repetition_used;
        Mate::Dfpn::verification_repetition_used = false;
        Mate::Dfpn::verification_deadline = deadline;
        auto move = solver.mate_dfpn(pos, remaining);
        Mate::Dfpn::verification_deadline = previousDeadline;
        bool repetitionUsed = Mate::Dfpn::verification_repetition_used;
        Mate::Dfpn::verification_repetition_used = previousRepetition;
        remaining -= std::min(remaining, std::max<u64>(1, solver.get_nodes_searched()));
        if (repetitionUsed || move == Move::none()) return {};
        if (move == Move::null()) return {Result::NoMate, 0, {}};
        auto pv = solver.get_pv();
        if (pv.empty() || (bound && int(pv.size()) > bound)) return {};
        const auto attacker = pos.side_to_move();
        std::vector<StateInfo> states(pv.size());
        size_t made = 0;
        for (auto step : pv) {
            auto legal = USIEngine::to_move(pos, USIEngine::move(step));
            if (legal != step || (pos.side_to_move() == attacker && !pos.gives_check(step))) break;
            pos.do_move(step, states[made++]);
        }
        bool terminal = made == pv.size() && pos.side_to_move() != attacker
                        && pos.in_check() && MoveList<LEGAL_ALL>(pos).size() == 0;
        while (made) pos.undo_move(pv[--made]);
        if (!terminal) return {};
        return {Result::Mate, int(pv.size()), pv};
    }

    Result bounded(Position& pos, bool defending, int bound) {
        if (!defending) return attack(pos, bound);
        // A negative search score without check can describe a threat, not mate.
        if (!pos.in_check()) return {};
        MoveList<LEGAL_ALL> replies(pos);
        if (replies.size() == 0) return {Result::Mate, 0, {}};
        Result longest{Result::Mate, 0, {}};
        for (auto reply : replies) {
            StateInfo state;
            pos.do_move(reply, state);
            auto result = attack(pos, bound ? bound - 1 : 0);
            pos.undo_move(reply);
            if (result.status != Result::Mate) return result;
            if (int(result.pv.size()) + 1 > longest.plies) {
                longest.plies = int(result.pv.size()) + 1;
                longest.pv = result.pv;
                longest.pv.insert(longest.pv.begin(), reply);
            }
        }
        return longest;
    }

public:
    Verifier() { solver.alloc(16); }

    Result shortest(Position& pos, bool defending) {
        if (!pos.pieces(BLACK, KING) || !pos.pieces(WHITE, KING)) return {};
        auto found = bounded(pos, defending, 0);
        if (found.status != Result::Mate || found.plies == 0) return found;
        const int offset = defending ? 2 : 1;
        const int cap = (63 - offset) / 2;
        int lower = -1;
        int upper = std::min(cap, std::max(0, (found.plies - offset + 1) / 2));
        auto proof = bounded(pos, defending, 2 * upper + offset);
        while (proof.status != Result::Mate) {
            if (proof.status == Result::Unknown || upper == cap) return {};
            lower = upper;
            upper = std::min(cap, 2 * upper + 1);
            proof = bounded(pos, defending, 2 * upper + offset);
        }
        while (upper - lower > 1) {
            int middle = (upper + lower) / 2;
            auto result = bounded(pos, defending, 2 * middle + offset);
            if (result.status == Result::Unknown) return {};
            if (result.status == Result::Mate) {
                upper = middle;
                proof = result;
            } else lower = middle;
        }
        proof.plies = 2 * upper + offset;
        return proof;
    }
};

struct Entry {
    int mate;
    int depth;
    size_t nodes;
    std::string pv;
};

class Session {
    std::map<size_t, Entry> pending;
public:
    void update(const Search::InfoFull& info) {
        if (info.score.is<Score::Mate>())
            pending[info.multiPV] = {info.score.get<Score::Mate>().plies, info.depth,
                                     info.nodes, std::string(info.pv)};
        else pending.erase(info.multiPV);
    }

    std::string finish(Position& pos) {
        std::string best;
        if (pending.empty()) return best;
        Verifier verifier;
        for (const auto& [multi, entry] : pending) {
            Result result;
            // PV1 describes the whole position. Other positive PVs fix the first move.
            if (multi == 1) result = verifier.shortest(pos, entry.mate <= 0);
            else if (entry.mate > 0) {
                auto first = USIEngine::to_move(pos, entry.pv.substr(0, entry.pv.find(' ')));
                if (first != Move::none() && pos.legal(first) && pos.gives_check(first)) {
                    StateInfo state;
                    pos.do_move(first, state);
                    result = verifier.shortest(pos, true);
                    pos.undo_move(first);
                    if (result.status == Result::Mate) {
                        ++result.plies;
                        result.pv.insert(result.pv.begin(), first);
                    }
                }
            }
            if (result.status != Result::Mate) continue;
            std::string pv;
            for (auto move : result.pv) {
                if (!pv.empty()) pv += ' ';
                pv += USIEngine::move(move);
            }
            int signedPlies = entry.mate <= 0 ? -result.plies : result.plies;
            sync_cout << "info depth " << entry.depth << " multipv " << multi
                      << " score mate " << signedPlies << " mateverified nodes " << entry.nodes
                      << " pv " << pv << sync_endl;
            if (multi == 1 && !result.pv.empty()) best = USIEngine::move(result.pv.front());
        }
        pending.clear();
        return best;
    }
};
}
