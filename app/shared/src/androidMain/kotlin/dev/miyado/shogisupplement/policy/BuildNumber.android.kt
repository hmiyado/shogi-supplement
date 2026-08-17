package dev.miyado.shogisupplement.policy

/**
 * Why not アプリのBuildConfigを直接参照: 共有モジュールはアプリモジュールの生成クラスに
 * 依存できず、Android-KMPプラグイン自身もBuildConfigを生成しないため、
 * gradle.propertiesの単一の値からKotlin定数を生成する。
 */
actual fun currentBuildNumber(): Int = GENERATED_APP_VERSION_CODE
