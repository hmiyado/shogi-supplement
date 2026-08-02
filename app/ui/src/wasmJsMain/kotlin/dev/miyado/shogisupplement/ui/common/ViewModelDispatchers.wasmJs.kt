package dev.miyado.shogisupplement.ui.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Web(wasmJs)にはAndroid/iOSのような専用IOディスパッチャがないため、iOSと同じくDefaultを使う
// （ブラウザはシングルスレッドで、DB/ネットワークI/OもJS標準ライブラリの非同期APIに委ねるため
// 専用スレッドプールを持つ意味がない）。
internal actual val defaultIoDispatcher: CoroutineDispatcher = Dispatchers.Default
