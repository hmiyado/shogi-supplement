package dev.miyado.shogisupplement.util

actual fun currentEpochSeconds(): Long = (kotlin.js.Date().getTime() / 1000.0).toLong()
