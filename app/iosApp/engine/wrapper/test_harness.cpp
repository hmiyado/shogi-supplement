// engine_wrapper.h の疎通確認用の使い捨てハーネス（コミットしない・Xcode組込み前の検証専用）。
// stdoutはエンジンに奪われるのでログはすべてstderrへ出す。
#include "engine_wrapper.h"
#include <chrono>
#include <cstdio>
#include <string>
#include <thread>

int main() {
    fprintf(stderr, "[harness] starting engine...\n");
    shogi_engine_start();

    shogi_engine_send("usi");
    char buf[8192];
    bool usiok = false;
    auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
    while (std::chrono::steady_clock::now() < deadline) {
        int n = shogi_engine_read_line(buf, sizeof(buf), 500);
        if (n >= 0) {
            fprintf(stderr, "[engine] %s\n", buf);
            if (std::string(buf) == "usiok") { usiok = true; break; }
        } else if (n == -2) {
            fprintf(stderr, "[harness] engine closed pipe\n");
            return 1;
        }
    }
    fprintf(stderr, "[harness] usiok=%d\n", usiok);
    if (!usiok) return 1;

    const char* evalDir = std::getenv("EVAL_DIR");
    std::string setEvalDir = std::string("setoption name EvalDir value ") + (evalDir ? evalDir : "");

    const char* opts[] = {
        "setoption name Threads value 1",
        "setoption name USI_Hash value 128",
        "setoption name MultiPV value 2",
        "setoption name USI_OwnBook value false",
        "setoption name NetworkDelay value 0",
        "setoption name NetworkDelay2 value 0",
        "setoption name FV_SCALE value 20",
    };
    for (auto* o : opts) shogi_engine_send(o);
    if (evalDir) shogi_engine_send(setEvalDir.c_str());

    shogi_engine_send("isready");
    bool readyok = false;
    deadline = std::chrono::steady_clock::now() + std::chrono::seconds(30);
    while (std::chrono::steady_clock::now() < deadline) {
        int n = shogi_engine_read_line(buf, sizeof(buf), 500);
        if (n >= 0) {
            fprintf(stderr, "[engine] %s\n", buf);
            if (std::string(buf) == "readyok") { readyok = true; break; }
        }
    }
    fprintf(stderr, "[harness] readyok=%d\n", readyok);
    if (!readyok) return 1;

    shogi_engine_send("usinewgame");
    shogi_engine_send("position startpos");
    auto t0 = std::chrono::steady_clock::now();
    shogi_engine_send("go nodes 400000");

    bool bestmoveSeen = false;
    std::string lastInfo;
    deadline = std::chrono::steady_clock::now() + std::chrono::seconds(60);
    while (std::chrono::steady_clock::now() < deadline) {
        int n = shogi_engine_read_line(buf, sizeof(buf), 500);
        if (n >= 0) {
            std::string line(buf);
            fprintf(stderr, "[engine] %s\n", buf);
            if (line.rfind("info", 0) == 0) lastInfo = line;
            if (line.rfind("bestmove", 0) == 0) { bestmoveSeen = true; break; }
        }
    }
    auto t1 = std::chrono::steady_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();
    fprintf(stderr, "[harness] bestmove_seen=%d elapsed_ms=%lld\n", bestmoveSeen, (long long)ms);
    fprintf(stderr, "[harness] last_info=%s\n", lastInfo.c_str());

    shogi_engine_send("quit");
    return bestmoveSeen ? 0 : 1;
}
