package dev.miyado.shogisupplement.ui

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * 全スクリーンショットテスト共通のRoborazzi設定。changeThresholdは0（完全一致）にする。
 * 閾値を緩めると小さなUI差分がverifyRoborazziDebugをすり抜けて陳腐化する。
 * 同一環境での再実行はビット完全一致するため、厳格にしてもフレーキーにはならない。
 */
@OptIn(ExperimentalRoborazziApi::class)
val screenshotRoborazziOptions = RoborazziOptions(
    recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
)
