import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

fun Test.javaAgent(jar: FileCollection) {
    inputs.files(jar).withPropertyName("javaAgent")
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${jar.singleFile.path}") })
}
