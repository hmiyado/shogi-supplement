package dev.miyado.shogisupplement.auth

import org.jetbrains.compose.resources.getString
import dev.miyado.shogisupplement.ui.generated.resources.*
object AuthErrorMapper {
    suspend fun mapSignInAnonymouslyError(throwable: Throwable): String {
        val msg = throwable.message?.lowercase() ?: ""
        return when {
            isNetworkError(throwable, msg) -> getString(Res.string.auth_error_network)
            else -> getString(Res.string.auth_error_anon_sign_in_generic)
        }
    }
    suspend fun mapDeleteAccountError(throwable: Throwable): String {
        val msg = throwable.message?.lowercase() ?: ""
        return when {
            isNetworkError(throwable, msg) -> getString(Res.string.auth_error_network)
            else -> getString(Res.string.auth_error_delete_generic)
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
