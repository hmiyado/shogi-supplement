// engine_wrapper.h の実装。
#include "engine_wrapper.h"

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>

#include <poll.h>
#include <unistd.h>

// YaneuraOu本体の main()。build_ios.sh で -Dmain=yaneuraou_main にリネームしてリンクしている。
extern int yaneuraou_main(int argc, char* argv[]);

namespace {

// アプリ→エンジン（エンジンのstdinになる）
int g_toEngineWriteFd = -1;
// エンジン→アプリ（エンジンのstdoutになる）
int g_fromEngineReadFd = -1;

std::mutex g_sendMutex;
std::mutex g_readMutex;

// shogi_engine_read_line の呼び出しをまたいで残る受信済みバイト（改行未到達分）。
std::string g_readBuffer;

bool g_started = false;
std::mutex g_startMutex;

void runEngineThread() {
    // yaneuraou_main はUSIループを回し続ける（quit コマンドで返る想定）。
    char progName[] = "yaneuraou";
    char* argv[] = {progName, nullptr};
    yaneuraou_main(1, argv);
    fprintf(stderr, "[shogi_engine] yaneuraou_main returned (engine thread exiting)\n");
}

}  // namespace

void shogi_engine_start(void) {
    std::lock_guard<std::mutex> startLock(g_startMutex);
    if (g_started) {
        fprintf(stderr, "[shogi_engine] shogi_engine_start() called twice; ignoring\n");
        return;
    }

    int toEngine[2];   // toEngine[0]=read(engine stdin) / toEngine[1]=write(app側)
    int fromEngine[2]; // fromEngine[0]=read(app側) / fromEngine[1]=write(engine stdout)
    if (pipe(toEngine) != 0 || pipe(fromEngine) != 0) {
        fprintf(stderr, "[shogi_engine] pipe() failed: %s\n", strerror(errno));
        return;
    }

    // 制約: dup2はプロセス全体のfd 0/1を差し替える（スレッドローカルにはできない）。
    // 以降、アプリ側のstdout/stdin経由の入出力はすべてこのパイプに吸われる。
    // アプリ側ログはこのファイル内も含めてstderr（またはOSLog）を使うこと。
    if (dup2(toEngine[0], STDIN_FILENO) < 0) {
        fprintf(stderr, "[shogi_engine] dup2(stdin) failed: %s\n", strerror(errno));
        return;
    }
    if (dup2(fromEngine[1], STDOUT_FILENO) < 0) {
        fprintf(stderr, "[shogi_engine] dup2(stdout) failed: %s\n", strerror(errno));
        return;
    }
    // dup2先に複製済みなので元のfdは閉じる。
    close(toEngine[0]);
    close(fromEngine[1]);

    g_toEngineWriteFd = toEngine[1];
    g_fromEngineReadFd = fromEngine[0];

    std::thread(runEngineThread).detach();
    g_started = true;
    fprintf(stderr, "[shogi_engine] engine thread started\n");
}

void shogi_engine_send(const char* line) {
    if (g_toEngineWriteFd < 0) {
        fprintf(stderr, "[shogi_engine] send() before start(); ignored: %s\n", line ? line : "(null)");
        return;
    }
    std::lock_guard<std::mutex> lock(g_sendMutex);
    std::string withNewline(line ? line : "");
    withNewline.push_back('\n');
    size_t written = 0;
    while (written < withNewline.size()) {
        ssize_t n = write(g_toEngineWriteFd, withNewline.data() + written, withNewline.size() - written);
        if (n < 0) {
            if (errno == EINTR) continue;
            fprintf(stderr, "[shogi_engine] write() failed: %s\n", strerror(errno));
            return;
        }
        written += static_cast<size_t>(n);
    }
}

int shogi_engine_read_line(char* buf, int cap, int timeout_ms) {
    if (g_fromEngineReadFd < 0 || buf == nullptr || cap <= 0) {
        return -1;
    }
    std::lock_guard<std::mutex> lock(g_readMutex);

    for (;;) {
        // 既にバッファに改行があれば即返す。
        size_t nl = g_readBuffer.find('\n');
        if (nl != std::string::npos) {
            std::string lineStr = g_readBuffer.substr(0, nl);
            g_readBuffer.erase(0, nl + 1);
            int len = static_cast<int>(lineStr.size());
            if (len >= cap) len = cap - 1;
            memcpy(buf, lineStr.data(), static_cast<size_t>(len));
            buf[len] = '\0';
            return len;
        }

        struct pollfd pfd;
        pfd.fd = g_fromEngineReadFd;
        pfd.events = POLLIN;
        pfd.revents = 0;
        int rc = poll(&pfd, 1, timeout_ms);
        if (rc == 0) {
            // タイムアウト。改行未到達分はバッファに残したまま次回呼び出しへ持ち越す。
            return -1;
        }
        if (rc < 0) {
            if (errno == EINTR) continue;
            fprintf(stderr, "[shogi_engine] poll() failed: %s\n", strerror(errno));
            return -1;
        }
        if (pfd.revents & (POLLIN | POLLHUP)) {
            char chunk[4096];
            ssize_t n = read(g_fromEngineReadFd, chunk, sizeof(chunk));
            if (n < 0) {
                if (errno == EINTR) continue;
                fprintf(stderr, "[shogi_engine] read() failed: %s\n", strerror(errno));
                return -1;
            }
            if (n == 0) {
                // EOF: エンジン側がstdoutをクローズ（終了）。
                return -2;
            }
            g_readBuffer.append(chunk, static_cast<size_t>(n));
            // ループ先頭に戻って改行判定へ。
        }
    }
}
