plugins {
    // Multiplatform (JVM target, JDK 21), serialization, kotlin-test + coroutines-test: see build-logic.
    id("optcg.kmp-library")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.metro)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: shared's public types (e.g. CatalogRepository) expose catalog models.
            api(project(":catalog"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            api(libs.metrox.viewmodel.compose)
            implementation(libs.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
        }
        jvmTest.dependencies {
            // Skia's native library, needed to decode and create images in tests.
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.mock)
        }
    }
}