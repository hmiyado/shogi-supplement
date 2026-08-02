package dev.miyado.shogisupplement.util

import android.util.Log

actual object Logger {
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
