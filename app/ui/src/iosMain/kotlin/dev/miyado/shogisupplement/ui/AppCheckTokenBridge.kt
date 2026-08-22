package dev.miyado.shogisupplement.ui

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firebase App CheckのSwift/Kotlin橋渡し。
 * SDK設定はSwift側が担い、plistがない場合はトークンなしで動作する。
 * Kotlin側はクロージャを受け取り、取得失敗時もnullを返す。
 */
object AppCheckTokenBridge {

    /** Swiftから渡されるトークン取得クロージャ。completionのスレッドは仮定しない。 */
    var tokenHandler: ((completion: (String?) -> Unit) -> Unit)? = null

    /**
     * [dev.miyado.shogisupplement.engine.RemoteAnalysisRunner.appCheckTokenProvider] に
     * そのまま渡せる suspend 関数。[tokenHandler] 未設定（plist無しビルド）なら
     * コルーチンに入らず即座に null を返す。
     */
    suspend fun getToken(): String? {
        val handler = tokenHandler ?: return null
        return suspendCancellableCoroutine { cont ->
            handler { token -> cont.resume(token) }
        }
    }
}
