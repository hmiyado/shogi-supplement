package dev.miyado.shogisupplement.server.worker

import dev.miyado.shogisupplement.api.analysis.EngineMetaJson
import dev.miyado.shogisupplement.api.analysis.engineConditionName
import dev.miyado.shogisupplement.engine.Engine
import dev.miyado.shogisupplement.engine.EngineInvariants
import java.io.File
import java.security.MessageDigest

data class StudyEngineConfig(
    val enginePath: String,
    val evalDir: String,
    val engineRev: String,
    val evalSha256: String,
    val fvScale: Int,
) {
    init {
        require(enginePath.isNotBlank() && evalDir.isNotBlank() && engineRev.isNotBlank())
        require(evalSha256.matches(Regex("[0-9a-f]{64}")))
        require(fvScale > 0)
    }

    fun verifyEvaluation() {
        val digest = MessageDigest.getInstance("SHA-256")
        File(evalDir, "nn.bin").inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == evalSha256) { "Study evaluation SHA-256 does not match configured provenance" }
    }

    companion object {
        fun fromEnv(env: (String) -> String?): StudyEngineConfig? {
            val names = listOf("STUDY_ENGINE_PATH", "STUDY_ENGINE_EVAL_DIR", "STUDY_ENGINE_REV",
                "STUDY_ENGINE_EVAL_SHA256", "STUDY_ENGINE_FV_SCALE")
            val values = names.map { env(it)?.takeIf(String::isNotBlank) }
            if (values.all { it == null }) return null
            require(values.all { it != null }) { "All STUDY_ENGINE settings must be provided together" }
            return StudyEngineConfig(values[0]!!, values[1]!!, values[2]!!, values[3]!!, values[4]!!.toInt())
        }
    }
}

class StudyEngineProfile(
    val config: StudyEngineConfig,
    private val factory: (StudyEngineConfig) -> Engine,
) {
    fun create(): Engine = factory(config)

    val cacheKeyPrefix: String = "study|${config.engineRev}|${config.evalSha256}" +
        "|nodes=${EngineInvariants.NODES}|threads=${EngineInvariants.THREADS}" +
        "|usi_hash=${EngineInvariants.USI_HASH_MB}|fv_scale=${config.fvScale}"

    fun meta(multiPv: Int) = EngineMetaJson(
        engineRev = config.engineRev,
        evalSha256 = config.evalSha256,
        nodes = EngineInvariants.NODES,
        threads = EngineInvariants.THREADS,
        multiPv = multiPv,
        usiHash = EngineInvariants.USI_HASH_MB,
        fvScale = config.fvScale,
        conditionName = engineConditionName(config.engineRev, config.evalSha256,
            EngineInvariants.NODES, EngineInvariants.THREADS, multiPv,
            EngineInvariants.USI_HASH_MB, config.fvScale),
    )
}
