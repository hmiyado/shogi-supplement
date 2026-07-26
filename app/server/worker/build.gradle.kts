plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("dev.miyado.shogisupplement.server.worker.ApplicationKt")
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Why not supabase-kt: :sharedのcommonMain依存であるsupabase-ktはAuth/anon-key前提の
    // クライアント認証フローが中心で、service_roleでのRLSバイパスアクセス向けの薄いAPIでは
    // ないため、ワーカー側はPostgRESTを直接叩く。
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.nimbus.jose.jwt)

    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}
