package dev.miyado.shogisupplement.util

actual object Logger {
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        console.error("E/$tag: $message")
        throwable?.let { console.error(it.stackTraceToString()) }
    }
}
