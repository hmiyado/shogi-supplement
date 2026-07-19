package dev.miyado.shogisupplement.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val analysisIoDispatcher: CoroutineDispatcher = Dispatchers.IO
