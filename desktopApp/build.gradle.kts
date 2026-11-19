import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    // Kotlin/JVM (JDK 21) + kotlin-test: see build-logic.
    id("optcg.kotlin-jvm-app")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.metro)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

// Runs from Gradle (run, hotRun) are the debug version: separate data, settings and window title.
// Installers start the app without this, so anything installed is the release version.
tasks.withType<JavaExec>().configureEach {
    systemProperty("optcg.flavor", "debug")
}

compose.desktop {
    application {
        mainClass = "commanderpepper.optcgsimcardartchooser.MainKt"

        nativeDistributions {
            // jpackage can only build installers for the OS it runs on:
            // run packageDistributionForCurrentOS on Windows for the .msi and on a Mac for the .dmg.
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "OPTCGSimCardArtChooser"
            // The release workflow passes the tag's version (v1.2.3 → -PappVersion=1.2.3). MSI limits:
            // MAJOR.MINOR.BUILD with MAJOR and MINOR ≤ 255, BUILD ≤ 65535; the DMG needs MAJOR ≥ 1.
            packageVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")
            description = "Choose which card art OPTCGSim displays"
            vendor = "commanderpepper"

            // Extra JDK modules for the bundled runtime, from `./gradlew :desktopApp:suggestRuntimeModules`
            // (java.desktop is always included). java.net.http: Ktor's Java engine; java.prefs: the saved
            // sim folder and theme. Re-run that task after adding libraries: `run` uses the full JDK, so a
            // missing module only shows up in the packaged app.
            modules("java.instrument", "java.management", "java.net.http", "java.prefs", "jdk.unsupported")

            // App icons (installer, shortcuts, app bundle); made from one picture, see icons/README.md.
            linux { iconFile.set(project.file("icons/app.png")) }
            windows {
                iconFile.set(project.file("icons/app.ico"))
                menuGroup = "OPTCGSim Card Art Chooser"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                // Must never change: lets newer MSIs upgrade an existing install.
                upgradeUuid = "162323e9-f469-4308-a331-cb59ad8d2e14"
            }
            macOS {
                iconFile.set(project.file("icons/app.icns"))
                bundleID = "commanderpepper.optcgsimcardartchooser"
            }
        }
    }
}