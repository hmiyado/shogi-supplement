@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.miyado.shogisupplement.ui

import dev.miyado.shogisupplement.text.AppStrings
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/** iOSの解析完了・失敗通知を一箇所に集約する。通知権限が無くても解析結果には影響しない。 */
internal object IosAnalysisNotificationCenter {
    private const val COMPLETION_IDENTIFIER = "analysis-completed"
    private const val ERROR_IDENTIFIER = "analysis-error"
    private var authorizationRequested = false

    fun requestAuthorization() {
        if (authorizationRequested) return
        authorizationRequested = true
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }

    fun isAppActive(): Boolean =
        UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive

    fun notifyCompleted(gameId: Long) {
        schedule(
            identifier = COMPLETION_IDENTIFIER,
            title = AppStrings.NOTIF_DONE_TITLE,
            body = AppStrings.NOTIF_DONE_TEXT,
            userInfo = mapOf("gameId" to gameId.toString()),
        )
    }

    fun notifyFailed(message: String) {
        schedule(
            identifier = ERROR_IDENTIFIER,
            title = AppStrings.NOTIF_ERROR_TITLE,
            body = message,
        )
    }

    fun clearDeliveredNotifications() {
        val identifiers = listOf(COMPLETION_IDENTIFIER, ERROR_IDENTIFIER)
        UNUserNotificationCenter.currentNotificationCenter().apply {
            removePendingNotificationRequestsWithIdentifiers(identifiers)
            removeDeliveredNotificationsWithIdentifiers(identifiers)
        }
    }

    private fun schedule(
        identifier: String,
        title: String,
        body: String,
        userInfo: Map<Any?, *>? = null,
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
            if (userInfo != null) setUserInfo(userInfo)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }
}
