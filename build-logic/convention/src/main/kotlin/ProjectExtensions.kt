import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** JDK every module compiles and runs with; Gradle downloads it (foojay) if it's missing. */
internal const val JDK_VERSION = 21

/** A `[libraries]` entry of `gradle/libs.versions.toml`, by its key (e.g. "kotlin-test"). */
internal fun Project.library(key: String): Provider<MinimalExternalModuleDependency> =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(key)
        .orElseThrow { IllegalStateException("No library '$key' in gradle/libs.versions.toml") }
