import Foundation
import UserNotifications

/// Kotlin側の解析通知をタップしたとき、Composeの現在のコントローラへ伝える。
final class AnalysisNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = AnalysisNotificationDelegate()

    private override init() {}

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let gameId = response.notification.request.content.userInfo["gameId"] as? String {
            NotificationCenter.default.post(
                name: .analysisNotificationTapped,
                object: nil,
                userInfo: ["gameId": gameId],
            )
        }
        completionHandler()
    }
}

extension Notification.Name {
    static let analysisNotificationTapped = Notification.Name("shogi-supplement.analysis-notification-tapped")
}
