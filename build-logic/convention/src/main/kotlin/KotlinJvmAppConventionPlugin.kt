import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** `optcg.kotlin-jvm-app`: a plain Kotlin/JVM module (the desktop app). */
class KotlinJvmAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(JDK_VERSION)
        }
        dependencies {
            "testImplementation"(library("kotlin-test"))
        }
    }
}
