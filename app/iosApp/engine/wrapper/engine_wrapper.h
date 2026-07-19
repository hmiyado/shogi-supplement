// YaneuraOu(libyaneuraou.a)をin-process化するためのC APIラッパー。
//
// 方式: engine本体の main() は build_ios.sh で `-Dmain=yaneuraou_main` にリネームしてある。
// これを std::thread 上で起動し、標準入出力(fd0/fd1)をパイプへdup2して差し替えることで
// USIプロトコルの標準入出力ベースの通信をプロセス内で完結させる。
//
// 制約（重要）:
//   dup2(fd0)/dup2(fd1) はプロセス全体のfd 0/1を差し替える（スレッドローカルにできない）。
//   よってshogi_engine_start()呼び出し後、アプリ側のstdout/stdin経由の出力・入力は
//   すべてエンジンのパイプに吸われる。アプリのログはstderr/OSLogを使うこと
//   （このwrapper自身のログもすべてfprintf(stderr, ...)で出す）。
//
// スレッド安全性: 呼び出し側（Swift/Kotlin側）で直列に呼ぶ想定。内部で送信・受信を
// 別々のmutexで保護しているが、複数箇所から同時にsend/read_lineを呼ぶ設計は想定していない。

#ifdef __cplusplus
extern "C" {
#endif

/// エンジンスレッドを起動し、USIループを開始する。プロセス内で一度だけ呼ぶこと。
/// 呼び出し後、fd0/fd1はエンジン用パイプに差し替わる（上記制約参照）。
void shogi_engine_start(void);

/// エンジンへ1行送る（USIコマンド）。内部で改行を1つ付与する。
void shogi_engine_send(const char* line);

/// エンジンから1行読む（改行まで、または timeout_ms 経過まで待つ）。
///
/// @param buf 出力先バッファ（改行・終端NULは含めない文字列を書き込む）
/// @param cap buf の容量（バイト数）
/// @param timeout_ms タイムアウト（ミリ秒）。0以下は待たずに即時判定
/// @return 書き込んだ文字数（0以上）。タイムアウトで1行取得できなければ -1。
///         パイプがクローズ（エンジン終了）していれば -2。
int shogi_engine_read_line(char* buf, int cap, int timeout_ms);

#ifdef __cplusplus
}
#endif
