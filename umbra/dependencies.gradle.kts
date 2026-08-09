import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.DependencyHandlerScope

val libs = the<LibrariesForLibs>()

val api = configurations["api"]
val compileOnly = configurations["compileOnly"]
val annotationProcessor = configurations["annotationProcessor"]
val shadowImplementation = configurations["shadowImplementation"]
val runtimeOnlyNonPublishable = configurations["runtimeOnlyNonPublishable"]

// Embed in JAR without relocation (compile only, shadowed into JAR)
val embedOnly by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
configurations["compileClasspath"].extendsFrom(embedOnly)
configurations["runtimeClasspath"].extendsFrom(embedOnly)
configurations["testCompileClasspath"].extendsFrom(embedOnly)
configurations["testRuntimeClasspath"].extendsFrom(embedOnly)

configurations.configureEach {
    exclude(group = "com.github.GTNewHorizons", module = "Angelica")
}

DependencyHandlerScope.of(dependencies).apply {
    compileOnly(libs.retrofuturabootstrap) { isTransitive = false }

    compileOnly(libs.annotations)
    compileOnly(libs.lombok) { isTransitive = false }
    annotationProcessor(libs.lombok)

    api(libs.gtnhlib) { artifact { classifier = "dev" } }

    shadowImplementation(project(":glsm")) { isTransitive = false }
    shadowImplementation(project(":lwjgl3-backend")) { isTransitive = false }
    shadowImplementation(libs.eventbus)

    shadowImplementation(libs.jcpp) // Apache 2.0
    shadowImplementation(libs.glsl.transformation.lib) {
        exclude(module = "antlr4") // we only want to shadow the runtime module
    }
    shadowImplementation(libs.antlr4.runtime)

    embedOnly(libs.celeritas.common) { isTransitive = false }
    embedOnly(libs.celeritas.lwjgl2.service) { isTransitive = false }
    runtimeOnlyNonPublishable(libs.celeritas.common) { isTransitive = false }
    runtimeOnlyNonPublishable(libs.celeritas.lwjgl2.service) { isTransitive = false }
}
