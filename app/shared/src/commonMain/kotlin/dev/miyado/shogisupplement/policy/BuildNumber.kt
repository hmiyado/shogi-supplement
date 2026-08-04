package dev.miyado.shogisupplement.policy

/**
 * 自分の側のビルド番号（Android=versionCode、iOS=CFBundleVersion）。
 * [dev.miyado.shogisupplement.policy.ForceUpdatePolicyChecker] の判定に使う
 * ([dev.miyado.shogisupplement.auth.authIoDispatcher] と同じ expect/actual の置き場所。
 * commonMain=宣言のみ、androidMain/iosMain/jvmMain=実体)。
 */
expect fun currentBuildNumber(): Int
