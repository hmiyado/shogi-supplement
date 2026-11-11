package dev.miyado.shogisupplement.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.miyado.shogisupplement.ShogiApp
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34], application = Application::class)
class AnalysisServiceTest {
    @Test
    fun progressNotificationIsPostedWhenPermissionIsGranted() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val notification = updateProgressNotification()

        assertNotNull(notification)
        assertEquals(50, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(100, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
    }

    @Test
    fun progressNotificationIsSkippedWhenPermissionIsDenied() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertNull(updateProgressNotification())
    }

    @Test
    @Config(sdk = [32])
    fun progressNotificationDoesNotRequireRuntimePermissionBeforeApi33() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertNotNull(updateProgressNotification())
    }

    private fun updateProgressNotification(): Notification? {
        val controller = Robolectric.buildService(AnalysisService::class.java)
        val service = controller.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ShogiApp.CHANNEL_ANALYSIS, "Analysis", NotificationManager.IMPORTANCE_LOW),
        )
        // 通知更新のみを検証し、解析開始やアプリの外部サービス初期化は行わない。
        ReflectionHelpers.setField(service, "notificationManager", manager)
        ReflectionHelpers.setField(service, "notificationTitle", "Analyzing")
        ReflectionHelpers.setField(service, "notificationPreparing", "Preparing")
        try {
            ReflectionHelpers.callInstanceMethod<Void>(
                service,
                "updateProgressNotification",
                ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, 5),
                ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, 10),
            )
            return shadowOf(manager).getNotification(1001)
        } finally {
            controller.destroy()
        }
    }
}
