#!/bin/bash
set -euo pipefail
coproc ENGINE { exec /opt/engine/study/yaneuraou; }
engine_pid=$ENGINE_PID
read_fd=${ENGINE[0]}
write_fd=${ENGINE[1]}
send() { printf '%s\n' "$1" >&"$write_fd"; }
until_line() {
  while IFS= read -r -t 30 -u "$read_fd" line; do
    printf '%s\n' "$line"
    [[ "$line" == "$1"* ]] && return 0
  done
  return 1
}
send usi
until_line usiok
send 'setoption name EvalDir value /opt/engine/study/eval'
send 'setoption name FV_SCALE value 40'
send 'setoption name Threads value 1'
send 'setoption name USI_Hash value 128'
send 'setoption name MultiPV value 3'
send isready
until_line readyok
send usinewgame
send 'position startpos'
send 'go infinite'
until_line 'info depth'
send stop
until_line bestmove
send 'position startpos moves 7g7f 3c3d'
send 'go nodes 400000'
until_line bestmove
cat "/proc/$engine_pid/status" | grep -E '^(VmHWM|VmRSS):'
send quit
wait "$engine_pid"
echo 'ENGINE_STOP_RESUME_OK=true'
