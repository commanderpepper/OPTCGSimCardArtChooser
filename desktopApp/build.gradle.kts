import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.metro)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    testImplementation(libs.kotlin.test)
}

compose.desktop {
    application {
        mainClass = "commanderpepper.optcgsimcardartchooser.MainKt"

        nativeDistributions {
            // jpackage can only build installers for the OS it runs on:
            // run packageDistributionForCurrentOS on Windows for the .msi and on a Mac for the .dmg.
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "OPTCGSimCardArtChooser"
            packageVersion = "1.0.0"
            description = "Choose which card art OPTCGSim displays"
            vendor = "commanderpepper"

            // java.desktop (Swing/AWT pickers) is included by default; Preferences needs java.prefs.
            modules("java.prefs")

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