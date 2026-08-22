package dev.miyado.shogisupplement.ui.common

import kotlinx.coroutines.CoroutineDispatcher

/** Nativeで公開範囲が異なるDispatchers.IOをexpect/actualで分離した既定ディスパッチャ。 */
internal expect val defaultIoDispatcher: CoroutineDispatcher
