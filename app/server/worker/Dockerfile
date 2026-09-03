# syntax=docker/dockerfile:1
#
# analysis-worker（Cloud Run）用Dockerfile。
#
# ビルドコンテキストは他モジュールに依存するため `app/` ディレクトリを想定する:
#   cd app && docker build -f server/worker/Dockerfile -t analysis-worker .
#
# エンジン（やねうら王 + Háo評価関数）の取り込みは engine-build / eval-fetch の
# 2ステージで行う。ビルドスクリプトは server/worker/engine/ に分離している:
#   engine/compile.sh         やねうら王(NNUE, v7.00)をAVX2向けにコンパイル
#   engine/fetch_hao_eval.sh  Háo評価関数を取得しSHA-256照合
# ステージを分けているのは、ソース取得元(ENGINE_REV)とHáoの版(ENGINE_EVAL_SHA256)が
# 独立して変わりうるため（片方だけ更新したときに他方の層まで再ビルドさせないため）。

ARG ENGINE_REV=v7.00
ARG ENGINE_EVAL_SHA256=1141d275bceec911156801f27303dc9ff5beb24f4f59144cc069306c59e80782
ARG SIMD_TARGET=avx2

# ── engine build stage（やねうら王バイナリ）───────────────────────────────
# AVX2はx86命令拡張のため linux/amd64 を明示する（Cloud Runの実行環境と一致させる）。
FROM --platform=linux/amd64 debian:bookworm-slim AS engine-build

RUN apt-get update && apt-get install -y --no-install-recommends \
      clang git ca-certificates file \
    && rm -rf /var/lib/apt/lists/*

ARG ENGINE_REV
RUN git clone --branch "${ENGINE_REV}" --depth 1 \
      https://github.com/yaneurao/YaneuraOu.git /src/upstream

COPY server/worker/engine/compile.sh /src/compile.sh
RUN chmod +x /src/compile.sh

ARG SIMD_TARGET
RUN mkdir -p /out && /src/compile.sh "${SIMD_TARGET}" /src/upstream/source /out

# ── eval fetch stage（Háo評価関数）──────────────────────────────────────
FROM debian:bookworm-slim AS eval-fetch

RUN apt-get update && apt-get install -y --no-install-recommends \
      curl ca-certificates p7zip-full \
    && rm -rf /var/lib/apt/lists/*

COPY server/worker/engine/fetch_hao_eval.sh /src/fetch_hao_eval.sh
RUN chmod +x /src/fetch_hao_eval.sh

ARG ENGINE_EVAL_SHA256
RUN /src/fetch_hao_eval.sh "${ENGINE_EVAL_SHA256}" /out

# ── worker jar build stage ───────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# :server:worker は :contracts / :analysis / :engine:subprocess のjvmターゲットに依存するため
# 単独ディレクトリでは完結しない。
COPY . .

RUN ./gradlew :server:worker:installDist --no-daemon

# ── runtime stage ────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/server/worker/build/install/worker /app

# エンジンバイナリ・評価関数の配置先。パスはConfig.ktの既定値
# （ENGINE_PATH / ENGINE_EVAL_DIR）と一致させてある。nn.binのファイル名はやねうら王の
# EvalDirオプション既定のロード対象名なので固定（UsiEngineSubprocess参照）。
ARG SIMD_TARGET
COPY --from=engine-build /out/YaneuraOu-NNUE-linux-${SIMD_TARGET} /opt/engine/yaneuraou
COPY --from=eval-fetch /out/nn.bin /opt/engine/eval_hao/nn.bin
COPY --from=eval-fetch /out/gpl-3.0.txt /opt/engine/eval_hao/gpl-3.0.txt
RUN chmod +x /opt/engine/yaneuraou

# Cloud RunはPORTを注入する。ENGINE_REV / ENGINE_EVAL_SHA256 は未設定では起動時にfailする
# （WorkerConfig.fromEnv() 参照）。機微情報ではないためSecret Managerではなくビルド引数で渡し、
# 実際に使ったビルド引数の値をそのままイメージのENVへ焼き込む（engine_metaとして
# 解析結果に記録される値の来歴を、イメージ自身から追えるようにするため）。
ARG ENGINE_REV
ARG ENGINE_EVAL_SHA256
ENV PORT=8080
ENV ENGINE_PATH=/opt/engine/yaneuraou
ENV ENGINE_EVAL_DIR=/opt/engine/eval_hao
ENV ENGINE_REV=${ENGINE_REV}
ENV ENGINE_EVAL_SHA256=${ENGINE_EVAL_SHA256}

# 機微情報（SUPABASE_SERVICE_ROLE_KEY等）は実行時にCloud Run側でSecret Managerから注入する。
# イメージには焼き込まない。

EXPOSE 8080
ENTRYPOINT ["/app/bin/worker"]
