plugins {
    // Multiplatform (JVM target, JDK 21), serialization, kotlin-test + coroutines-test: see build-logic.
    id("optcg.kmp-library")
}

// Catalog creation (card data from external sources -> catalog.json) and card image processing.
// No UI dependencies: the app and the seed generator both use this module.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.java)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

// ./gradlew :catalog:generateSeedCatalog
// Pulls card data from optcgapi.com (4 requests) and rewrites the catalog bundled with the app.
tasks.register<JavaExec>("generateSeedCatalog") {
    group = "catalog"
    description = "Regenerates the bundled seed catalog from optcgapi.com."
    val main = kotlin.jvm().compilations.getByName("main")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("commanderpepper.optcgsimcardartchooser.catalog.SeedCatalogKt")
    args(layout.projectDirectory.file("src/jvmMain/resources/seed-catalog.json").asFile.absolutePath)
}
