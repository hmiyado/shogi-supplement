package dev.miyado.shogisupplement.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val authIoDispatcher: CoroutineDispatcher = Dispatchers.Default
