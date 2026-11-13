plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// Catalog creation (card data from external sources -> catalog.json) and card image processing.
// No UI dependencies: the app and the seed generator both use this module.
kotlin {
    jvm()
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
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
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

// ./gradlew :catalog:convertSet -Pset=OP01
// Downloads and converts one set's art into the app data folder (resumable; skips finished variants).
tasks.register<JavaExec>("convertSet") {
    group = "catalog"
    description = "Converts one set's card art into sim-ready files. Usage: -Pset=OP01"
    val main = kotlin.jvm().compilations.getByName("main")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("commanderpepper.optcgsimcardartchooser.catalog.conversion.ConvertSetKt")
    val setId = providers.gradleProperty("set")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(setId.orNull ?: throw GradleException("Pass the set, e.g. -Pset=OP01"))
    })
}
