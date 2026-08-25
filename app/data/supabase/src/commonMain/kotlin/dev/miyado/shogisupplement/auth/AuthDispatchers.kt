package dev.miyado.shogisupplement.auth

import kotlinx.coroutines.CoroutineDispatcher

/** Nativeで公開範囲が異なるDispatchers.IOをexpect/actualで分離した認証用dispatcher。 */
internal expect val authIoDispatcher: CoroutineDispatcher
