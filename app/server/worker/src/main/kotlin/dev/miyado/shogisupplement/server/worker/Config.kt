package dev.miyado.shogisupplement.server.worker

// すべて環境変数から読む。機微情報の注入経路はDockerfile参照。
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
) {
    companion object {
        fun fromEnv(env: (String) -> String? = System::getenv): WorkerConfig {
            val supabaseUrl = requireEnv(env, "SUPABASE_URL").trimEnd('/')
            return WorkerConfig(
                // Cloud RunはPORTを注入する（既定8080）。
                port = (env("PORT") ?: "8080").toInt(),
                supabaseUrl = supabaseUrl,
                supabaseServiceRoleKey = requireEnv(env, "SUPABASE_SERVICE_ROLE_KEY"),
                supabaseJwksUrl = env("SUPABASE_JWKS_URL") ?: "$supabaseUrl/auth/v1/.well-known/jwks.json",
                supabaseJwtIssuer = env("SUPABASE_JWT_ISSUER") ?: "$supabaseUrl/auth/v1",
                enginePath = env("ENGINE_PATH") ?: "/opt/engine/yaneuraou",
                engineEvalDir = env("ENGINE_EVAL_DIR") ?: "/opt/engine/eval_hao",
                engineRev = requireEnv(env, "ENGINE_REV"),
                evalSha256 = requireEnv(env, "ENGINE_EVAL_SHA256"),
                // 1局面ずつ解析するエンジンプロセスの本数。CPU割り当てと揃えること
                // （エンジンはThreads=1なので、割り当てを超えるとタイムシェアするだけで速くならない）。
                analysisWorkers = (env("ANALYSIS_WORKERS") ?: "1").toInt(),
            )
        }

        private fun requireEnv(env: (String) -> String?, name: String): String =
            env(name)?.takeIf { it.isNotBlank() }
                ?: error("environment variable $name is required")
    }
}
