plugins {
    `kotlin-dsl`
}

// compileOnly: the root build already loads these plugins (`apply false`), so the convention
// plugins use that copy instead of putting a second one on the classpath.
dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.serializationGradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "optcg.kmp-library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kotlinJvmApp") {
            id = "optcg.kotlin-jvm-app"
            implementationClass = "KotlinJvmAppConventionPlugin"
        }
    }
}
