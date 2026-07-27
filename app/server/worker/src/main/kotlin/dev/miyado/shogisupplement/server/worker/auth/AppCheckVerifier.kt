package dev.miyado.shogisupplement.server.worker.auth

sealed class AppCheckResult {
    data object Valid : AppCheckResult()
    data class Invalid(val reason: String) : AppCheckResult()
}

// 本番実装は [FirebaseAppCheckVerifier]。[AnalysisService] は
// nullable（呼び出し側がnullを渡す＝FIREBASE_PROJECT_NUMBER未設定）で段階導入を表現する。
// テストはこのインターフェースを直接フェイクで差し替える。
interface AppCheckVerifier {
    suspend fun verify(token: String): AppCheckResult
}
