package dev.miyado.shogisupplement.server.worker

data class WorkerConfig(
    val port: Int,
    val supabaseUrl: String,
    val supabaseServiceRoleKey: String,
    val supabaseJwksUrl: String,
    val supabaseJwtIssuer: String,
    val enginePath: String,
    val engineEvalDir: String,
    val engineRev: String,
    val evalSha256: String,
    val analysisWorkers: Int,
    val isolatePositions: Boolean,
    val analysisPositionDailyLimit: Int,
    val staleRunningTimeoutMs: Long,
    // 空文字列はApp Check検証を無効化し、段階導入を可能にする。
    val firebaseProjectNumber: String,
    val transferRateLimitPerMinute: Int,
    // ローカルのdocs/mypage.html（python http.server等）からの動作確認用。
    // 本番のCloud Run環境変数には設定しないため既定false。
    val allowLocalhostCors: Boolean,
) {
    companion object {
        fun fromEnv(env: (String) -> String? = System::getenv): WorkerConfig {
            val supabaseUrl = requireEnv(env, "SUPABASE_URL").trimEnd('/')
            return WorkerConfig(
                port = (env("PORT") ?: "8080").toInt(),
                supabaseUrl = supabaseUrl,
                supabaseServiceRoleKey = requireEnv(env, "SUPABASE_SERVICE_ROLE_KEY"),
                supabaseJwksUrl = env("SUPABASE_JWKS_URL") ?: "$supabaseUrl/auth/v1/.well-known/jwks.json",
                supabaseJwtIssuer = env("SUPABASE_JWT_ISSUER") ?: "$supabaseUrl/auth/v1",
                enginePath = env("ENGINE_PATH") ?: "/opt/engine/yaneuraou",
                engineEvalDir = env("ENGINE_EVAL_DIR") ?: "/opt/engine/eval_hao",
                engineRev = requireEnv(env, "ENGINE_REV"),
                evalSha256 = requireEnv(env, "ENGINE_EVAL_SHA256"),
                analysisWorkers = (env("ANALYSIS_WORKERS") ?: "1").toInt(),
                // 局面ごとに置換表をクリアし、解析順と並列度への依存を避ける。
                isolatePositions = (env("ANALYSIS_ISOLATE_POSITIONS") ?: "true").toBooleanStrict(),
                analysisPositionDailyLimit = (env("ANALYSIS_POSITION_DAILY_LIMIT") ?: "100").toInt(),
                // 切断後に終了処理へ到達しないrunning行を、解析時間より長い閾値で検知する。
                staleRunningTimeoutMs = (env("STALE_RUNNING_TIMEOUT_MS") ?: "600000").toLong(),
                firebaseProjectNumber = env("FIREBASE_PROJECT_NUMBER") ?: "",
                transferRateLimitPerMinute = (env("TRANSFER_RATE_LIMIT_PER_MINUTE") ?: "5").toInt(),
                allowLocalhostCors = (env("ALLOW_LOCALHOST_CORS") ?: "false").toBooleanStrict(),
            )
        }

        private fun requireEnv(env: (String) -> String?, name: String): String =
            env(name)?.takeIf { it.isNotBlank() }
                ?: error("environment variable $name is required")
    }
}
