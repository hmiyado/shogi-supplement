package dev.miyado.shogisupplement.auth

import kotlinx.coroutines.CoroutineDispatcher

/**
 * SupabaseAuthRepository のセッション監視スコープ用ディスパッチャ。
 *
 * kotlinx.coroutines は Kotlin/Native では `Dispatchers.IO` を公開APIとして
 * 露出していないため、expect/actual で分離する（:shared の engine/AnalysisDispatchers.kt・
 * :ui の ViewModelDispatchers.kt と同じパターン）:
 * - Android/JVM: [kotlinx.coroutines.Dispatchers.IO]
 * - iOS: [kotlinx.coroutines.Dispatchers.Default]
 */
internal expect val authIoDispatcher: CoroutineDispatcher
