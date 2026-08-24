package dev.miyado.shogisupplement.auth

import dev.miyado.shogisupplement.text.AppStrings

/**
 * 認証エラーを日本語メッセージにマッピングする。
 * 匿名認証（v1）のエラーのみを扱う。
 * 生のエラーメッセージは呼び出し元（AccountViewModel）で Logcat に出す。
 */
object AuthErrorMapper {

    /** 匿名サインインエラーのマッピング（ネットワーク系 + 汎用フォールバック）。 */
    fun mapSignInAnonymouslyError(throwable: Throwable): String {
        val msg = throwable.message?.lowercase() ?: ""
        return when {
            isNetworkError(throwable, msg) -> AppStrings.AUTH_ERROR_NETWORK
            else -> AppStrings.AUTH_ERROR_ANON_SIGN_IN_GENERIC
        }
    }

    /** アカウント削除エラーのマッピング（ネットワーク系 + 汎用フォールバック）。 */
    fun mapDeleteAccountError(throwable: Throwable): String {
        val msg = throwable.message?.lowercase() ?: ""
        return when {
            isNetworkError(throwable, msg) -> AppStrings.AUTH_ERROR_NETWORK
            else -> AppStrings.AUTH_ERROR_DELETE_GENERIC
        }
    }

    private fun isNetworkError(throwable: Throwable, msg: String): Boolean {
        if (msg.contains("unable to resolve host") ||
            msg.contains("network") ||
            msg.contains("connection") ||
            msg.contains("timeout")) return true
        val className = throwable::class.simpleName?.lowercase() ?: ""
        val causeClassName = throwable.cause?.let { it::class.simpleName?.lowercase() } ?: ""
        return className.contains("network") ||
            className.contains("connect") ||
            className.contains("socket") ||
            causeClassName.contains("network") ||
            causeClassName.contains("connect")
    }
}
