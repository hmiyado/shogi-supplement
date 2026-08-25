package dev.miyado.shogisupplement.policy

/**
 * JVM実装はテスト・開発ツール用途限定（実APKを持たないため実際のビルド番号が存在しない。
 * crypto/TransferSecretStore.jvm.kt と同じ位置づけ）。強制アップデートの対象に
 * ならないよう、常に判定を満たさない Int.MAX_VALUE を返す。
 */
actual fun currentBuildNumber(): Int = Int.MAX_VALUE
