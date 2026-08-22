package dev.miyado.shogisupplement.policy

/** app_policyのplatformキーを決める。Debugはdev行を読み、判定不能時は本番行を選ぶ。 */
fun resolvePolicyPlatform(basePlatform: String, isDebugBuild: Boolean): String =
    if (isDebugBuild) "$basePlatform-dev" else basePlatform
