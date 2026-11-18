import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import javax.inject.Inject

abstract class UnpackLwjgl3Natives : Sync() {
    @get:Inject
    abstract val archives: ArchiveOperations
}

// LWJGL extracts jar-packed natives to a shared temp dir with no cross-process safety; plain files it loads in place.
fun Project.registerLwjgl3NativesUnpack(): TaskProvider<UnpackLwjgl3Natives> {
    val jars = the<SourceSetContainer>()["test"].runtimeClasspath.filter {
        it.name.startsWith("lwjgl") && it.name.contains("-natives-") && !it.name.startsWith("lwjgl-platform-")
    }
    return tasks.register<UnpackLwjgl3Natives>("unpackLwjgl3Natives") {
        val archives = archives
        from(jars.elements.map { files -> files.map { archives.zipTree(it) } })
        exclude("META-INF/**")
        into(layout.buildDirectory.dir("lwjgl3-natives"))
    }
}

fun Test.loadLwjgl3NativesFrom(unpack: TaskProvider<UnpackLwjgl3Natives>) {
    classpath = project.files(unpack) + classpath
}
