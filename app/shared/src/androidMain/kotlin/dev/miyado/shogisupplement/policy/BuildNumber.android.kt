package dev.miyado.shogisupplement.policy

import dev.miyado.shogisupplement.shared.BuildConfig

/**
 * :sharedモジュール自身のBuildConfig（androidApp/build.gradle.ktsのversionCodeと
 * 同じgradle.propertiesの値。shared/build.gradle.kts参照）を返す。
 *
 * Why not androidApp側BuildConfig.VERSION_CODEを直接参照: KMPのexpect/actualは
 * 宣言元と同一モジュール内の各プラットフォームソースセットでしか解決できないため、
 * :shared（このファイルの所属）から:androidApp（別モジュール）のBuildConfigは参照できない。
 * Contextからの PackageManager 取得（androidApp/db/AppDatabase.kt 等が Context を
 * 明示的に受け取る既存パターン）も選択肢だったが、この関数は他のexpect/actual
 * （authIoDispatcher等）と同じ引数無しの形にしたかったため、gradle.propertiesを
 * 単一の値源にする方式を選んだ（食い違い防止はビルド設定側で担保）。
 */
actual fun currentBuildNumber(): Int = BuildConfig.APP_VERSION_CODE
