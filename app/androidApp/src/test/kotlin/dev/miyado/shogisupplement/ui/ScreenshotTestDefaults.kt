package dev.miyado.shogisupplement.ui

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * changeThresholdは0（完全一致）。緩めると小さなUI差分がverifyをすり抜けて陳腐化する。
 * maxDistanceを既定の0.007から広げるのは、記録した環境と検証する環境でアンチエイリアスの
 * 丸めが色距離0.0096ずれ、既定値を超えて全画面が差分と数えられるため。
 */
@OptIn(ExperimentalRoborazziApi::class)
val screenshotRoborazziOptions = RoborazziOptions(
    recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
    compareOptions = RoborazziOptions.CompareOptions(
        changeThreshold = 0f,
        imageComparator = SimpleImageComparator(maxDistance = 0.015f),
    ),
)
