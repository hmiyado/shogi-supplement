# syntax=docker/dockerfile:1
#
# analysis-worker（Cloud Run）用Dockerfile。
#
# ビルドコンテキストは :shared に依存するため `app/` ディレクトリを想定する:
#   cd app && docker build -f server/worker/Dockerfile -t analysis-worker .

# ── build stage ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# :server:worker は :shared のjvmターゲットに依存するため単独ディレクトリでは完結しない。
COPY . .

RUN ./gradlew :server:worker:installDist --no-daemon

# ── runtime stage ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

COPY --from=build /workspace/server/worker/build/install/worker /app

# /opt/engine はエンジンバイナリの配置先として空で用意する。パスはConfig.ktの既定値
# （ENGINE_PATH / ENGINE_EVAL_DIR）と一致させてある。
RUN mkdir -p /opt/engine

# Cloud RunはPORTを注入する。ENGINE_REV / ENGINE_EVAL_SHA256 は未設定では起動時にfailする
# （WorkerConfig.fromEnv() 参照）。機微情報ではないためSecret Managerではなくビルド引数で渡す想定。
ENV PORT=8080
ENV ENGINE_PATH=/opt/engine/yaneuraou
ENV ENGINE_EVAL_DIR=/opt/engine/eval_hao
ENV ENGINE_REV=TODO-unset
ENV ENGINE_EVAL_SHA256=TODO-unset

# 機微情報（SUPABASE_SERVICE_ROLE_KEY等）は実行時にCloud Run側でSecret Managerから注入する。
# イメージには焼き込まない。

EXPOSE 8080
ENTRYPOINT ["/app/bin/worker"]
