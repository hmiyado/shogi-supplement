package dev.miyado.shogisupplement.policy

/** AndroidのversionCodeまたはiOSのCFBundleVersionを返すexpect/actual。 */
expect fun currentBuildNumber(): Int
