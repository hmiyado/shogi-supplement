package dev.miyado.shogisupplement.ui.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * ViewModel（AccountViewModel/DrillViewModel）のDB/エンジン処理向け既定ディスパッチャ。
 *
 * kotlinx.coroutines 1.10.2のNative向けAPIでは `Dispatchers.IO` が internal（公開APIとして
 * 露出していない）ため、commonMainのデフォルト引数に直接書けない。expect/actualで
 * プラットフォームごとの適切な既定値に分離している:
 * - Android: [kotlinx.coroutines.Dispatchers.IO]
 * - iOS: [kotlinx.coroutines.Dispatchers.Default]（NativeでのIOバウンド処理の一般的な代替）
 */
internal expect val defaultIoDispatcher: CoroutineDispatcher
