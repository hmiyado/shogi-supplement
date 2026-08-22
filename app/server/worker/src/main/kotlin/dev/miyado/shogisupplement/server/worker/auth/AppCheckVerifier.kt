package dev.miyado.shogisupplement.server.worker.auth

sealed class AppCheckResult {
    data object Valid : AppCheckResult()
    data class Invalid(val reason: String) : AppCheckResult()
}

// AppCheck検証はnullableで無効化でき、テストではこのインターフェースをfakeへ差し替える。
interface AppCheckVerifier {
    suspend fun verify(token: String): AppCheckResult
}
