package dev.miyado.shogisupplement.engine

import kotlinx.coroutines.CoroutineDispatcher

/** Nativeで公開範囲が異なるDispatchers.IOをexpect/actualで分離した解析用dispatcher。 */
internal expect val analysisIoDispatcher: CoroutineDispatcher
