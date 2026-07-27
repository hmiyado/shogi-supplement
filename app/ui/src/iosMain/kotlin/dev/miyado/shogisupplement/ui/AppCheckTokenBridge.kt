package dev.miyado.shogisupplement.ui

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firebase App CheckトークンのSwift⇄Kotlin橋渡し（匿名アカウント量産への防御）。
 *
 * App Check SDK自体（FirebaseApp.configure()・プロバイダファクトリ設定・
 * `AppCheck.appCheck().token(forcingRefresh:completion:)`の呼び出し）はSwift側
 * （iosApp/iosApp/IosFirebaseAppCheck.swift）が担う。Kotlin/Native ⇄ Swift の境界は
 * [IosFileImportBridge] と同じく「プレーンな関数呼び出し」と「クロージャ型プロパティへの
 * 代入」のみで構成する（同ファイルKDoc参照。SKIE等は未導入）。
 *
 * 起動時の配線: [IosFirebaseAppCheck.configureIfAvailable]（plistがバンドルに
 * あるときのみ）が [tokenHandler] へcompletion形式のクロージャを代入する。
 * `local/GoogleService-Info.plist` 未同梱ビルドでは代入自体が起きないため
 * [tokenHandler] は null のままで、[getToken] は常に null を返す
 * （呼び出し側の [dev.miyado.shogisupplement.engine.RemoteAnalysisRunner] は
 * ヘッダを付けずに送るだけ＝サーバー側の段階導入と同じくgraceful degradation）。
 */
object AppCheckTokenBridge {

    /**
     * Swift側（IosFirebaseAppCheck.configureIfAvailable）が起動時に代入する、
     * 「App Checkトークンを取得する」実処理へのクロージャ。completionはメインスレッド以外
     * （SDK内部のキューやディスク書き込みが絡む）から呼ばれる可能性があるため、
     * [getToken] 側はスレッドを仮定せず [suspendCancellableCoroutine] で受け取る。
     */
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
