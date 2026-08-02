package dev.miyado.shogisupplement.util

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L
