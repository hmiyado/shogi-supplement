package dev.miyado.shogisupplement.util

actual object Logger {
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        System.err.println("E/$tag: $message")
        throwable?.printStackTrace(System.err)
    }
}
