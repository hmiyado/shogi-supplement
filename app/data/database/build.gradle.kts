plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.sqldelightPlugin)
}

sqldelight {
    databases {
        create("ShogiSupplementDatabase") {
            packageName.set("dev.miyado.shogisupplement.db")
            // 新規作成スキーマ（CREATE TABLE群）と .sqm を積み上げてマイグレーションした
            // スキーマが一致することをビルド時に機械検証する（verifySqlDelightMigration*タスク）。
            // verifyMigrationsは比較対象のスキーマスナップショット（.db）の出力先が必要なため
            // schemaOutputDirectoryも合わせて指定する。
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "dev.miyado.shogisupplement.data.database"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Repositoryのportと保存レコード型。
            api(project(":application"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // 取込・解析・同意のuse caseを実DBで通すテストをここに置く（fakeではなく実装で確かめる）。
            implementation(project(":engine:remote"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

// jvmTestの解析テストが係数表を読む。リソースを複製せず、androidApp/src/main/assetsの
// 正本からコピーする（:analysis・:shared にある同名タスクと同じ理由）。
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("jvmTestProcessResources") {
    from(rootProject.file("androidApp/src/main/assets/coefficients_hao_isolate_v1.json"))
}

// DrillRepositoryTestの日付境界テストがSQLiteの date(...,'localtime',...) を使う。
// 'localtime'はJVMのTimeZone.setDefault()ではなくプロセスのOSタイムゾーンに従うため、
// テストプロセス自体のTZをAsia/Tokyoへ固定する（CI実行環境のタイムゾーンに依存させない）。
tasks.named<Test>("jvmTest") {
    environment("TZ", "Asia/Tokyo")
}
