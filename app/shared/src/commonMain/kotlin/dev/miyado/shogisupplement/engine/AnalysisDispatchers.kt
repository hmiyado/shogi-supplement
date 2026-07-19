package dev.miyado.shogisupplement.engine

import kotlinx.coroutines.CoroutineDispatcher

/**
 * [AnalysisRunner] のワーカー実行向け既定ディスパッチャ。
 *
 * kotlinx.coroutines の Native向けAPIでは `Dispatchers.IO` が internal
 * （公開APIとして露出していない）ため、expect/actual で分離する
 * （:ui の ViewModelDispatchers.kt と同じパターン）:
 * - Android: [kotlinx.coroutines.Dispatchers.IO]
 * - iOS: [kotlinx.coroutines.Dispatchers.Default]
 */
internal expect val analysisIoDispatcher: CoroutineDispatcher
