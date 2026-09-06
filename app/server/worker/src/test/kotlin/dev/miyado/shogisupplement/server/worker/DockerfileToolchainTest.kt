package dev.miyado.shogisupplement.server.worker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Dockerfileのビルドステージに書かれたJDKと、libs.versions.tomlのjvm-toolchainの一致を検査する。
 * ずれるとGradleがtoolchainを見つけられずDockerビルドが落ちるが、イメージのビルドはmainへの
 * pushでしか走らないため、PRのCIでは気付けない（Dockerfileの版指定は構成から共有できず複製になる）。
 */
class DockerfileToolchainTest {

    @Test
    fun `ビルドステージのJDKはjvm-toolchainと一致する`() {
        val dockerfile = File("Dockerfile")
        val versions = File("../../gradle/libs.versions.toml")

        val buildStageJdk = Regex("""FROM eclipse-temurin:(\d+)-jdk AS build""")
            .find(dockerfile.readText())
            ?.groupValues?.get(1)
        assertNotNull(buildStageJdk, "Dockerfileのビルドステージが読み取れない: ${dockerfile.absolutePath}")

        val toolchain = Regex("""^jvm-toolchain\s*=\s*"(\d+)"""", RegexOption.MULTILINE)
            .find(versions.readText())
            ?.groupValues?.get(1)
        assertNotNull(toolchain, "jvm-toolchainが読み取れない: ${versions.absolutePath}")

        assertEquals(toolchain, buildStageJdk, "ビルドステージのJDKをjvm-toolchainへ合わせること")
    }
}
